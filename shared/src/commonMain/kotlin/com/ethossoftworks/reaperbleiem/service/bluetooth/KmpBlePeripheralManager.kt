package com.ethossoftworks.reaperbleiem.service.bluetooth

import kotlinx.coroutines.flow.Flow

interface IKmpBlePeripheralManager {
    suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralEvent>

    suspend fun notify(characteristicUuid: String, data: ByteArray, centralUuids: List<String>? = null)

    suspend fun respondToRequest(
        central: KmpBleCentralId,
        requestId: Int,
        offset: Int,
        result: KmpBlePeripheralGattResult,
        value: ByteArray,
    )
}

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

sealed class KmpBlePeripheralEvent {
    data class Error(val error: KmpBleError) : KmpBlePeripheralEvent()

    data object Advertising : KmpBlePeripheralEvent()

    data class CentralSubscribed(val centralId: KmpBleCentralId, val characteristicUuid: String) :
        KmpBlePeripheralEvent()

    data class CentralUnsubscribed(val centralId: KmpBleCentralId, val characteristicUuid: String) :
        KmpBlePeripheralEvent()

    data class ReadRequest(
        val central: KmpBleCentralId,
        val characteristicUuid: String,
        val requestId: Int,
        val offset: Int,
    ) : KmpBlePeripheralEvent()

    data class WriteRequest(
        val central: KmpBleCentralId,
        val characteristicUuid: String,
        val requestId: Int,
        val offset: Int,
        val data: ByteArray,
    ) : KmpBlePeripheralEvent()
}

typealias KmpBleCentralId = String
