@file:OptIn(ExperimentalUuidApi::class)

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
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCharacteristic
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheral
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleService
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionPriority
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionStatus
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleError
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralDisconnect
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralGattResult
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralId
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleScanRecord
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleWriteMode
import com.ethossoftworks.reaperbleiem.lib.bluetooth.awaitBond
import com.ethossoftworks.reaperbleiem.lib.bluetooth.withScope
import com.outsidesource.oskitkmp.concurrency.flowIn
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.asFlow
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

@Suppress("FunctionNaming")
fun KmpBleCentralManager(context: Context): IKmpBleCentralManager = AndroidKmpBleCentralManager(context)

internal class AndroidKmpBleCentralManager(private val context: Context) : IKmpBleCentralManager {
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
                            services =
                                result.scanRecord?.serviceUuids?.map {
                                    Uuid.fromLongs(it.uuid.mostSignificantBits, it.uuid.leastSignificantBits)
                                } ?: emptyList(),
                            serviceData =
                                buildMap {
                                    val data = result.scanRecord?.serviceData ?: return@buildMap
                                    data.forEach { uuid, bytes ->
                                        put(
                                            Uuid.fromLongs(
                                                uuid.uuid.mostSignificantBits,
                                                uuid.uuid.leastSignificantBits,
                                            ),
                                            bytes,
                                        )
                                    }
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
        identifier: KmpBlePeripheralId,
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

    suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority) {
        val connectionPriority =
            when (priority) {
                KmpBleConnectionPriority.High -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
                KmpBleConnectionPriority.Balanced -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
                KmpBleConnectionPriority.Low -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            }
        requestConnectionPriority(connectionPriority).suspend()
    }

    fun getDiscoveredServicesExternal(): List<BluetoothGattService> = services.value

    suspend fun readExternal(char: BluetoothGattCharacteristic): ByteArray {
        return readCharacteristic(char).suspend().value ?: byteArrayOf()
    }

    suspend fun writeExternal(
        char: BluetoothGattCharacteristic,
        data: ByteArray,
        mode: Int,
    ): Outcome<Unit, KmpBleError> {
        return try {
            writeCharacteristic(char, data, mode).suspend()
            Outcome.Ok(Unit)
        } catch (e: RequestFailedException) {
            val error =
                when (e.status) {
                    BluetoothGatt.GATT_FAILURE -> KmpBlePeripheralGattResult.Failure
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ->
                        KmpBlePeripheralGattResult.InsufficientAuthentication
                    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> KmpBlePeripheralGattResult.InsufficientEncryption
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> KmpBlePeripheralGattResult.InvalidAttributeLength
                    BluetoothGatt.GATT_INVALID_OFFSET -> KmpBlePeripheralGattResult.InvalidOffset
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> KmpBlePeripheralGattResult.ReadNotPermitted
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> KmpBlePeripheralGattResult.RequestNotSupported
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> KmpBlePeripheralGattResult.WriteNotPermitted
                    else if (e.status in 0x80..0x9F) -> KmpBlePeripheralGattResult.UserDefined(e.status.toByte())
                    else -> KmpBlePeripheralGattResult.Failure
                }
            Outcome.Error(KmpBleError.WriteError(error))
        }
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

    private val localServices: AtomicRef<Map<Uuid, AndroidKmpBleService>> = atomic(emptyMap())
    private val localCharacteristics: AtomicRef<Map<Uuid, AndroidKmpBleCharacteristic>> = atomic(emptyMap())

    override val services: Map<Uuid, IKmpBleService>
        get() = localServices.value

    override val characteristics: Map<Uuid, IKmpBleCharacteristic>
        get() = localCharacteristics.value

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

    override suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority) {
        withScope(scope) { Outcome.Ok(nordicManager.requestConnectionPriority(priority)) }
    }

    override suspend fun disconnect(): Outcome<Unit, KmpBleError> =
        withScope(scope) { Outcome.Ok(nordicManager.disconnect().suspend()) }

    override suspend fun discoverServices(): Outcome<Map<Uuid, IKmpBleService>, KmpBleError> =
        withScope(scope) {
            val serviceMap = buildMap {
                nordicManager.getDiscoveredServicesExternal().forEach {
                    val uuid = Uuid.fromLongs(it.uuid.mostSignificantBits, it.uuid.leastSignificantBits)
                    put(uuid, AndroidKmpBleService(it, nordicManager, scope))
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

    override suspend fun awaitBond(encryptedReadCharacteristic: Uuid): Outcome<Unit, KmpBleError> =
        awaitBond(encryptedReadCharacteristic, scope)

    @SuppressLint("MissingPermission")
    override suspend fun read(characteristic: Uuid): Outcome<ByteArray, KmpBleError> {
        val characteristic =
            localCharacteristics.value[characteristic] ?: return Outcome.Error(KmpBleError.UnknownCharacteristic)
        return characteristic.read()
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(
        characteristic: Uuid,
        data: ByteArray,
        mode: KmpBleWriteMode,
    ): Outcome<Unit, KmpBleError> {
        val characteristic =
            localCharacteristics.value[characteristic] ?: return Outcome.Error(KmpBleError.UnknownCharacteristic)
        return characteristic.write(data, mode)
    }

    override suspend fun notifications(
        characteristic: Uuid,
        bufferSize: Int,
        bufferOverflow: BufferOverflow,
    ): Flow<ByteArray> {
        val characteristic = localCharacteristics.value[characteristic] ?: return emptyFlow()
        return characteristic.notifications(bufferSize, bufferOverflow)
    }
}

private class AndroidKmpBleService(
    private val service: BluetoothGattService,
    val manager: NordicManagerProxy,
    scope: CoroutineScope,
) : IKmpBleService {
    override val uuid: Uuid = Uuid.fromLongs(service.uuid.mostSignificantBits, service.uuid.leastSignificantBits)
    override val characteristics: Map<Uuid, IKmpBleCharacteristic> = buildMap {
        service.characteristics.forEach {
            put(
                Uuid.fromLongs(it.uuid.mostSignificantBits, it.uuid.leastSignificantBits),
                AndroidKmpBleCharacteristic(it, manager, scope),
            )
        }
    }
}

private class AndroidKmpBleCharacteristic(
    private val characteristic: BluetoothGattCharacteristic,
    private val manager: NordicManagerProxy,
    private val scope: CoroutineScope,
) : IKmpBleCharacteristic {
    override val uuid: Uuid =
        Uuid.fromLongs(characteristic.uuid.mostSignificantBits, characteristic.uuid.leastSignificantBits)
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
            manager.writeExternal(characteristic, data, platformMode)
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
