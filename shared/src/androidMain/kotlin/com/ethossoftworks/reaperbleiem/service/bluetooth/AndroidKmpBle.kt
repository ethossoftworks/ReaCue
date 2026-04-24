package com.ethossoftworks.reaperbleiem.service.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.util.forEach
import com.outsidesource.oskitkmp.concurrency.flowIn
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.asFlow
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

@Suppress("FunctionNaming") fun KmpBle(context: Context): IKmpBle = AndroidKmpBle(context)

internal class AndroidKmpBle(private val context: Context) : IKmpBle {
    override fun scan(): Flow<KmpBleScanRecord> = callbackFlow {
        val scanner = BluetoothLeScannerCompat.getScanner()
        val settings =
            ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(true)
                .build()

        val callback =
            object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record =
                        KmpBleScanRecord(
                            name = result.device.name ?: "",
                            identifier = result.device.address,
                            rssi = result.rssi,
                            serviceUuids =
                                result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList(),
                            serviceData =
                                buildMap {
                                    val data = result.scanRecord?.serviceData ?: return@buildMap
                                    data.forEach { uuid, bytes -> put(uuid.uuid.toString(), bytes) }
                                },
                            manufacturerData =
                                buildMap {
                                    val data = result.scanRecord?.manufacturerSpecificData ?: return@buildMap
                                    data.forEach { id, bytes -> put(id, bytes) }
                                },
                        )
                    launch { send(record) }
                }
            }

        scanner.startScan(emptyList(), settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    override suspend fun connect(
        identifier: KmpBleIdentifier,
        scope: CoroutineScope,
    ): Outcome<IKmpBlePeripheral, KmpBleError> {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val nordicManager = NordicManagerProxy(context)
            val peripheral = AndroidKmpBlePeripheral(nordicManager, scope)
            val device =
                bluetoothManager.adapter.getRemoteDevice(identifier)
                    ?: return Outcome.Error(KmpBleError.InvalidIdentifier)

            nordicManager.connect(device).retry(5, 200).timeout(30_000).suspend()
            Outcome.Ok(peripheral)
        } catch (t: Throwable) {
            Outcome.Error(KmpBleError.Unknown(t))
        }
    }
}

private class NordicManagerProxy(private val context: Context) : BleManager(context) {

    internal val connectionStatus = MutableStateFlow<KmpBleConnectionStatus>(KmpBleConnectionStatus.Connecting)
    private val services: AtomicRef<List<BluetoothGattService>> = atomic(emptyList())

    fun getCurrentMtu() = mtu

    suspend fun requestMtuExternal(value: Int): Int = requestMtu(value).suspend()

    fun getDiscoveredServicesExternal(): List<BluetoothGattService> = services.value

    suspend fun readExternal(char: BluetoothGattCharacteristic): ByteArray {
        return readCharacteristic(char).suspend().value ?: byteArrayOf()
    }

    suspend fun writeExternal(char: BluetoothGattCharacteristic, data: ByteArray, mode: Int) {
        writeCharacteristic(char, data, mode).suspend()
    }

    suspend fun enableNotificationsExternal(char: BluetoothGattCharacteristic) {
        enableNotifications(char).suspend()
    }

    suspend fun enableIndicationsExternal(char: BluetoothGattCharacteristic) {
        enableIndications(char).suspend()
    }

    suspend fun disableNotificationsExternal(char: BluetoothGattCharacteristic) {
        disableNotifications(char).suspend()
    }

