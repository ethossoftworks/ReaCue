@file:OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)

package com.ethossoftworks.reacue.lib.bluetooth

import com.outsidesource.oskitkmp.concurrency.filterIsInstance
import com.outsidesource.oskitkmp.concurrency.flowIn
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBATTErrorDomain
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorUnlikelyError
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyAuthenticatedSignedWrites
import platform.CoreBluetooth.CBCharacteristicPropertyBroadcast
import platform.CoreBluetooth.CBCharacteristicPropertyExtendedProperties
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.CoreFoundation.CFAbsoluteTime
import platform.Foundation.NSArray
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.data
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.posix.memcpy

@Suppress("FunctionNaming")
fun KmpBleCentralManager(cbCentralFactory: (() -> CBCentralManager)? = null): IKmpBleCentralManager =
    AppleKmpBleCentralManager(cbCentralFactory)

val KmpBleCbCentralQueue: dispatch_queue_t = dispatch_queue_create("kmp-ble-central-manager", attr = null)

internal class AppleKmpBleCentralManager(cbCentralManagerFactory: (() -> CBCentralManager)? = null) :
    IKmpBleCentralManager {

    private val centralManagerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val centralEvents =
        MutableSharedFlow<CentralManagerEvent>(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    internal val centralManager: CBCentralManager by lazy {
        val manager = (cbCentralManagerFactory?.invoke() ?: CBCentralManager(null, KmpBleCbCentralQueue))
        manager.apply { delegate = this@AppleKmpBleCentralManager.centralManagerDelegate }
    }
    private val centralManagerDelegate = AppleCBCentralDelegate(centralManagerScope, centralEvents)

    override fun scan(): Flow<KmpBleScanRecord> = callbackFlow {
        awaitCentralManagerPoweredOn() ?: return@callbackFlow
        centralManager.scanForPeripheralsWithServices(null, null)
        centralEvents
            .filterIsInstance<CentralManagerEvent.ScanRecord>()
            .onEach { send(it.record) }
            .launchIn(centralManagerScope)
        awaitClose { centralManager.stopScan() }
    }

    override suspend fun connect(
        identifier: KmpBlePeripheralId,
        scope: CoroutineScope,
    ): Outcome<IKmpBlePeripheral, KmpBleError> {
        awaitCentralManagerPoweredOn() ?: return Outcome.Error(KmpBleError.PlatformHandlerNotReady)

        val uuid = NSUUID(identifier)
        val peripherals = centralManager.retrievePeripheralsWithIdentifiers(listOf(uuid))
        val peripheral =
            peripherals.firstOrNull() as? CBPeripheral ?: return Outcome.Error(KmpBleError.InvalidIdentifier)

        centralManager.connectPeripheral(peripheral, null)

        val result =
            withTimeoutOrNull(30_000.milliseconds) {
                centralEvents
                    .filterIsInstance<CentralManagerEvent.ConnectionResult> { it.identifier == identifier }
                    .first()
            }

        if (result == null) {
            centralManager.cancelPeripheralConnection(peripheral)
            return Outcome.Error(KmpBleError.Unknown(Unit))
        }

        if (result.outcome is Outcome.Error) return result.outcome

        return Outcome.Ok(AppleKmpBlePeripheral(scope, this, peripheral))
    }

    private suspend fun awaitCentralManagerPoweredOn(): Unit? {
        return withTimeoutOrNull(2_000.milliseconds) {
            while (isActive) {
                if (centralManager.state == CBManagerStatePoweredOn) return@withTimeoutOrNull
                delay(16.milliseconds)
            }
        }
    }
}

private class AppleCBCentralDelegate(
    private val scope: CoroutineScope,
    private val events: MutableSharedFlow<CentralManagerEvent>,
) : CBCentralManagerDelegateProtocol, NSObject() {

    @OptIn(ExperimentalStdlibApi::class)
    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        scope.launch {
            val serviceUUIDs = buildList {
                val raw = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? NSArray ?: return@buildList
                raw.forEach {
                    val uuid = it as? CBUUID ?: return@forEach
                    add(formatCbUuid(uuid))
                }
            }

            val serviceData = buildMap {
                val raw = advertisementData[CBAdvertisementDataServiceDataKey] as? NSDictionary ?: return@buildMap
                raw.forEach { k, v ->
                    val uuid = k as? CBUUID ?: return@forEach
                    val data = v as? NSData ?: return@forEach
                    put(formatCbUuid(uuid), data.toByteArray())
                }
            }

            val manufacturerData = buildMap {
                val raw = advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData ?: return@buildMap
                val bytes = raw.toByteArray()
                val companyId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
                put(companyId, bytes.copyOfRange(2, bytes.size))
            }

            val record =
                KmpBleScanRecord(
                    name =
                        (advertisementData[CBAdvertisementDataLocalNameKey] as? NSString)?.toString()
                            ?: didDiscoverPeripheral.name
                            ?: "",
                    rssi = RSSI.intValue,
                    identifier = didDiscoverPeripheral.identifier.UUIDString,
                    manufacturerData = manufacturerData,
                    serviceData = serviceData,
                    services = serviceUUIDs,
                )

            events.emit(CentralManagerEvent.ScanRecord(record))
        }
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {}

    override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
        scope.launch {
            val event =
                CentralManagerEvent.ConnectionResult(
                    identifier = didFailToConnectPeripheral.identifier.UUIDString,
                    outcome =
                        if (error?.code == 14L) {
                            Outcome.Error(KmpBleError.PeerRemovedPairingInfo)
                        } else {
                            Outcome.Error(KmpBleError.Unknown(error?.localizedDescription ?: ""))
                        },
                )
            events.emit(event)
        }
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        scope.launch {
            val event =
                CentralManagerEvent.ConnectionResult(
                    identifier = didConnectPeripheral.identifier.UUIDString,
                    outcome = Outcome.Ok(Unit),
                )
            events.emit(event)
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        timestamp: CFAbsoluteTime,
        isReconnecting: Boolean,
        error: NSError?,
    ) {
        scope.launch { events.emit(CentralManagerEvent.Disconnected(didDisconnectPeripheral)) }
    }
}

internal sealed class CentralManagerEvent {
    data class ConnectionResult(val identifier: KmpBlePeripheralId, val outcome: Outcome<Unit, KmpBleError>) :
        CentralManagerEvent()

    data class Disconnected(val peripheral: CBPeripheral) : CentralManagerEvent()

    data class ScanRecord(val record: KmpBleScanRecord) : CentralManagerEvent()
}

private class AppleKmpBlePeripheral(
    override val scope: CoroutineScope,
    private val appleKmpBle: AppleKmpBleCentralManager,
    private val peripheral: CBPeripheral,
) : IKmpBlePeripheral {

    private val events = MutableSharedFlow<PeripheralEvent>(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    private val canSendWriteWithoutResponseFlow = MutableStateFlow(peripheral.canSendWriteWithoutResponse)
    private val delegate = ApplePeripheralDelegate(scope, canSendWriteWithoutResponseFlow, events)
    private val localServices: AtomicRef<Map<Uuid, AppleKmpBleService>> = atomic(emptyMap())
    private val localCharacteristics: AtomicRef<Map<Uuid, AppleKmpBleCharacteristic>> = atomic(emptyMap())
    private val localConnectionFlow = MutableStateFlow<KmpBleConnectionStatus>(KmpBleConnectionStatus.Connected)

    override val services: Map<Uuid, IKmpBleService> = localServices.value
    override val characteristics: Map<Uuid, IKmpBleCharacteristic> = localCharacteristics.value
    override val connectionStatus: StateFlow<KmpBleConnectionStatus> = localConnectionFlow.asStateFlow()

    init {
        peripheral.delegate = delegate
        scope.launch {
            appleKmpBle.centralEvents.filterIsInstance<CentralManagerEvent.Disconnected>().collect {
                localConnectionFlow.value = KmpBleConnectionStatus.Disconnected
                scope.coroutineContext.cancelChildren(KmpBlePeripheralDisconnect())
            }
        }
    }

    override fun mtu(mode: KmpBleWriteMode): Int = peripheral.maximumWriteValueLengthForType(mode.toCbType()).toInt()

    override suspend fun requestMtu(size: Int): Outcome<Int, KmpBleError> =
        Outcome.Ok(mtu(mode = KmpBleWriteMode.WithResponse))

    override suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority) {
        // Noop
    }

    override suspend fun disconnect(): Outcome<Unit, KmpBleError> =
        withScope(scope) {
            appleKmpBle.centralManager.cancelPeripheralConnection(peripheral)
            appleKmpBle.centralEvents
                .filterIsInstance<CentralManagerEvent.Disconnected> {
                    it.peripheral.identifier == peripheral.identifier
                }
                .first()
            Outcome.Ok(Unit)
        }

    override suspend fun discoverServices(): Outcome<Map<Uuid, IKmpBleService>, KmpBleError> =
        withScope(scope) {
            peripheral.discoverServices(null)
            val event = events.filterIsInstance<PeripheralEvent.DiscoveredOrModifiedServices>().first()
            if (event.error != null) return@withScope Outcome.Error(KmpBleError.Unknown(event.error))

            // Discover characteristics
            coroutineScope {
                    val results =
                        peripheral.services
                            ?.mapNotNull { service ->
                                if (service !is CBService) return@mapNotNull null
                                async {
                                    peripheral.discoverCharacteristics(null, forService = service)
                                    events
                                        .filterIsInstance<PeripheralEvent.DiscoveredCharacteristics> {
                                            it.service == service
                                        }
                                        .first()
                                }
                            }
                            ?.awaitAll()

                    if (results?.any { it.error != null } == true) {
                        return@coroutineScope Outcome.Error(KmpBleError.CouldNotDiscoverCharacteristics)
                    }
                    Outcome.Ok(Unit)
                }
                .unwrapOrReturn {
                    return@withScope it
                }

            val serviceMap = buildMap {
                peripheral.services?.forEach { service ->
                    if (service !is CBService) return@forEach
                    put(formatCbUuid(service.UUID), AppleKmpBleService(service, this@AppleKmpBlePeripheral))
                }
            }

            val characteristicMap = buildMap {
                serviceMap.forEach { (_, service) ->
                    service.characteristics.forEach {
                        val characteristic = it.value as? AppleKmpBleCharacteristic ?: return@forEach
                        put(it.key, characteristic)
                    }
                }
            }

            localServices.update { serviceMap }
            localCharacteristics.update { characteristicMap }

            return@withScope Outcome.Ok(serviceMap)
        }

    override suspend fun awaitBond(encryptedReadCharacteristic: Uuid): Outcome<Unit, KmpBleError> =
        awaitBond(encryptedReadCharacteristic, scope)

    override suspend fun read(characteristic: Uuid): Outcome<ByteArray, KmpBleError> =
        withScope(scope) {
            val characteristic =
                localCharacteristics.value[characteristic]
                    ?: return@withScope Outcome.Error(KmpBleError.UnknownCharacteristic)

            peripheral.readValueForCharacteristic(characteristic.characteristic)
            val event =
                events
                    .filterIsInstance<PeripheralEvent.ValueChanged> {
                        it.characteristic == characteristic.characteristic
                    }
                    .first()
            if (event.error != null) return@withScope Outcome.Error(KmpBleError.Unknown(event.error))

            Outcome.Ok(event.data ?: byteArrayOf())
        }

    override suspend fun write(
        characteristic: Uuid,
        data: ByteArray,
        mode: KmpBleWriteMode,
    ): Outcome<Unit, KmpBleError> =
        withScope(scope) {
            val characteristic =
                localCharacteristics.value[characteristic]
                    ?: return@withScope Outcome.Error(KmpBleError.UnknownCharacteristic)

            val platformMode =
                when (mode) {
                    KmpBleWriteMode.WithoutResponse -> CBCharacteristicWriteWithoutResponse
                    KmpBleWriteMode.WithResponse -> CBCharacteristicWriteWithResponse
                }

            when (mode) {
                KmpBleWriteMode.WithoutResponse -> {
                    if (!canSendWriteWithoutResponseFlow.updateAndGet { peripheral.canSendWriteWithoutResponse }) {
                        canSendWriteWithoutResponseFlow.first { it }
                    }
                    peripheral.writeValue(data.toNSData(), characteristic.characteristic, platformMode)
                    return@withScope Outcome.Ok(Unit)
                }

                KmpBleWriteMode.WithResponse -> {
                    characteristic.lock.withLock {
                        peripheral.writeValue(data.toNSData(), characteristic.characteristic, platformMode)
                        val event =
                            events
                                .filterIsInstance<PeripheralEvent.WriteWithResponse> {
                                    it.characteristic == characteristic.characteristic
                                }
                                .first()

                        if (event.error != null) {
                            if (event.error.domain != CBATTErrorDomain)
                                return@withScope Outcome.Error(KmpBleError.Unknown(event.error))
                            val response =
                                when (event.error.code) {
                                    CBATTErrorUnlikelyError -> KmpBlePeripheralGattResult.Failure
                                    CBATTErrorInsufficientAuthentication ->
                                        KmpBlePeripheralGattResult.InsufficientAuthentication
                                    CBATTErrorInsufficientEncryption ->
                                        KmpBlePeripheralGattResult.InsufficientEncryption
                                    CBATTErrorInvalidAttributeValueLength ->
                                        KmpBlePeripheralGattResult.InvalidAttributeLength
                                    CBATTErrorInvalidOffset -> KmpBlePeripheralGattResult.InvalidOffset
                                    CBATTErrorReadNotPermitted -> KmpBlePeripheralGattResult.ReadNotPermitted
                                    CBATTErrorRequestNotSupported -> KmpBlePeripheralGattResult.RequestNotSupported
                                    CBATTErrorWriteNotPermitted -> KmpBlePeripheralGattResult.WriteNotPermitted
                                    else if (event.error.code in 0x80..0x9F) ->
                                        KmpBlePeripheralGattResult.UserDefined(event.error.code.toByte())
                                    else -> KmpBlePeripheralGattResult.Failure
                                }
                            return@withScope Outcome.Error(KmpBleError.WriteError(response))
                        }
                    }
                    Outcome.Ok(Unit)
                }
            }
        }

    override suspend fun notifications(
        characteristic: Uuid,
        bufferSize: Int,
        bufferOverflow: BufferOverflow,
    ): Flow<ByteArray> =
        callbackFlow<ByteArray> {
                val characteristic =
                    localCharacteristics.value[characteristic]
                        ?: run {
                            close()
                            return@callbackFlow
                        }

                peripheral.setNotifyValue(true, characteristic.characteristic)

                val event =
                    events
                        .filterIsInstance<PeripheralEvent.NotificationStateChanged> {
                            it.characteristic == characteristic.characteristic
                        }
                        .first()

                if (event.error != null) close()

                events
                    .filterIsInstance<PeripheralEvent.ValueChanged> {
                        it.characteristic == characteristic.characteristic
                    }
                    .mapNotNull { it.data }
                    .onEach { send(it) }
                    .launchIn(this)

                awaitClose {
                    if (connectionStatus.value == KmpBleConnectionStatus.Disconnected) return@awaitClose
                    peripheral.setNotifyValue(false, characteristic.characteristic)
                }
            }
            .flowIn(scope)

    override suspend fun openL2CapChannel(psm: Int): Outcome<IKmpBleL2CapChannel, KmpBleError> =
        withScope(scope) {
            peripheral.openL2CAPChannel(psm.convert())
            val event = events.filterIsInstance<PeripheralEvent.L2CapChannelOpened>().first()
            val channel = event.channel
            if (event.error != null || channel == null) {
                return@withScope Outcome.Error(KmpBleError.Unknown(event.error ?: "L2CAP open failed"))
            }
            Outcome.Ok(AppleKmpBleL2CapChannel(channel))
        }
}

private class ApplePeripheralDelegate(
    private val scope: CoroutineScope,
    private val canSendWriteWithoutResponseFlow: MutableStateFlow<Boolean>,
    private val events: MutableSharedFlow<PeripheralEvent>,
) : CBPeripheralDelegateProtocol, NSObject() {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        events.tryEmit(PeripheralEvent.DiscoveredOrModifiedServices(peripheral, didDiscoverServices))
    }

    override fun peripheral(peripheral: CBPeripheral, didModifyServices: List<*>) {
        events.tryEmit(PeripheralEvent.DiscoveredOrModifiedServices(peripheral, null))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        val event = PeripheralEvent.DiscoveredCharacteristics(peripheral, didDiscoverCharacteristicsForService, error)
        events.tryEmit(event)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        events.tryEmit(PeripheralEvent.WriteWithResponse(didWriteValueForCharacteristic, error))
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        events.tryEmit(
            PeripheralEvent.ValueChanged(
                didUpdateValueForCharacteristic,
                didUpdateValueForCharacteristic.value
                    ?.toByteArray(), // Make sure to send the current data because the characteristic value may update
                // before the value is handled in the flow
                error,
            )
        )
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        events.tryEmit(PeripheralEvent.NotificationStateChanged(didUpdateNotificationStateForCharacteristic, error))
    }

    override fun peripheralIsReadyToSendWriteWithoutResponse(peripheral: CBPeripheral) {
        canSendWriteWithoutResponseFlow.value = true
    }

    @ObjCSignatureOverride
    override fun peripheral(peripheral: CBPeripheral, didOpenL2CAPChannel: CBL2CAPChannel?, error: NSError?) {
        events.tryEmit(PeripheralEvent.L2CapChannelOpened(didOpenL2CAPChannel, error))
    }
}

