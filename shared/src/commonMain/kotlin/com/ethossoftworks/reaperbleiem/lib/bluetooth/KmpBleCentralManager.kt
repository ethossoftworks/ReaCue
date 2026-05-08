package com.ethossoftworks.reaperbleiem.lib.bluetooth

import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import com.outsidesource.oskitkmp.lib.containsAny
import com.outsidesource.oskitkmp.outcome.Outcome
import io.ktor.utils.io.CancellationException
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
import kotlinx.coroutines.withContext

data class KmpBleScanRecord(
    val name: String,
    val identifier: KmpBlePeripheralId,
    val rssi: Int,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<String, ByteArray>,
    val serviceUuids: List<String>,
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

internal class KmpBlePeripheralDisconnect : CancellationException("The peripheral has disconnected")

internal suspend fun IKmpBlePeripheral.awaitBond(
    encryptedReadCharacteristicUuid: String,
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
                when (read(encryptedReadCharacteristicUuid)) {
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
    val task = scope.async(start = CoroutineStart.UNDISPATCHED) {
        block()
    }

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