    suspend fun disableIndicationsExternal(char: BluetoothGattCharacteristic) {
        disableIndications(char).suspend()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setNotificationCallbackExternal(char: BluetoothGattCharacteristic): Flow<ByteArray> {
        return setNotificationCallback(char).asFlow().map { it.value ?: byteArrayOf() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setIndicationCallbackExternal(char: BluetoothGattCharacteristic): Flow<ByteArray> {
        return setIndicationCallback(char).asFlow().map { it.value ?: byteArrayOf() }
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        services.update { gatt.services }
        return true
    }

    override fun initialize() {
        connectionObserver =
            object : ConnectionObserver {
                override fun onDeviceFailedToConnect(p0: BluetoothDevice, p1: Int) {}

                override fun onDeviceReady(p0: BluetoothDevice) {}

                override fun onDeviceConnecting(p0: BluetoothDevice) {
                    connectionStatus.value = KmpBleConnectionStatus.Connecting
                }

                override fun onDeviceConnected(p0: BluetoothDevice) {
                    connectionStatus.value = KmpBleConnectionStatus.Connected
                }

                override fun onDeviceDisconnecting(p0: BluetoothDevice) {
                    connectionStatus.value = KmpBleConnectionStatus.Disconnecting
                }

                override fun onDeviceDisconnected(p0: BluetoothDevice, p1: Int) {
                    connectionStatus.value = KmpBleConnectionStatus.Disconnected
                }
            }
    }

    override fun onServicesInvalidated() {
        services.update { emptyList() }
    }
}

private class AndroidKmpBlePeripheral(
    private val nordicManager: NordicManagerProxy,
    override val scope: CoroutineScope,
) : IKmpBlePeripheral {

    private val localServices: AtomicRef<Map<String, AndroidKmpBleService>> = atomic(emptyMap())
    private val localCharacteristics: AtomicRef<Map<String, AndroidKmpBleCharacteristic>> = atomic(emptyMap())

    override val services: Map<String, IKmpBleService> = localServices.value
    override val characteristics: Map<String, IKmpBleCharacteristic> = localCharacteristics.value
    override val connectionStatus: StateFlow<KmpBleConnectionStatus> = nordicManager.connectionStatus.asStateFlow()

    init {
        scope.launch {
            nordicManager.connectionStatus
                .filter { it == KmpBleConnectionStatus.Disconnected }
                .collect { scope.coroutineContext.cancelChildren(KmpBlePeripheralDisconnect()) }
        }
    }

    override fun mtu(mode: KmpBleWriteMode): Int = nordicManager.getCurrentMtu()

    override suspend fun requestMtu(size: Int): Outcome<Int, KmpBleError> =
        withScope(scope) { Outcome.Ok(nordicManager.requestMtuExternal(size)) }

    override suspend fun disconnect(): Outcome<Unit, KmpBleError> =
        withScope(scope) { Outcome.Ok(nordicManager.disconnect().suspend()) }

    override suspend fun discoverServices(): Outcome<Map<String, IKmpBleService>, KmpBleError> =
        withScope(scope) {
            val serviceMap = buildMap {
                nordicManager.getDiscoveredServicesExternal().forEach {
                    put(it.uuid.toString(), AndroidKmpBleService(it, nordicManager, scope))
                }
            }
            val characteristicMap = buildMap {
                serviceMap.forEach { _, service ->
                    service.characteristics.forEach {
                        val characteristic = it.value as? AndroidKmpBleCharacteristic ?: return@forEach
                        put(it.key, characteristic)
                    }
                }
            }
            localServices.update { serviceMap }
            localCharacteristics.update { characteristicMap }

            Outcome.Ok(services)
        }

    override suspend fun awaitBond(encryptedReadCharacteristicUuid: String): Outcome<Unit, KmpBleError> =
        awaitBond(encryptedReadCharacteristicUuid, scope)

    @SuppressLint("MissingPermission")
    override suspend fun read(characteristicUuid: String): Outcome<ByteArray, KmpBleError> {
        val characteristic =
            localCharacteristics.value[characteristicUuid] ?: return Outcome.Error(KmpBleError.UnknownCharacteristic)
        return characteristic.read()
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(
        characteristicUuid: String,
        data: ByteArray,
        mode: KmpBleWriteMode,
    ): Outcome<Unit, KmpBleError> {
        val characteristic =
            localCharacteristics.value[characteristicUuid] ?: return Outcome.Error(KmpBleError.UnknownCharacteristic)
        return characteristic.write(data, mode)
    }

    override suspend fun notifications(
        characteristicUuid: String,
        bufferSize: Int,
        bufferOverflow: BufferOverflow,
    ): Flow<ByteArray> {
        val characteristic = localCharacteristics.value[characteristicUuid] ?: return emptyFlow()
        return characteristic.notifications(bufferSize, bufferOverflow)
    }
}

private class AndroidKmpBleService(
    private val service: BluetoothGattService,
    val manager: NordicManagerProxy,
    scope: CoroutineScope,
) : IKmpBleService {
    override val uuid: String = service.uuid.toString()
    override val characteristics: Map<String, IKmpBleCharacteristic> = buildMap {
        service.characteristics.forEach { put(it.uuid.toString(), AndroidKmpBleCharacteristic(it, manager, scope)) }
    }
}

private class AndroidKmpBleCharacteristic(
    private val characteristic: BluetoothGattCharacteristic,
    private val manager: NordicManagerProxy,
    private val scope: CoroutineScope,
) : IKmpBleCharacteristic {
    override val uuid: String = characteristic.uuid.toString()
    override val properties: List<KmpBleGattProperty> = buildList {
        val props = characteristic.properties
        if ((props and BluetoothGattCharacteristic.PROPERTY_BROADCAST) > 0) add(KmpBleGattProperty.Broadcast)
        if ((props and BluetoothGattCharacteristic.PROPERTY_READ) > 0) add(KmpBleGattProperty.Read)
        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
            add(KmpBleGattProperty.WriteWithoutResponse)
        }
        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) add(KmpBleGattProperty.Write)
        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) add(KmpBleGattProperty.Notify)
        if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) add(KmpBleGattProperty.Indicate)
        if ((props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) > 0) add(KmpBleGattProperty.SignedWrite)
        if ((props and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) > 0) add(KmpBleGattProperty.ExtendedProps)
    }

