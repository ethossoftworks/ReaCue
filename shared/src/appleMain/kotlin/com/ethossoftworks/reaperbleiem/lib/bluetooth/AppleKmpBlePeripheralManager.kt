package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.ethossoftworks.reaperbleiem.service.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementCharacteristic
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementData
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementService
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleCentralId
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleError
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralEvent
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult
import com.outsidesource.oskitkmp.lib.update
import kotlin.time.Duration.Companion.milliseconds
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
import platform.CoreBluetooth.CBManagerStatePoweredOn
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

val KmpBleCbPeripheralQueue: dispatch_queue_t = dispatch_queue_create("kmp-ble-peripheral-manager", attr = null)

// TODO: Add all services at once and wait at the end to check for count. Also remove service added event
// TODO: Maybe add state observable for peripheral manager. Need to check how Android handles things.
// TODO: Create write mutex per characteristic
// TODO: Add MTU size property?
class AppleKmpBlePeripheralManager(cbPeripheralManagerFactory: (() -> CBPeripheralManager)? = null) :
    CBPeripheralManagerDelegateProtocol, NSObject(), IKmpBlePeripheralManager {
    internal val events = MutableSharedFlow<KmpBlePeripheralEvent>(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    private val sendMutex = Mutex()
    private val peripheralManagerIsReadyToUpdateSubscribers = Channel<Unit>(Channel.CONFLATED)
    internal val peripheralManager: CBPeripheralManager by lazy {
        val manager = cbPeripheralManagerFactory?.invoke() ?: CBPeripheralManager(null, KmpBleCbPeripheralQueue)
        manager.apply { delegate = this@AppleKmpBlePeripheralManager }
    }

    private val localCharacteristics: AtomicRef<Map<String, CBMutableCharacteristic>> = atomic(emptyMap())
    private val localCentrals: AtomicRef<Map<String, CBCentral>> = atomic(emptyMap())
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>> = atomic(emptyMap())
    private val _state = MutableStateFlow(peripheralManager.state)

    private val requestIdCounter = atomic(0)

    override suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralEvent> =
        callbackFlow {
            awaitPeripheralManagerPoweredOn() ?: return@callbackFlow

            try {
                advertisementData.services.forEach { service ->
                    peripheralManager.addService(service.toMutableService())

                    val response = events.first {
                        it is KmpBlePeripheralEvent.Error || it == KmpBlePeripheralEvent.ServiceAdded(service.uuid)
                    }
                    send(response)

                    if (response is KmpBlePeripheralEvent.Error) close()
                }

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

                events.onEach { send(it) }.launchIn(this)
            } catch (t: Throwable) {
                send(KmpBlePeripheralEvent.Error(KmpBleError.Unknown(t)))
                close()
            }

            awaitClose { peripheralManager.stopAdvertising() }
        }

    private fun KmpBleAdvertisementService.toMutableService() =
        CBMutableService(type = CBUUID.UUIDWithString(uuid), primary = isPrimaryService).apply {
            val characteristics =
                this@toMutableService.characteristics.map { characteristic ->
                    val mutableCharacteristic = localCharacteristics.update {
                        it.update { this[characteristic.uuid] = characteristic.toMutableCharacteristic() }
                    }
                    mutableCharacteristic
                }
            setCharacteristics(characteristics)
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

    /** IKmpPeripheralManager */
    override suspend fun notify(characteristicUuid: String, data: ByteArray, centralUuids: List<String>?): Unit =
        sendMutex.withLock {
            val characteristic = localCharacteristics.value[characteristicUuid] ?: return
            val centrals = centralUuids?.mapNotNull { localCentrals.value[it] }
            while (!peripheralManager.updateValue(data.toNSData(), characteristic, centrals)) {
                peripheralManagerIsReadyToUpdateSubscribers.receive()
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

    /** NSPeripheralManager */
    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        _state.value = peripheral.state
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        if (error != null) {
            events.tryEmit(KmpBlePeripheralEvent.Error(KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(KmpBlePeripheralEvent.ServiceAdded(didAddService.UUID.UUIDString()))
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
                characteristicUuid = request.characteristic.UUID.UUIDString(),
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
                    characteristicUuid = request.characteristic.UUID.UUIDString(),
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
        events.tryEmit(
            KmpBlePeripheralEvent.CentralSubscribed(
                centralId = central.identifier.UUIDString(),
                didSubscribeToCharacteristic.UUID.UUIDString(),
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

        events.tryEmit(
            KmpBlePeripheralEvent.CentralUnsubscribed(
                centralId = central.identifier.UUIDString(),
                didUnsubscribeFromCharacteristic.UUID.UUIDString(),
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

    /** Helpers */
    private suspend fun awaitPeripheralManagerPoweredOn(): Unit? {
        return withTimeoutOrNull(2_000.milliseconds) {
            _state.first { it == CBManagerStatePoweredOn }
            Unit
        }
    }
}
