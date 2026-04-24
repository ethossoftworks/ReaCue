package com.ethossoftworks.reaperbleiem.service.bluetooth

import com.outsidesource.oskitkmp.lib.update
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
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

data class KmpBleAdvertisementData(
    val name: String,
    val services: List<KmpBleAdvertisementService> = emptyList(),
    val manufacturerData: ByteArray? = null, // May only be 31 bytes
)

data class KmpBleAdvertisementService(
    val uuid: String,
    val characteristics: List<KmpBleAdvertisementCharacteristic>,
    val isPrimaryService: Boolean,
)

data class KmpBleAdvertisementCharacteristic(
    val uuid: String,
    val properties: Set<KmpBleGattProperty>,
    val permissions: Set<KmpBleGattPermission>,
)

class AppleKmpBlePeripheralManager(cbPeripheralManagerFactory: (() -> CBPeripheralManager)? = null) :
    CBPeripheralManagerDelegateProtocol, NSObject() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val events =
        MutableSharedFlow<KmpBlePeripheralManagerEvent>(replay = 0, extraBufferCapacity = Channel.UNLIMITED)
    internal val peripheralManager: CBPeripheralManager by lazy {
        val manager = cbPeripheralManagerFactory?.invoke() ?: CBPeripheralManager(null, KmpBleCbPeripheralQueue)
        manager.apply { delegate = this@AppleKmpBlePeripheralManager }
    }

    private val localCharacteristics: AtomicRef<Map<String, CBMutableCharacteristic>> = atomic(emptyMap())
    private val localCentrals: AtomicRef<Map<String, CBCentral>> = atomic(emptyMap())

    suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralManagerEvent> =
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
                                                            KmpBleGattProperty.Broadcast ->
                                                                accum or CBCharacteristicPropertyBroadcast
                                                            KmpBleGattProperty.Read ->
                                                                accum or CBCharacteristicPropertyRead
                                                            KmpBleGattProperty.WriteWithoutResponse ->
                                                                accum or CBCharacteristicPropertyWriteWithoutResponse
                                                            KmpBleGattProperty.Write ->
                                                                accum or CBCharacteristicPropertyWrite
                                                            KmpBleGattProperty.Notify ->
                                                                accum or CBCharacteristicPropertyNotify
                                                            KmpBleGattProperty.Indicate ->
                                                                accum or CBCharacteristicPropertyIndicate
                                                            KmpBleGattProperty.SignedWrite ->
                                                                accum or
                                                                    CBCharacteristicPropertyAuthenticatedSignedWrites
                                                            KmpBleGattProperty.ExtendedProps ->
                                                                accum or CBCharacteristicPropertyExtendedProperties
                                                        }
                                                    },
                                                permissions =
                                                    characteristic.permissions.fold(0uL) { accum, permission ->
                                                        when (permission) {
                                                            KmpBleGattPermission.Readable ->
                                                                accum or CBAttributePermissionsReadable
                                                            KmpBleGattPermission.Writable ->
                                                                accum or CBAttributePermissionsWriteable
                                                            KmpBleGattPermission.ReadEncryptionRequired ->
                                                                accum or CBAttributePermissionsReadEncryptionRequired
                                                            KmpBleGattPermission.WriteEncryptionRequired ->
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
                        it is KmpBlePeripheralManagerEvent.Error ||
                            it == KmpBlePeripheralManagerEvent.ServiceAdded(service.uuid)
                    }
                    send(response)

                    if (response is KmpBlePeripheralManagerEvent.Error) close()
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
                send(KmpBlePeripheralManagerEvent.Error(KmpBleError.Unknown(t)))
                close()
            }

            awaitClose { peripheralManager.stopAdvertising() }
        }

    private suspend fun notify(data: ByteArray, characteristicUuid: String, centralUuids: List<String>? = null) {
        val characteristic = localCharacteristics.value[characteristicUuid] ?: return
        val centrals = centralUuids?.mapNotNull { localCentrals.value[it] }
        peripheralManager.updateValue(data.toNSData(), characteristic, centrals)
    }

    private suspend fun awaitPeripheralManagerPoweredOn(): Unit? {
        return withTimeoutOrNull(2_000.milliseconds) {
            while (isActive) {
                if (peripheralManager.state == CBManagerStatePoweredOn) return@withTimeoutOrNull
                delay(16.milliseconds)
            }
        }
    }

    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {}

    override fun peripheralManager(peripheral: CBPeripheralManager, didAddService: CBService, error: NSError?) {
        if (error != null) {
            events.tryEmit(KmpBlePeripheralManagerEvent.Error(KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(KmpBlePeripheralManagerEvent.ServiceAdded(didAddService.UUID.UUIDString()))
    }

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {}

    override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {}

    @ObjCSignatureOverride
    override fun peripheralManager(
        peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeToCharacteristic: CBCharacteristic,
    ) {
        localCentrals.update { it.update { this[central.identifier.UUIDString()] = central } }
        events.tryEmit(
            KmpBlePeripheralManagerEvent.CentralSubscribedToCharacteristic(
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
        events.tryEmit(
            KmpBlePeripheralManagerEvent.CentralUnsubscribedFromCharacteristic(
                centralId = central.identifier.UUIDString(),
                didUnsubscribeFromCharacteristic.UUID.UUIDString(),
            )
        )
    }

    override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: NSError?) {
        if (error != null) {
            events.tryEmit(KmpBlePeripheralManagerEvent.Error(KmpBleError.Unknown(error)))
            return
        }
        events.tryEmit(KmpBlePeripheralManagerEvent.Advertising)
    }

    override fun peripheralManagerIsReadyToUpdateSubscribers(peripheral: CBPeripheralManager) {}
}

sealed class KmpBlePeripheralManagerEvent {
    data class Error(val error: KmpBleError) : KmpBlePeripheralManagerEvent()

    data class ServiceAdded(val uuid: String) : KmpBlePeripheralManagerEvent()

    data object Advertising : KmpBlePeripheralManagerEvent()

    data class CentralSubscribedToCharacteristic(val centralId: String, val characteristicUuid: String) :
        KmpBlePeripheralManagerEvent()

    data class CentralUnsubscribedFromCharacteristic(val centralId: String, val characteristicUuid: String) :
        KmpBlePeripheralManagerEvent()
}
