package com.ethossoftworks.reaperbleiem.service.bluetooth

import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import com.outsidesource.oskitkmp.lib.containsAny
import com.outsidesource.oskitkmp.outcome.Outcome
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

typealias KmpBleIdentifier = String

data class KmpBleScanRecord(
    val name: String,
    val identifier: KmpBleIdentifier,
    val rssi: Int,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<String, ByteArray>,
    val serviceUuids: List<String>,
)

interface IKmpBle {
    fun scan(): Flow<KmpBleScanRecord>

    suspend fun connect(
        identifier: KmpBleIdentifier,
        scope: CoroutineScope = CoroutineScope(KmpDispatchers.IO + SupervisorJob()),
    ): Outcome<IKmpBlePeripheral, KmpBleError>
}

enum class KmpBleConnectionStatus {
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
}

interface IKmpBlePeripheral {
    val connectionStatus: StateFlow<KmpBleConnectionStatus>
    val services: Map<String, IKmpBleService>
    val characteristics: Map<String, IKmpBleCharacteristic>
    val scope: CoroutineScope

    fun mtu(mode: KmpBleWriteMode = KmpBleWriteMode.WithResponse): Int

    /**
     * [requestMtu] Requests a new MTU
     *
     * @param [size] The size in bytes of the MTU to request. Valid values are 23-517
     */
    suspend fun requestMtu(size: Int): Outcome<Int, KmpBleError>

    suspend fun disconnect(): Outcome<Unit, KmpBleError>

    suspend fun discoverServices(): Outcome<Map<String, IKmpBleService>, KmpBleError>

    /**
     * Await bond attempts to read from an encrypted read characteristic and watches for disconnection. This will cause
     * platforms to show pairing prompts
     *
     * Note: This call should be done before any reads/writes on any characteristic.
     */
    suspend fun awaitBond(encryptedReadCharacteristicUuid: String): Outcome<Unit, KmpBleError>

    suspend fun read(characteristicUuid: String): Outcome<ByteArray, KmpBleError>

    suspend fun write(
        characteristicUuid: String,
        data: ByteArray,
        mode: KmpBleWriteMode = KmpBleWriteMode.WithResponse,
    ): Outcome<Unit, KmpBleError>

    suspend fun notifications(
        characteristicUuid: String,
        bufferSize: Int = 0,
        bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    ): Flow<ByteArray>
}

interface IKmpBleService {
    val uuid: String
    val characteristics: Map<String, IKmpBleCharacteristic>
}

interface IKmpBleCharacteristic {
    val uuid: String
    val properties: List<KmpBleGattProperty>

    val isBroadcastSupported: Boolean
        get() = properties.contains(KmpBleGattProperty.Broadcast)

    val isReadSupported: Boolean
        get() = properties.contains(KmpBleGattProperty.Read)

    val isWriteSupported: Boolean
        get() =
            properties.containsAny(
                KmpBleGattProperty.Write,
                KmpBleGattProperty.WriteWithoutResponse,
                KmpBleGattProperty.SignedWrite,
            )

    val isNotifySupported: Boolean
        get() = properties.contains(KmpBleGattProperty.Notify)

    val isIndicateSupported: Boolean
        get() = properties.contains(KmpBleGattProperty.Indicate)

    suspend fun read(): Outcome<ByteArray, KmpBleError>

    suspend fun write(data: ByteArray, mode: KmpBleWriteMode = KmpBleWriteMode.WithResponse): Outcome<Unit, KmpBleError>

    suspend fun notifications(
        bufferSize: Int,
        bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    ): Flow<ByteArray>
}

enum class KmpBleWriteMode {
    WithoutResponse,
    WithResponse,
}

enum class KmpBleGattProperty(val value: Int) {
    Broadcast(1),
    Read(2),
    WriteWithoutResponse(4),
    Write(8),
    Notify(16),
    Indicate(32),
    SignedWrite(64),
    ExtendedProps(128),
}

enum class KmpBleGattPermission {
    Readable,
    Writable,
    ReadEncryptionRequired,
    WriteEncryptionRequired,
}

sealed class KmpBleError {
    object PlatformHandlerNotReady : KmpBleError()

    object PeripheralDisconnected : KmpBleError()

    object InvalidIdentifier : KmpBleError()

    object UnknownCharacteristic : KmpBleError()

    object CouldNotDiscoverCharacteristics : KmpBleError()

    /** Only occurs on iOS during a call to [IKmpBle.connect] */
    object PeerRemovedPairingInfo : KmpBleError()

    data class Unknown(val error: Any) : KmpBleError()

    object NotBonded : KmpBleError()
}

class KmpBlePeripheralDisconnect : CancellationException("The KmpBlePeripheral has disconnected")
