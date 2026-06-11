package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import com.outsidesource.oskitkmp.io.toKmpIoSource
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readFloat
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readShort
import kotlin.experimental.and
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val SupportedSchemaVersion = 0

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
                val buffer = ByteArray(65536)
                while (isActive && socket.isActive) {
                    val schemaVersion = reader.readByte()
                    if (schemaVersion > SupportedSchemaVersion) {
                        Logger.e { "NetworkIemService - Received unsupported schema version $schemaVersion" }
                        close()
                        return@launch
                    }

                    val messageType = reader.readByte()
                    val payloadLength = reader.readInt()
                    val message = reader.readByteArray(payloadLength).toKmpIoSource()

                    val curve = message.readFloat()
                    val minDb = message.readFloat()
                    val maxDb = message.readFloat()
                    val trackCount = message.readShort()
                    val nameSize = message.readShort()
                    val projectName = message.readUtf8(nameSize.toInt())

                    val tracks = persistentMapOf<Int, Track>().builder()
                    val receives = persistentMapOf<Int, Mix>().builder()
                    val hardwareOuts = persistentMapOf<Int, Mix>().builder()

                    for (i in 0 until trackCount) {
                        val receiveCount = message.readShort()
                        val hwOutCount = message.readShort()
                        val volume = message.readFloat()
                        val pan = message.readFloat()
                        val mute = message.readByte()
                        val trackNameLength = message.readShort()
                        val trackName = message.readUtf8(trackNameLength.toInt())

                        for (j in 0 until receiveCount) {
                            val sourceTrack = message.readShort()
                            val volume = message.readFloat()
                            val pan = message.readFloat()
                            val mute = message.readByte()

                            receives[j] =
                                Mix(
                                    id = j,
                                    trackId = sourceTrack.toInt(),
                                    volume = volume,
                                    pan = pan,
                                    isMuted = mute and 0xFF.toByte() > 0x00,
                                )
                        }

                        for (j in 0 until hwOutCount) {
                            val sourceTrack = message.readShort()
                            val volume = message.readFloat()
                            val pan = message.readFloat()
                            val mute = message.readByte()

                            hardwareOuts[j] =
                                Mix(
                                    id = j,
                                    trackId = sourceTrack.toInt(),
                                    volume = volume,
                                    pan = pan,
                                    isMuted = mute and 0xFF.toByte() > 0x00,
                                )
                        }

                        tracks[i] =
                            Track(
                                id = i,
                                name = trackName,
                                receives = receives.build(),
                                hardwareOuts = hardwareOuts.build(),
                            )
                    }

                    val event =
                        IemEvent.Refreshed(
                            projectName = projectName,
                            faderInfo =
                                FaderInfo(
                                    curve = curve,
                                    minDb = minDb,
                                    maxDb = maxDb,
                                ),
                            tracks = tracks.build(),
                        )

                    println(event)
                    send(event)
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

    override suspend fun refresh() {}

    override suspend fun setOutputVolume(trackId: Int, value: Float) {}

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {}

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {}

    private fun closeSocket() {
        tcpSocket.value?.close()
        tcpSocket.update { null }
        writeChannel.update { null }
    }
}
