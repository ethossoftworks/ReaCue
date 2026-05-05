package com.ethossoftworks.reaperbleiem.service.bluetooth

import kotlinx.coroutines.flow.Flow

interface IKmpBlePeripheralManager {
    suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralManagerEvent>

    suspend fun notify(data: ByteArray, characteristicUuid: String, centralUuids: List<String>? = null)

    val writeRequests: Flow<KmpBlePeripheralWriteRequest>
    val readRequests: Flow<KmpBlePeripheralReadRequest>

    suspend fun respondToRequest(
        central: KmpBleCentralId,
        requestId: Int,
        offset: Int,
        result: KmpBlePeripheralGattResult,
        value: ByteArray,
    )
}

data class KmpBlePeripheralReadRequest(
    val central: KmpBleCentralId,
    val characteristicUuid: String,
    val requestId: Int,
    val offset: Int,
)

data class KmpBlePeripheralWriteRequest(
    val central: KmpBleCentralId,
    val characteristicUuid: String,
    val requestId: Int,
    val offset: Int,
    val data: ByteArray,
)

sealed class KmpBlePeripheralGattResult {
    data object Success : KmpBlePeripheralGattResult()
    data object ReadNotPermitted : KmpBlePeripheralGattResult()
    data object WriteNotPermitted : KmpBlePeripheralGattResult()
    data object RequestNotSupported : KmpBlePeripheralGattResult()
    data object InsufficientAuthentication : KmpBlePeripheralGattResult()
    data object InsufficientEncryption : KmpBlePeripheralGattResult()
    data object InvalidOffset : KmpBlePeripheralGattResult()
    data object InvalidAttributeLength : KmpBlePeripheralGattResult()
    data object Failure : KmpBlePeripheralGattResult()
    /** `value` must be 0x80 - 0x9F */
    data class UserDefined(val value: Byte) : KmpBlePeripheralGattResult()
}

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

sealed class KmpBlePeripheralManagerEvent {
    data class Error(val error: KmpBleError) : KmpBlePeripheralManagerEvent()

    data class ServiceAdded(val uuid: String) : KmpBlePeripheralManagerEvent()

    data object Advertising : KmpBlePeripheralManagerEvent()

    data class CentralSubscribedToCharacteristic(val centralId: KmpBleCentralId, val characteristicUuid: String) :
        KmpBlePeripheralManagerEvent()

    data class CentralUnsubscribedFromCharacteristic(val centralId: KmpBleCentralId, val characteristicUuid: String) :
        KmpBlePeripheralManagerEvent()
}

typealias KmpBleCentralId = String
