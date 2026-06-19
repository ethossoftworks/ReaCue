package com.ethossoftworks.reaperbleiem.lib.bluetooth

import android.bluetooth.BluetoothSocket
import com.outsidesource.oskitkmp.outcome.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidKmpBleL2CapChannel(private val socket: BluetoothSocket) : IKmpBleL2CapChannel {

    private val output = socket.outputStream
    private val writeMutex = Mutex()

    override val incoming: Flow<ByteArray> =
        flow {
                val input = socket.inputStream
                val buffer = ByteArray(4096)
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) emit(buffer.copyOf(read))
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun write(data: ByteArray): Outcome<Unit, KmpBleError> =
        withContext(Dispatchers.IO) {
            try {
                writeMutex.withLock {
                    output.write(data)
                    output.flush()
                }
                Outcome.Ok(Unit)
            } catch (t: Throwable) {
                Outcome.Error(KmpBleError.Unknown(t))
            }
        }

    override fun close() {
        try {
            socket.close()
        } catch (_: Throwable) {
            // ignore
        }
    }
}
