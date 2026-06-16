package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import com.outsidesource.oskitkmp.io.IKmpIoSource
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
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFloat
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort
import kotlin.experimental.and
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeFloat

private val SupportedSchemaVersion: Byte = 0x00
private val HeartbeatInterval = 2.seconds

class NetworkIemService(private val peripheralPreferencesService: PeripheralPreferencesService) : IIemService {

    private val tcpIp = "127.0.0.1"
    private val selectorManager = SelectorManager(KmpDispatchers.IO)
    private val tcpSocket = atomic<Socket?>(null)

    // All TCP frames are written by a single dedicated coroutine — the only safe way to use ktor's ByteWriteChannel
    // (writing from multiple talker coroutines/threads, even mutex-serialized, can corrupt the stream). Senders
    // enqueue complete, pre-framed messages here; the channel preserves ordering and there is no cross-thread write.
    private val outgoing = atomic<Channel<ByteArray>?>(null)

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        try {
            peripheralPreferencesService.awaitSettings()

            val socket =
                aSocket(selectorManager)
                    .tcp()
                    .connect(tcpIp, peripheralPreferencesService.settings.value.reacueReaScriptPort)
            tcpSocket.update { socket }

            launch {
                val reader = socket.openReadChannel()
                while (isActive && socket.isActive) {
                    val schemaVersion = reader.readByte()
                    if (schemaVersion != SupportedSchemaVersion) {
                        Logger.e { "NetworkIemService - Received unsupported schema version $schemaVersion" }
                        close()
                        return@launch
                    }

                    val messageType = reader.readByte()
                    val payloadSize = reader.readInt()
                    val payload = reader.readByteArray(payloadSize).toKmpIoSource()

                    val event =
                        when (messageType) {
                            TcpMessageType.StructureChanged.value -> parseRefreshedMessage(payload)
                            TcpMessageType.TrackNameChanged.value -> parseTrackNameChangedMessage(payload, payloadSize)
                            TcpMessageType.TrackVolChanged.value -> parseTrackVolChangedMessage(payload)
                            TcpMessageType.TrackPanChanged.value -> parseTrackPanChangedMessage(payload)
                            TcpMessageType.TrackMuteChanged.value -> parseTrackMuteChangedMessage(payload)
                            TcpMessageType.ReceiveVolChanged.value -> parseReceiveVolChangedMessage(payload)
                            TcpMessageType.ReceivePanChanged.value -> parseReceivePanChangedMessage(payload)
                            TcpMessageType.ReceiveMuteChanged.value -> parseReceiveMuteChangedMessage(payload)
                            TcpMessageType.HwOutVolChanged.value -> parseHwOutVolChangedMessage(payload)
                            TcpMessageType.HwOutPanChanged.value -> parseHwOutPanChangedMessage(payload)
                            TcpMessageType.HwOutMuteChanged.value -> parseHwOutMuteChangedMessage(payload)
                            else -> {}
                        }

                    if (event is IemEvent) send(event)
                }
            }

            val outgoingFrames = Channel<ByteArray>(Channel.UNLIMITED)
            outgoing.update { outgoingFrames }
            val writeChannel = socket.openWriteChannel(autoFlush = true)

            // Single writer: drains pre-framed messages from the queue to the socket, in order, on one coroutine.
            launch {
                try {
                    for (frame in outgoingFrames) writeChannel.writeFully(frame)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Logger.e("NetworkIemService writer", t)
                }
            }

            launch {
                while (isActive) {
                    delay(HeartbeatInterval)
                    sendHeartbeat()
                }
            }
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

    private suspend inline fun parseRefreshedMessage(payload: IKmpIoSource): IemEvent.StructureChanged {
        val curve = payload.readFloat()
        val minDb = payload.readFloat()
        val maxDb = payload.readFloat()
        val trackCount = payload.readShort()
        val nameSize = payload.readShort()
        val projectName = payload.readUtf8(nameSize.toInt())

        val tracks = persistentMapOf<Int, Track>().builder()

        for (i in 0 until trackCount) {
            val receiveCount = payload.readShort()
            val hwOutCount = payload.readShort()
            val volume = payload.readFloat()
            val pan = payload.readFloat()
            val mute = payload.readByte()
            val trackNameLength = payload.readShort()
            val trackName = payload.readUtf8(trackNameLength.toInt())

            val receives = persistentMapOf<Int, Mix>().builder()
            val hardwareOuts = persistentMapOf<Int, Mix>().builder()

            for (j in 0 until receiveCount) {
                receives[j] =
                    Mix(
                        id = j,
                        trackId = payload.readShort().toInt(),
                        volume = payload.readFloat(),
                        pan = payload.readFloat(),
                        isMuted = payload.readByte() and 0xFF.toByte() > 0x00,
                    )
            }

            for (j in 0 until hwOutCount) {
                hardwareOuts[j] =
                    Mix(
                        id = j,
                        trackId = payload.readShort().toInt(),
                        volume = payload.readFloat(),
                        pan = payload.readFloat(),
                        isMuted = payload.readByte() and 0xFF.toByte() > 0x00,
                    )
            }

            tracks[i] =
                Track(
                    id = i,
                    name = trackName,
                    volume = volume,
                    pan = pan,
                    isMuted = mute and 0xFF.toByte() > 0x00,
                    receives = receives.build(),
                    hardwareOuts = hardwareOuts.build(),
                )
        }

        return IemEvent.StructureChanged(
            projectName = projectName,
            faderInfo =
                FaderInfo(
                    curve = curve,
                    minDb = minDb,
                    maxDb = maxDb,
                ),
            tracks = tracks.build(),
        )
    }

    private suspend fun parseTrackNameChangedMessage(payload: IKmpIoSource, payloadSize: Int): IemEvent {
        val trackId = payload.readShort().toInt()
        val value = payload.readUtf8(payloadSize - 2)
        return IemEvent.TrackNameUpdated(trackId = trackId, name = value)
    }

    private suspend fun parseTrackVolChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.TrackVolumeUpdated(trackId = trackId, value = value)
    }

