@file:OptIn(ExperimentalUuidApi::class, ExperimentalForeignApi::class)

package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBL2CAPPSM
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorUnlikelyError
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteEncryptionRequired
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyAuthenticatedSignedWrites
import platform.CoreBluetooth.CBCharacteristicPropertyBroadcast
import platform.CoreBluetooth.CBCharacteristicPropertyExtendedProperties
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerConnectionLatencyHigh
import platform.CoreBluetooth.CBPeripheralManagerConnectionLatencyLow
import platform.CoreBluetooth.CBPeripheralManagerConnectionLatencyMedium
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Suppress("FunctionNaming")
fun KmpBlePeripheralManager(cbPeripheralFactory: (() -> CBPeripheralManager)? = null): IKmpBlePeripheralManager =
    AppleKmpBlePeripheralManager(cbPeripheralFactory)

private val KmpBleCbPeripheralQueue: dispatch_queue_t = dispatch_queue_create("kmp-ble-peripheral-manager", attr = null)

// TODO: Maybe add state observable for peripheral manager. Need to check how Android handles things.
class AppleKmpBlePeripheralManager(cbPeripheralManagerFactory: (() -> CBPeripheralManager)? = null) :
    IKmpBlePeripheralManager {
    private val events = MutableSharedFlow<KmpBlePeripheralEvent>(extraBufferCapacity = Channel.UNLIMITED)
    private val serviceAddedEvents = Channel<ServiceAddedEvent>(Channel.CONFLATED)
    private val peripheralManagerIsReadyToUpdateSubscribers = Channel<Unit>(Channel.CONFLATED)
    private val notificationQueue: Channel<BleNotification> = Channel(Channel.UNLIMITED)
    private val localCharacteristics: AtomicRef<Map<Uuid, CBMutableCharacteristic>> = atomic(emptyMap())
    private val localCentrals: AtomicRef<Map<String, CBCentral>> = atomic(emptyMap())
    private val localCentralSubscriptions: AtomicRef<Map<Uuid, Set<KmpBleCentralId>>> = atomic(emptyMap())
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>> = atomic(emptyMap())
    private val _state: MutableStateFlow<CBManagerState> = MutableStateFlow(CBManagerStateUnknown)
    private val l2capPublishEvents = Channel<Outcome<Int, KmpBleError>>(Channel.CONFLATED)

    private val peripheralDelegate =
        PeripheralManagerDelegate(
            events = events,
            localCentrals = localCentrals,
            localCentralSubscriptions = localCentralSubscriptions,
            localRequests = localRequests,
            serviceAddedEvents = serviceAddedEvents,
            state = _state,
            peripheralManagerIsReadyToUpdateSubscribers = peripheralManagerIsReadyToUpdateSubscribers,
            l2capPublishEvents = l2capPublishEvents,
        )

    private val peripheralManager: CBPeripheralManager by lazy {
        val manager =
            cbPeripheralManagerFactory?.invoke()
                ?: CBPeripheralManager(null, KmpBleCbPeripheralQueue).apply { delegate = peripheralDelegate }
        _state.tryEmit(manager.state)
        manager
    }

    /** IKmpPeripheralManager */
    override fun maximumUpdateValueLengthForCentral(central: KmpBleCentralId): UInt {
        return localCentrals.value[central]?.maximumUpdateValueLength()?.toUInt() ?: 20u
    }

    override fun subscribedCentrals(characteristic: Uuid): Set<KmpBleCentralId> {
        return localCentralSubscriptions.value[characteristic] ?: emptySet()
    }

    override suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority, centralId: KmpBleCentralId) {
        val latency =
            when (priority) {
                KmpBleConnectionPriority.High -> CBPeripheralManagerConnectionLatencyLow
                KmpBleConnectionPriority.Balanced -> CBPeripheralManagerConnectionLatencyMedium
                KmpBleConnectionPriority.Low -> CBPeripheralManagerConnectionLatencyHigh
            }
        peripheralManager.setDesiredConnectionLatency(
            latency = latency,
            forCentral = localCentrals.value[centralId] ?: return,
        )
    }

    override suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralEvent> =
        callbackFlow {
            awaitPeripheralManagerPoweredOn()
                ?: run {
                    send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown("Bluetooth not turned on")))
                    close()
                }

            try {
                for (service in advertisementData.services) {
                    peripheralManager.addService(createMutableService(service))

                    val response = serviceAddedEvents.receive()
                    if (response.error != null) {
                        send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown("Error adding service ${response.error}")))
                        close()
                    }
                }

                events.onEach { send(it) }.launchIn(this)

                _state
                    .onEach { state ->
                        if (state != CBManagerStatePoweredOff) return@onEach
                        send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown("Bluetooth is turned off")))
                        close()
                    }
                    .launchIn(this)

                launch { processNotificationQueue() }

                peripheralManager.startAdvertising(
                    buildMap {
                        this[CBAdvertisementDataLocalNameKey] = advertisementData.name
                        this[CBAdvertisementDataServiceUUIDsKey] =
                            advertisementData.services.map { CBUUID.UUIDWithData(it.uuid.toByteArray().toNSData()) }
                        if (advertisementData.manufacturerData != null) {
                            this[CBAdvertisementDataManufacturerDataKey] = advertisementData.manufacturerData
                        }
                    }
                )
            } catch (t: Throwable) {
                send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown(t)))
                close()
            }

            awaitClose {
                peripheralManager.stopAdvertising()
                peripheralManager.removeAllServices()
                localCharacteristics.update { emptyMap() }
                localCentrals.update { emptyMap() }
                localCentralSubscriptions.update { emptyMap() }
                localRequests.update { emptyMap() }

                // Drain any stale events. channels will hold on to old data if advertise is stopped
                while (serviceAddedEvents.tryReceive().isSuccess) {}
                while (notificationQueue.tryReceive().isSuccess) {}
            }
        }

    private fun createMutableService(service: KmpBleAdvertisementService): CBMutableService {
        return CBMutableService(
                type = CBUUID.UUIDWithData(service.uuid.toByteArray().toNSData()),
                primary = service.isPrimaryService,
            )
            .apply {
                val characteristics =
                    service.characteristics.map { characteristic ->
                        val mutableCharacteristic = characteristic.toMutableCharacteristic()
                        localCharacteristics.update { it.update { this[characteristic.uuid] = mutableCharacteristic } }
                        mutableCharacteristic
                    }
                setCharacteristics(characteristics)
            }
    }

    private fun KmpBleAdvertisementCharacteristic.toMutableCharacteristic() =
        CBMutableCharacteristic(
            type = CBUUID.UUIDWithData(uuid.toByteArray().toNSData()),
            value = null,
            properties =
                properties.fold(0uL) { accum, property ->
                    when (property) {
                        KmpBleGattProperty.Broadcast -> accum or CBCharacteristicPropertyBroadcast

                        KmpBleGattProperty.Read -> accum or CBCharacteristicPropertyRead

                        KmpBleGattProperty.WriteWithoutResponse -> accum or CBCharacteristicPropertyWriteWithoutResponse

                        KmpBleGattProperty.Write -> accum or CBCharacteristicPropertyWrite

                        KmpBleGattProperty.Notify -> accum or CBCharacteristicPropertyNotify

                        KmpBleGattProperty.Indicate -> accum or CBCharacteristicPropertyIndicate

                        KmpBleGattProperty.SignedWrite -> accum or CBCharacteristicPropertyAuthenticatedSignedWrites

                        KmpBleGattProperty.ExtendedProps -> accum or CBCharacteristicPropertyExtendedProperties
                    }
                },
            permissions =
                permissions.fold(0uL) { accum, permission ->
                    when (permission) {
                        KmpBleGattPermission.Readable -> accum or CBAttributePermissionsReadable

                        KmpBleGattPermission.Writable -> accum or CBAttributePermissionsWriteable

                        KmpBleGattPermission.ReadEncryptionRequired ->
                            accum or CBAttributePermissionsReadEncryptionRequired

                        KmpBleGattPermission.WriteEncryptionRequired ->
                            accum or CBAttributePermissionsWriteEncryptionRequired
                    }
                },
        )

    override fun notify(characteristic: Uuid, data: ByteArray, centrals: List<KmpBleCentralId>?) {
        notificationQueue.trySend(BleNotification(characteristic, data, centrals))
    }

    private suspend fun processNotificationQueue() {
        for (notification in notificationQueue) {
            val characteristic = localCharacteristics.value[notification.characteristic] ?: return
            val centralsRefs = notification.centrals?.mapNotNull { localCentrals.value[it] }

            while (peripheralManagerIsReadyToUpdateSubscribers.tryReceive().isSuccess) {
                // Drain ready signal
            }

            while (!peripheralManager.updateValue(notification.data.toNSData(), characteristic, centralsRefs)) {
                peripheralManagerIsReadyToUpdateSubscribers.receive()
            }
        }
    }

    override suspend fun respondToRequest(
        central: KmpBleCentralId,
        requestId: Int,
        offset: Int,
        result: KmpBlePeripheralGattResult,
        value: ByteArray,
    ) {
        val request = localRequests.value[requestId] ?: return
        localRequests.update { it.update { remove(requestId) } }

        peripheralManager.respondToRequest(
            request = request.apply { setValue(value.toNSData()) },
            withResult =
                when (result) {
                    KmpBlePeripheralGattResult.Failure -> CBATTErrorUnlikelyError
                    KmpBlePeripheralGattResult.InsufficientAuthentication -> CBATTErrorInsufficientAuthentication
                    KmpBlePeripheralGattResult.InsufficientEncryption -> CBATTErrorInsufficientEncryption
                    KmpBlePeripheralGattResult.InvalidAttributeLength -> CBATTErrorInvalidAttributeValueLength
                    KmpBlePeripheralGattResult.InvalidOffset -> CBATTErrorInvalidOffset
                    KmpBlePeripheralGattResult.ReadNotPermitted -> CBATTErrorReadNotPermitted
                    KmpBlePeripheralGattResult.RequestNotSupported -> CBATTErrorRequestNotSupported
                    KmpBlePeripheralGattResult.Success -> CBATTErrorSuccess
                    is KmpBlePeripheralGattResult.UserDefined -> result.value.toLong()
                    KmpBlePeripheralGattResult.WriteNotPermitted -> CBATTErrorWriteNotPermitted
                },
        )
    }

    override suspend fun publishL2CapChannel(): Outcome<Int, KmpBleError> {
        awaitPeripheralManagerPoweredOn() ?: return Outcome.Error(KmpBleError.PlatformHandlerNotReady)
        while (l2capPublishEvents.tryReceive().isSuccess) {} // drain any stale result
        peripheralManager.publishL2CAPChannelWithEncryption(false)
        return l2capPublishEvents.receive()
    }

    override fun unpublishL2CapChannel(psm: Int) {
        peripheralManager.unpublishL2CAPChannel(psm.convert())
    }

    /** Helpers */
    private suspend fun awaitPeripheralManagerPoweredOn(): Unit? {
        if (peripheralManager.state == CBManagerStatePoweredOn) return Unit

        return withTimeoutOrNull(2.seconds) {
            _state.first { it == CBManagerStatePoweredOn }
            Unit
        }
    }
}

