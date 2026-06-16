@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import com.outsidesource.oskitkmp.lib.containsAny
import com.outsidesource.oskitkmp.outcome.Outcome
import io.ktor.utils.io.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

data class KmpBleScanRecord(
    val name: String,
    val identifier: KmpBlePeripheralId,
    val rssi: Int,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<Uuid, ByteArray>,
    val services: List<Uuid>,
)

typealias KmpBlePeripheralId = String

interface IKmpBleCentralManager {
    fun scan(): Flow<KmpBleScanRecord>

    suspend fun connect(
        identifier: KmpBlePeripheralId,
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
    val services: Map<Uuid, IKmpBleService>
    val characteristics: Map<Uuid, IKmpBleCharacteristic>
    val scope: CoroutineScope

    fun mtu(mode: KmpBleWriteMode = KmpBleWriteMode.WithResponse): Int

    /**
     * [requestMtu] Requests a new MTU
     *
     * @param [size] The size in bytes of the MTU to request. Valid values are 23-517
     */
    suspend fun requestMtu(size: Int): Outcome<Int, KmpBleError>

    /**
     * Requests the connection priority or latency. On Apple platforms this is a no-op as Apple does not expose this API
     * to centrals.
     */
    suspend fun requestConnectionPriority(priority: KmpBleConnectionPriority)

    suspend fun disconnect(): Outcome<Unit, KmpBleError>

    suspend fun discoverServices(): Outcome<Map<Uuid, IKmpBleService>, KmpBleError>

    /**
     * Await bond attempts to read from an encrypted read characteristic and watches for disconnection. This will cause
     * platforms to show pairing prompts
     *
     * Note: This call should be done before any reads/writes on any characteristic.
     */
    suspend fun awaitBond(encryptedReadCharacteristic: Uuid): Outcome<Unit, KmpBleError>

    suspend fun read(characteristic: Uuid): Outcome<ByteArray, KmpBleError>

    suspend fun write(
        characteristic: Uuid,
        data: ByteArray,
        mode: KmpBleWriteMode = KmpBleWriteMode.WithResponse,
    ): Outcome<Unit, KmpBleError>

    suspend fun notifications(
        characteristic: Uuid,
        bufferSize: Int = 0,
        bufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
    ): Flow<ByteArray>

    /**
     * Opens an L2CAP Connection-Oriented Channel to the connected peripheral on the given [psm]. L2CAP CoC is a
     * reliable, ordered byte stream (like TCP) with far higher throughput and lower overhead than GATT notifications —
     * used here for streaming talkback microphone audio. The [psm] is discovered out-of-band (ReaCue publishes it in
     * the handshake characteristic payload).
     */
    suspend fun openL2CapChannel(psm: Int): Outcome<IKmpBleL2CapChannel, KmpBleError>
}

/**
 * A bidirectional L2CAP CoC byte stream. Reliable and ordered. [incoming] emits chunks as they arrive (chunk
 * boundaries are not message boundaries — the consumer must frame). [write] suspends until the bytes are queued.
 */
interface IKmpBleL2CapChannel {
    val incoming: Flow<ByteArray>

    suspend fun write(data: ByteArray): Outcome<Unit, KmpBleError>

    fun close()
}

interface IKmpBleService {
    val uuid: Uuid
    val characteristics: Map<Uuid, IKmpBleCharacteristic>
}

interface IKmpBleCharacteristic {
    val uuid: Uuid
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

internal class KmpBlePeripheralDisconnect : CancellationException("The peripheral has disconnected")

internal suspend fun IKmpBlePeripheral.awaitBond(
    encryptedReadCharacteristic: Uuid,
    scope: CoroutineScope,
): Outcome<Unit, KmpBleError> =
    withScope(scope) {
        val deferred = CompletableDeferred<Outcome<Unit, KmpBleError>>()

        coroutineScope {
            async {
                connectionStatus
                    .filter { it == KmpBleConnectionStatus.Disconnected }
                    .collect {
                        deferred.complete(Outcome.Error(KmpBleError.NotBonded))
                        this@coroutineScope.coroutineContext.cancelChildren()
                    }
            }
            async {
                when (read(encryptedReadCharacteristic)) {
                    is Outcome.Ok -> deferred.complete(Outcome.Ok(Unit))
                    is Outcome.Error -> deferred.complete(Outcome.Error(KmpBleError.NotBonded))
                }
                this@coroutineScope.coroutineContext.cancelChildren()
            }
        }

        deferred.await()
    }

internal suspend inline fun <T> withScope(
    scope: CoroutineScope,
    crossinline block: suspend () -> Outcome<T, KmpBleError>,
): Outcome<T, KmpBleError> {
    val task = scope.async(start = CoroutineStart.UNDISPATCHED) { block() }

    return try {
        task.await()
    } catch (e: KmpBlePeripheralDisconnect) {
        Outcome.Error(KmpBleError.PeripheralDisconnected)
    } catch (e: CancellationException) {
        task.cancel()
        throw e
    } catch (t: Throwable) {
        Outcome.Error(KmpBleError.Unknown(t))
    } finally {
        task.cancel()
    }
}