    private suspend fun parseTrackPanChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.TrackPanUpdated(trackId = trackId, value = value)
    }

    private suspend fun parseTrackMuteChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val value = payload.readByte() > 0x00
        return IemEvent.TrackMuteUpdated(trackId = trackId, value = value)
    }

    private suspend fun parseReceiveVolChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val receiveId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.ReceiveVolumeUpdated(trackId = trackId, receiveId = receiveId, value = value)
    }

    private suspend fun parseReceivePanChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val receiveId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.ReceivePanUpdated(trackId = trackId, receiveId = receiveId, value = value)
    }

    private suspend fun parseReceiveMuteChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val receiveId = payload.readShort().toInt()
        val value = payload.readByte() > 0x00
        return IemEvent.ReceiveMuteUpdated(trackId = trackId, receiveId = receiveId, value = value)
    }

    private suspend fun parseHwOutVolChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val hwOutId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.HardwareOutputVolumeUpdated(trackId = trackId, hardwareOutId = hwOutId, value = value)
    }

    private suspend fun parseHwOutPanChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val hwOutId = payload.readShort().toInt()
        val value = payload.readFloat()
        return IemEvent.HardwareOutputPanUpdated(trackId = trackId, hardwareOutId = hwOutId, value = value)
    }

    private suspend fun parseHwOutMuteChangedMessage(payload: IKmpIoSource): IemEvent {
        val trackId = payload.readShort().toInt()
        val hwOutId = payload.readShort().toInt()
        val value = payload.readByte() > 0x00
        return IemEvent.HardwareOutputMuteUpdated(trackId = trackId, hardwareOutId = hwOutId, value = value)
    }

    suspend fun sendTalkbackStart(talkerId: Int) =
        sendFrame(TcpMessageType.TalkbackStart) {
            writeByte(talkerId.toByte())
        }

    /**
     * [pcm] is raw little-endian signed 16-bit mono PCM, exactly as captured/forwarded. The sample count and the
     * header use the protocol's big-endian framing; the PCM bytes are appended verbatim and the ReaScript reads them
     * as little-endian shorts.
     */
    suspend fun sendTalkbackAudio(talkerId: Int, pcm: ByteArray) =
        sendFrame(TcpMessageType.TalkbackAudio) {
            writeByte(talkerId.toByte())
            writeShort((pcm.size / 2).toShort())
            write(pcm)
        }

    suspend fun sendTalkbackStop(talkerId: Int) =
        sendFrame(TcpMessageType.TalkbackStop) {
            writeByte(talkerId.toByte())
        }

    private suspend fun sendHeartbeat() = sendFrame(TcpMessageType.Heartbeat) {}

    override suspend fun refresh() = sendFrame(TcpMessageType.Refresh) {}

    override suspend fun setTrackVolume(trackId: Int, value: Float) =
        sendFrame(TcpMessageType.TrackVolChanged) {
            writeShort(trackId.toShort())
            writeFloat(value)
        }

    override suspend fun setTrackPan(trackId: Int, value: Float) =
        sendFrame(TcpMessageType.TrackPanChanged) {
            writeShort(trackId.toShort())
            writeFloat(value)
        }

    override suspend fun setTrackMute(trackId: Int, value: Boolean) =
        sendFrame(TcpMessageType.TrackMuteChanged) {
            writeShort(trackId.toShort())
            writeByte(if (value) 1 else 0)
        }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) =
        sendFrame(TcpMessageType.ReceiveVolChanged) {
            writeShort(trackId.toShort())
            writeShort(receiveId.toShort())
            writeFloat(value)
        }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) =
        sendFrame(TcpMessageType.ReceivePanChanged) {
            writeShort(trackId.toShort())
            writeShort(receiveId.toShort())
            writeFloat(value)
        }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, value: Boolean) =
        sendFrame(TcpMessageType.ReceiveMuteChanged) {
            writeShort(trackId.toShort())
            writeShort(receiveId.toShort())
            writeByte(if (value) 1 else 0)
        }

    override suspend fun setOutputVolume(trackId: Int, hardwareOutId: Int, value: Float) =
        sendFrame(TcpMessageType.HwOutVolChanged) {
            writeShort(trackId.toShort())
            writeShort(hardwareOutId.toShort())
            writeFloat(value)
        }

    override suspend fun setOutputPan(trackId: Int, hardwareOutId: Int, value: Float) =
        sendFrame(TcpMessageType.HwOutPanChanged) {
            writeShort(trackId.toShort())
            writeShort(hardwareOutId.toShort())
            writeFloat(value)
        }

    override suspend fun setOutputMute(trackId: Int, hardwareOutId: Int, value: Boolean) =
        sendFrame(TcpMessageType.HwOutMuteChanged) {
            writeShort(trackId.toShort())
            writeShort(hardwareOutId.toShort())
            writeByte(if (value) 1 else 0)
        }

    private fun sendFrame(
        type: TcpMessageType,
        block: Buffer.() -> Unit,
    ) {
        val out = outgoing.value ?: return
        val payload = Buffer().apply(block).readByteArray()
        val frame =
            Buffer()
                .apply {
                    writeByte(SupportedSchemaVersion)
                    writeByte(type.value)
                    writeInt(payload.size)
                    write(payload)
                }
                .readByteArray()
        out.trySend(frame)
    }

    private fun closeSocket() {
        outgoing.value?.close()
        outgoing.update { null }
        tcpSocket.value?.close()
        tcpSocket.update { null }
    }
}

private enum class TcpMessageType(val value: Byte) {
    StructureChanged(0x00),
    TrackNameChanged(0x01),
    TrackVolChanged(0x02),
    TrackPanChanged(0x03),
    TrackMuteChanged(0x04),
    ReceiveVolChanged(0x05),
    ReceivePanChanged(0x06),
    ReceiveMuteChanged(0x07),
    HwOutVolChanged(0x08),
    HwOutPanChanged(0x09),
    HwOutMuteChanged(0x0A),
    Refresh(0x0B),
    Heartbeat(0x0C),
    TalkbackStart(0x0D),
    TalkbackAudio(0x0E),
    TalkbackStop(0x0F),
}