private sealed class PeripheralEvent {
    data class DiscoveredOrModifiedServices(val peripheral: CBPeripheral, val error: NSError?) : PeripheralEvent()

    data class DiscoveredCharacteristics(val peripheral: CBPeripheral, val service: CBService, val error: NSError?) :
        PeripheralEvent()

    data class WriteWithResponse(val characteristic: CBCharacteristic, val error: NSError?) : PeripheralEvent()

    data class ValueChanged(val characteristic: CBCharacteristic, val data: ByteArray?, val error: NSError?) :
        PeripheralEvent()

    data class NotificationStateChanged(val characteristic: CBCharacteristic, val error: NSError?) : PeripheralEvent()

    data class L2CapChannelOpened(val channel: CBL2CAPChannel?, val error: NSError?) : PeripheralEvent()
}

private class AppleKmpBleService(private val service: CBService, private val peripheral: AppleKmpBlePeripheral) :
    IKmpBleService {
    override val uuid: Uuid = formatCbUuid(service.UUID)
    override val characteristics: Map<Uuid, IKmpBleCharacteristic> = buildMap {
        service.characteristics?.forEach {
            if (it !is CBCharacteristic) return@forEach
            put(formatCbUuid(it.UUID), AppleKmpBleCharacteristic(it, peripheral))
        }
    }
}

