package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.lib.update
import kotlin.time.Duration.Companion.seconds
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheralManager
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
    private val sendMutexes = atomic<Map<String, Mutex>>(emptyMap())
    private val events = MutableSharedFlow<KmpBlePeripheralEvent>(extraBufferCapacity = Channel.UNLIMITED)
    private val serviceAddedEvents = Channel<ServiceAddedEvent>(Channel.CONFLATED)
    private val peripheralManagerIsReadyToUpdateSubscribers = Channel<Unit>(Channel.CONFLATED)
    private val localCharacteristics: AtomicRef<Map<String, CBMutableCharacteristic>> = atomic(emptyMap())
    private val localCentrals: AtomicRef<Map<String, CBCentral>> = atomic(emptyMap())
    private val localCentralSubscriptions: AtomicRef<Map<String, Set<String>>> = atomic(emptyMap())
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>> = atomic(emptyMap())
    private val _state: MutableStateFlow<CBManagerState> = MutableStateFlow(CBManagerStateUnknown)

    private val peripheralDelegate =
        PeripheralManagerDelegate(
            events = events,
            localCentrals = localCentrals,
            localCentralSubscriptions = localCentralSubscriptions,
            localRequests = localRequests,
            serviceAddedEvents = serviceAddedEvents,
            state = _state,
            peripheralManagerIsReadyToUpdateSubscribers = peripheralManagerIsReadyToUpdateSubscribers,
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

    override fun subscribedCentrals(characteristicUuid: String): Set<String> {
        return localCentralSubscriptions.value[characteristicUuid] ?: emptySet()
    }

    override suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralEvent> =
        callbackFlow {
            awaitPeripheralManagerPoweredOn()
                ?: run {
                    send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown("Bluetooth not turned on")))
                    close()
                }

            try {
                while (serviceAddedEvents.tryReceive().isSuccess) {
                    // Drain any stale events. This should happen in typical usage, but conflated channels will hold on
                    // to old data if advertise is stopped
                }

                for (service in advertisementData.services) {
                    for (characteristic in service.characteristics) {
                        sendMutexes.update { it + (characteristic.uuid to Mutex()) }
                    }
                    peripheralManager.addService(createMutableService(service))

                    val response = serviceAddedEvents.receive()
                    if (response.error != null) {
                        send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown("Error adding service ${response.error}")))
                        close()
                    }
                }

                events.onEach { send(it) }.launchIn(this)

                peripheralManager.startAdvertising(
                    buildMap {
                        this[CBAdvertisementDataLocalNameKey] = advertisementData.name
                        this[CBAdvertisementDataServiceUUIDsKey] =
                            advertisementData.services.map { CBUUID.UUIDWithString(it.uuid) }
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
                localCharacteristics.update { emptyMap() }
                localCentrals.update { emptyMap() }
                localCentralSubscriptions.update { emptyMap() }
                localRequests.update { emptyMap() }
                sendMutexes.update { emptyMap() }
            }
        }

    private fun createMutableService(service: KmpBleAdvertisementService): CBMutableService {
        return CBMutableService(type = CBUUID.UUIDWithString(service.uuid), primary = service.isPrimaryService).apply {
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
            type = CBUUID.UUIDWithString(uuid),
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

    // TODO: I'm not a fan of the silent error here, even though this should never happen
    override suspend fun notify(characteristicUuid: String, data: ByteArray, centrals: List<KmpBleCentralId>?) {
        val mutex = sendMutexes.value[characteristicUuid] ?: return
        val characteristic = localCharacteristics.value[characteristicUuid] ?: return
        val centralsRefs = centrals?.mapNotNull { localCentrals.value[it] }

        mutex.withLock {
            while (peripheralManagerIsReadyToUpdateSubscribers.tryReceive().isSuccess) {
                // Drain ready signal
            }

            while (!peripheralManager.updateValue(data.toNSData(), characteristic, centralsRefs)) {
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
            request = request,
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
    private val localCentrals: AtomicRef<Map<String, CBCentral>>,
    private val localCentralSubscriptions: AtomicRef<Map<String, Set<String>>>,
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>>,
    private val serviceAddedEvents: Channel<ServiceAddedEvent>,
    private val state: MutableStateFlow<CBManagerState>,
    private val peripheralManagerIsReadyToUpdateSubscribers: Channel<Unit>,
) : CBPeripheralManagerDelegateProtocol, NSObject() {
    private val requestIdCounter = atomic(0)

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        state.value = peripheral.state
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        serviceAddedEvents.trySend(ServiceAddedEvent(uuid = didAddService.UUID.UUIDString().lowercase(), error = error))
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
        val request = didReceiveReadRequest
        val requestId = requestIdCounter.getAndIncrement()
        val centralUuid = request.central.identifier.UUIDString()
        localCentrals.update { it.update { this[centralUuid] = request.central } }
        localRequests.update { it.update { this[requestId] = request } }

        events.tryEmit(
            KmpBlePeripheralEvent.ReadRequest(
                central = centralUuid,
                characteristicUuid = request.characteristic.UUID.UUIDString().lowercase(),
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
                    central = centralUuid,
                    characteristicUuid = request.characteristic.UUID.UUIDString().lowercase(),
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
                val characteristicUuid = didSubscribeToCharacteristic.UUID.UUIDString().lowercase()
                this[characteristicUuid] = (this[characteristicUuid] ?: emptySet()) + central.identifier.UUIDString()
            }
        }
        events.tryEmit(
            KmpBlePeripheralEvent.CentralSubscribed(
                centralId = central.identifier.UUIDString(),
                didSubscribeToCharacteristic.UUID.UUIDString().lowercase(),
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
                val characteristicUuid = didUnsubscribeFromCharacteristic.UUID.UUIDString().lowercase()
                this[characteristicUuid] = (this[characteristicUuid] ?: emptySet()) - central.identifier.UUIDString()
            }
        }

        events.tryEmit(
            KmpBlePeripheralEvent.CentralUnsubscribed(
                centralId = central.identifier.UUIDString(),
                didUnsubscribeFromCharacteristic.UUID.UUIDString().lowercase(),
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

private data class ServiceAddedEvent(val uuid: String, val error: NSError? = null)
