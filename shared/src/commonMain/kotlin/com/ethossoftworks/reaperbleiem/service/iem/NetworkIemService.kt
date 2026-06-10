package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByteArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkIemService(private val peripheralPreferencesService: PeripheralPreferencesService) : IIemService {

    private val tcpIp = "127.0.0.1"
    private var tcpPort = 9001

    private val selectorManager = SelectorManager(KmpDispatchers.IO)
    private val tcpSocket = atomic<Socket?>(null)
    private val writeChannel = atomic<ByteWriteChannel?>(null)

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        try {
            // TODO: Use user settings
            peripheralPreferencesService.awaitSettings()

            val socket = aSocket(selectorManager).tcp().connect(tcpIp, tcpPort)
            tcpSocket.update { socket }

            launch {
                val reader = socket.openReadChannel()
                val buffer = ByteArray(256)
                while (isActive && socket.isActive) {
                    val read = reader.readAvailable(buffer)
                    if (read > 0) {
                        println(buffer.copyOfRange(0, read).decodeToString())
                    }
                }
            }

            writeChannel.update { socket.openWriteChannel(autoFlush = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.e("NetworkIemService", t)
            send(IemEvent.Error.Unknown(t))
            closeSocket()
            close()
        }

        awaitClose { closeSocket() }
    }

    override suspend fun refresh() {
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {}

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {}

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {}

    private fun closeSocket() {
        tcpSocket.value?.close()
        tcpSocket.update { null }
        writeChannel.update { null }
    }
}
