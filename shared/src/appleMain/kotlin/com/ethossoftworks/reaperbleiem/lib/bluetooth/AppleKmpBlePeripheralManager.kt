package com.ethossoftworks.reaperbleiem.lib.bluetooth

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

// TODO: Add all services at once and wait at the end to check for count
// TODO: Maybe add state observable for peripheral manager. Need to check how Android handles things.
// TODO: Create write mutex per characteristic
class AppleKmpBlePeripheralManager(cbPeripheralManagerFactory: (() -> CBPeripheralManager)? = null) :
    CBPeripheralManagerDelegateProtocol, NSObject(),
    com.ethossoftworks.reaperbleiem.service.bluetooth.IKmpBlePeripheralManager {
    internal val events =
        MutableSharedFlow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent>(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    private val sendMutex = Mutex()
    private val peripheralManagerIsReadyToUpdateSubscribers = Channel<Unit>(Channel.CONFLATED)
    internal val peripheralManager: CBPeripheralManager by lazy {
        val manager = cbPeripheralManagerFactory?.invoke() ?: CBPeripheralManager(null, KmpBleCbPeripheralQueue)
        manager.apply { delegate = this@AppleKmpBlePeripheralManager }
    }

    private val localCharacteristics: AtomicRef<Map<String, CBMutableCharacteristic>> = atomic(emptyMap())
    private val localCentrals: AtomicRef<Map<String, CBCentral>> = atomic(emptyMap())
    private val localRequests: AtomicRef<Map<Int, CBATTRequest>> = atomic(emptyMap())
    private val localWriteRequestsFlow =
        MutableSharedFlow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralWriteRequest>(extraBufferCapacity = Channel.UNLIMITED)
    private val localReadRequestsFlow =
        MutableSharedFlow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralReadRequest>(extraBufferCapacity = Channel.UNLIMITED)
    private val _state = MutableStateFlow(peripheralManager.state)

    private val requestIdCounter = atomic(0)

    override suspend fun advertise(advertisementData: com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementData): Flow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent> =
        callbackFlow {
            awaitPeripheralManagerPoweredOn() ?: return@callbackFlow

            try {
                advertisementData.services.forEach { service ->
                    val mutableService =
                        CBMutableService(type = CBUUID.UUIDWithString(service.uuid), primary = service.isPrimaryService)
                            .apply {
                                val characteristics =
                                    service.characteristics.map { characteristic ->
                                        val mutableCharacteristic =
                                            CBMutableCharacteristic(
                                                type = CBUUID.UUIDWithString(characteristic.uuid),
                                                value = null,
                                                properties =
                                                    characteristic.properties.fold(0uL) { accum, property ->
                                                        when (property) {
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.Broadcast ->
                                                                accum or CBCharacteristicPropertyBroadcast
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.Read ->
                                                                accum or CBCharacteristicPropertyRead
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.WriteWithoutResponse ->
                                                                accum or CBCharacteristicPropertyWriteWithoutResponse
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.Write ->
                                                                accum or CBCharacteristicPropertyWrite
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.Notify ->
                                                                accum or CBCharacteristicPropertyNotify
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.Indicate ->
                                                                accum or CBCharacteristicPropertyIndicate
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.SignedWrite ->
                                                                accum or
                                                                    CBCharacteristicPropertyAuthenticatedSignedWrites
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty.ExtendedProps ->
                                                                accum or CBCharacteristicPropertyExtendedProperties
                                                        }
                                                    },
                                                permissions =
                                                    characteristic.permissions.fold(0uL) { accum, permission ->
                                                        when (permission) {
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission.Readable ->
                                                                accum or CBAttributePermissionsReadable
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission.Writable ->
                                                                accum or CBAttributePermissionsWriteable
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission.ReadEncryptionRequired ->
                                                                accum or CBAttributePermissionsReadEncryptionRequired
                                                            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission.WriteEncryptionRequired ->
                                                                accum or CBAttributePermissionsWriteEncryptionRequired
                                                        }
                                                    },
                                            )
                                        localCharacteristics.update {
                                            it.update { this[characteristic.uuid] = mutableCharacteristic }
                                        }
                                        mutableCharacteristic
                                    }
                                setCharacteristics(characteristics)
                            }

                    peripheralManager.addService(mutableService)

                    val response = events.first {
                        it is com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Error ||
                            it == _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.ServiceAdded(service.uuid)
                    }
                    send(response)

                    if (response is com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Error) close()
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
                send(
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Error(
                        _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleError.Unknown(t)))
                close()
            }

            awaitClose { peripheralManager.stopAdvertising() }
        }

    /** IKmpPeripheralManager */
    override suspend fun notify(data: ByteArray, characteristicUuid: String, centralUuids: List<String>?): Unit =
        sendMutex.withLock {
            val characteristic = localCharacteristics.value[characteristicUuid] ?: return
            val centrals = centralUuids?.mapNotNull { localCentrals.value[it] }
            while (!peripheralManager.updateValue(data.toNSData(), characteristic, centrals)) {
                peripheralManagerIsReadyToUpdateSubscribers.receive()
            }
        }

    override val readRequests: Flow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralReadRequest> = localReadRequestsFlow
    override val writeRequests: Flow<com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralWriteRequest> = localWriteRequestsFlow

    override suspend fun respondToRequest(
        central: com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleCentralId,
        requestId: Int,
        offset: Int,
        result: com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult,
        value: ByteArray,
    ) {
        val request = localRequests.value[requestId] ?: return
        localRequests.update { it.update { remove(requestId) } }

        peripheralManager.respondToRequest(
            request = request,
            withResult =
                when (result) {
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.Failure -> CBATTErrorUnlikelyError
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.InsufficientAuthentication -> CBATTErrorInsufficientAuthentication
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.InsufficientEncryption -> CBATTErrorInsufficientEncryption
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.InvalidAttributeLength -> CBATTErrorInvalidAttributeValueLength
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.InvalidOffset -> CBATTErrorInvalidOffset
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.ReadNotPermitted -> CBATTErrorReadNotPermitted
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.RequestNotSupported -> CBATTErrorRequestNotSupported
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.Success -> CBATTErrorSuccess
                    is com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.UserDefined -> result.value.toLong()
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralGattResult.WriteNotPermitted -> CBATTErrorWriteNotPermitted
                },
        )
    }

    /** NSPeripheralManager */
    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
        _state.value = peripheral.state
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        if (error != null) {
            events.tryEmit(
                _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Error(
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(_root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.ServiceAdded(didAddService.UUID.UUIDString()))
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
        val request = didReceiveReadRequest
        val requestId = requestIdCounter.getAndIncrement()
        val centralUuid = request.central.identifier.UUIDString()
        localCentrals.update { it.update { this[centralUuid] = request.central } }
        localRequests.update { it.update { this[requestId] = request } }

        localReadRequestsFlow.tryEmit(
            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralReadRequest(
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

            localWriteRequestsFlow.tryEmit(
                _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralWriteRequest(
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
            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.CentralSubscribedToCharacteristic(
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
            _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.CentralUnsubscribedFromCharacteristic(
                centralId = central.identifier.UUIDString(),
                didUnsubscribeFromCharacteristic.UUID.UUIDString(),
            )
        )
    }

    override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
        if (error != null) {
            events.tryEmit(
                _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Error(
                    _root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(_root_ide_package_.com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralManagerEvent.Advertising)
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