    override suspend fun read(): Outcome<ByteArray, KmpBleError> =
        withScope(scope) { Outcome.Ok(manager.readExternal(characteristic)) }

    override suspend fun write(data: ByteArray, mode: KmpBleWriteMode): Outcome<Unit, KmpBleError> =
        withScope(scope) {
            val platformMode =
                when (mode) {
                    KmpBleWriteMode.WithResponse -> WRITE_TYPE_DEFAULT
                    KmpBleWriteMode.WithoutResponse -> WRITE_TYPE_NO_RESPONSE
                }
            Outcome.Ok(manager.writeExternal(characteristic, data, platformMode))
        }

    override suspend fun notifications(bufferSize: Int, bufferOverflow: BufferOverflow): Flow<ByteArray> {
        val flowFactory: (BluetoothGattCharacteristic) -> Flow<ByteArray>
        val enabler: suspend (BluetoothGattCharacteristic) -> Unit
        val disabler: suspend (BluetoothGattCharacteristic) -> Unit

        when {
            properties.contains(KmpBleGattProperty.Notify) -> {
                flowFactory = manager::setNotificationCallbackExternal
                enabler = manager::enableNotificationsExternal
                disabler = manager::disableNotificationsExternal
            }
            properties.contains(KmpBleGattProperty.Indicate) -> {
                flowFactory = manager::setIndicationCallbackExternal
                enabler = manager::enableIndicationsExternal
                disabler = manager::disableIndicationsExternal
            }
            else -> return emptyFlow()
        }

        return flowFactory(characteristic)
            .onStart { ignoreExceptions { enabler(characteristic) } }
            .onCompletion { scope.launch { ignoreExceptions { disabler(characteristic) } } }
            .catch { /* Do nothing but complete */ }
            .flowIn(scope)
    }
}

private suspend inline fun ignoreExceptions(block: suspend () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        // do nothing
    }
}