private class AppleKmpBleCharacteristic(
    internal val characteristic: CBCharacteristic,
    private val peripheral: AppleKmpBlePeripheral,
) : IKmpBleCharacteristic {

    override val uuid = formatCbUuid(characteristic.UUID)
    internal val lock = Mutex()

    override val properties: List<KmpBleGattProperty> = buildList {
        val props = characteristic.properties
        if ((props and CBCharacteristicPropertyBroadcast) > 0u) add(KmpBleGattProperty.Broadcast)
        if ((props and CBCharacteristicPropertyRead) > 0u) add(KmpBleGattProperty.Read)
        if ((props and CBCharacteristicPropertyWriteWithoutResponse) > 0u) add(KmpBleGattProperty.WriteWithoutResponse)
        if ((props and CBCharacteristicPropertyWrite) > 0u) add(KmpBleGattProperty.Write)
        if ((props and CBCharacteristicPropertyNotify) > 0u) add(KmpBleGattProperty.Notify)
        if ((props and CBCharacteristicPropertyIndicate) > 0u) add(KmpBleGattProperty.Indicate)
        if ((props and CBCharacteristicPropertyAuthenticatedSignedWrites) > 0u) add(KmpBleGattProperty.SignedWrite)
        if ((props and CBCharacteristicPropertyExtendedProperties) > 0u) add(KmpBleGattProperty.ExtendedProps)
    }

    override suspend fun read(): Outcome<ByteArray, KmpBleError> = peripheral.read(uuid)

    override suspend fun write(data: ByteArray, mode: KmpBleWriteMode): Outcome<Unit, KmpBleError> =
        peripheral.write(uuid, data, mode)

    override suspend fun notifications(bufferSize: Int, bufferOverflow: BufferOverflow): Flow<ByteArray> =
        peripheral.notifications(uuid)
}

