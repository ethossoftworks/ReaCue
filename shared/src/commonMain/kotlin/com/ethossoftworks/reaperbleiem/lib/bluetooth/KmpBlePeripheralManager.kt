@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.outcome.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

interface IKmpBlePeripheralManager {
    fun maximumUpdateValueLengthForCentral(central: KmpBleCentralId): UInt

    fun subscribedCentrals(characteristic: Uuid): Set<KmpBleCentralId>

    suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority, centralId: KmpBleCentralId)

    suspend fun advertise(advertisementData: KmpBleAdvertisementData): Flow<KmpBlePeripheralEvent>

    fun notify(characteristic: Uuid, data: ByteArray, centrals: List<KmpBleCentralId>? = null)

    suspend fun respondToRequest(
        central: KmpBleCentralId,
        requestId: Int,
        offset: Int = 0,
        result: KmpBlePeripheralGattResult = KmpBlePeripheralGattResult.Success,
        value: ByteArray = byteArrayOf(),
    )

    /**
     * Publishes an L2CAP Connection-Oriented Channel and returns the dynamically assigned PSM. Centrals open a data
     * stream to this PSM (see [IKmpBleL2CapChannel]); each opened channel is surfaced as a
     * [KmpBlePeripheralEvent.L2CapChannelOpened] during [advertise].
     */
    suspend fun publishL2CapChannel(): Outcome<Int, KmpBleError>

    fun unpublishL2CapChannel(psm: Int)
}

typealias KmpBleCentralId = String

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
    val uuid: Uuid,
    val characteristics: List<KmpBleAdvertisementCharacteristic>,
    val isPrimaryService: Boolean,
)

data class KmpBleAdvertisementCharacteristic(
    val uuid: Uuid,
    val properties: Set<KmpBleGattProperty>,
    val permissions: Set<KmpBleGattPermission>,
)

sealed class KmpBlePeripheralEvent {
    data class Error(val error: KmpBleError) : KmpBlePeripheralEvent()

    data object Advertising : KmpBlePeripheralEvent()

    data class CentralSubscribed(val centralId: KmpBleCentralId, val characteristic: Uuid) : KmpBlePeripheralEvent()

    data class CentralUnsubscribed(val centralId: KmpBleCentralId, val characteristic: Uuid) : KmpBlePeripheralEvent()

    data class ReadRequest(
        val centralId: KmpBleCentralId,
        val characteristic: Uuid,
        val requestId: Int,
        val offset: Int,
    ) : KmpBlePeripheralEvent()

    data class WriteRequest(
        val centralId: KmpBleCentralId,
        val characteristic: Uuid,
        val requestId: Int,
        val offset: Int,
        val data: ByteArray,
    ) : KmpBlePeripheralEvent()

    data class L2CapChannelOpened(
        val centralId: KmpBleCentralId,
        val psm: Int,
        val channel: IKmpBleL2CapChannel,
    ) : KmpBlePeripheralEvent()
}