/** NSPeripheralManager */
private class PeripheralManagerDelegate(
    private val events: MutableSharedFlow<KmpBlePeripheralEvent>,
    private val localCentrals: AtomicRef<Map<KmpBleCentralId, CBCentral>>,
    private val localCentralSubscriptions: AtomicRef<Map<Uuid, Set<KmpBleCentralId>>>,
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>>,
    private val serviceAddedEvents: Channel<ServiceAddedEvent>,
    private val state: MutableStateFlow<CBManagerState>,
    private val peripheralManagerIsReadyToUpdateSubscribers: Channel<Unit>,
    private val l2capPublishEvents: Channel<Outcome<Int, KmpBleError>>,
) : CBPeripheralManagerDelegateProtocol, NSObject() {
    private val requestIdCounter = atomic(0)

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didPublishL2CAPChannel: CBL2CAPPSM,
        error: NSError?,
    ) {
        l2capPublishEvents.trySend(
            if (error != null) {
                Outcome.Error(KmpBleError.Unknown(error))
            } else {
                Outcome.Ok(didPublishL2CAPChannel.toInt())
            }
        )
    }

    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        didOpenL2CAPChannel: CBL2CAPChannel?,
        error: NSError?,
    ) {
        val channel = didOpenL2CAPChannel ?: return
        val centralId = channel.peer?.identifier?.UUIDString() ?: return
        events.tryEmit(
            KmpBlePeripheralEvent.L2CapChannelOpened(
                centralId = centralId,
                psm = channel.PSM.toInt(),
                channel = AppleKmpBleL2CapChannel(channel),
            )
        )
    }

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        state.value = peripheral.state
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        serviceAddedEvents.trySend(
            ServiceAddedEvent(uuid = Uuid.fromByteArray(didAddService.UUID.data.toByteArray()), error = error)
        )
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
        val request = didReceiveReadRequest
        val requestId = requestIdCounter.getAndIncrement()
        val centralUuid = request.central.identifier.UUIDString()
        localCentrals.update { it.update { this[centralUuid] = request.central } }
        localRequests.update { it.update { this[requestId] = request } }

        events.tryEmit(
            KmpBlePeripheralEvent.ReadRequest(
                centralId = centralUuid,
                characteristic = Uuid.fromByteArray(request.characteristic.UUID.data.toByteArray()),
                requestId = requestId,
                offset = request.offset.toInt(),
            )
        )
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
        didReceiveWriteRequests.forEach {
            val request = it as? CBATTRequest ?: return@forEach
            val requestId = requestIdCounter.getAndIncrement()
            val centralUuid = request.central.identifier.UUIDString()
            localCentrals.update { it.update { this[centralUuid] = request.central } }
            localRequests.update { it.update { this[requestId] = request } }

            events.tryEmit(
                KmpBlePeripheralEvent.WriteRequest(
                    centralId = centralUuid,
                    characteristic = Uuid.fromByteArray(request.characteristic.UUID.data.toByteArray()),
                    requestId = requestId,
                    offset = request.offset.toInt(),
                    data = request.value?.toByteArray() ?: byteArrayOf(),
                )
            )
        }
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeToCharacteristic: CBCharacteristic,
    ) {
        localCentrals.update { it.update { this[central.identifier.UUIDString()] = central } }
        localCentralSubscriptions.update {
            it.update {
                val characteristicUuid = Uuid.fromByteArray(didSubscribeToCharacteristic.UUID.data.toByteArray())
                this[characteristicUuid] = (this[characteristicUuid] ?: emptySet()) + central.identifier.UUIDString()
            }
        }
        events.tryEmit(
            KmpBlePeripheralEvent.CentralSubscribed(
                centralId = central.identifier.UUIDString(),
                characteristic = Uuid.fromByteArray(didSubscribeToCharacteristic.UUID.data.toByteArray()),
            )
        )
    }

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didUnsubscribeFromCharacteristic: CBCharacteristic,
    ) {
        localCentrals.update { it.update { remove(central.identifier.UUIDString()) } }
        localRequests.update { currentMap ->
            currentMap.filterValues { it.central.identifier.UUIDString() != central.identifier.UUIDString() }
        }
        localCentralSubscriptions.update {
            it.update {
                val characteristicUuid = Uuid.fromByteArray(didUnsubscribeFromCharacteristic.UUID.data.toByteArray())
                this[characteristicUuid] = (this[characteristicUuid] ?: emptySet()) - central.identifier.UUIDString()
            }
        }

        events.tryEmit(
            KmpBlePeripheralEvent.CentralUnsubscribed(
                centralId = central.identifier.UUIDString(),
                characteristic = Uuid.fromByteArray(didUnsubscribeFromCharacteristic.UUID.data.toByteArray()),
            )
        )
    }

    override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
        if (error != null) {
            events.tryEmit(KmpBlePeripheralEvent.Error(KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(KmpBlePeripheralEvent.Advertising)
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {
        peripheralManagerIsReadyToUpdateSubscribers.trySend(Unit)
    }
}

private data class ServiceAddedEvent(val uuid: Uuid, val error: NSError? = null)

private data class BleNotification(val characteristic: Uuid, val data: ByteArray, val centrals: List<KmpBleCentralId>?)