private inline fun NSArray.forEach(block: (Any?) -> Unit) {
    for (i in 0 until count().toInt()) {
        val item = objectAtIndex(i.toULong()) ?: continue
        block(item)
    }
}

private inline fun NSDictionary.forEach(block: (Any, Any?) -> Unit) {
    val keys = keyEnumerator()
    var key = keys.nextObject()

    while (key != null) {
        block(key, objectForKey(key))
        key = keys.nextObject()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    // TODO: Test this
    //    return bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: byteArrayOf()

    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    if (length > Int.MAX_VALUE.toULong()) throw IllegalStateException("NSData is too large to fit into a ByteArray")

    return ByteArray(size).apply { usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) } }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData.data()

    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

private fun KmpBleWriteMode.toCbType(): Long =
    when (this) {
        KmpBleWriteMode.WithResponse -> CBCharacteristicWriteWithResponse
        KmpBleWriteMode.WithoutResponse -> CBCharacteristicWriteWithoutResponse
    }

private fun formatCbUuid(uuid: CBUUID): Uuid {
    return if (uuid.UUIDString.length == 4) {
        Uuid.parseHexDash("0000${uuid.UUIDString}-0000-1000-8000-00805f9b34fb")
    } else {
        Uuid.fromByteArray(uuid.data.toByteArray())
    }
}
