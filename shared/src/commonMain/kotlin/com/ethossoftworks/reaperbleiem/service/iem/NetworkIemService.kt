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
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFloat
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeShort
import kotlin.experimental.and
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val SupportedSchemaVersion: Byte = 0x00

class NetworkIemService(private val peripheralPreferencesService: PeripheralPreferencesService) : IIemService {

    private val tcpIp = "127.0.0.1"
    private val selectorManager = SelectorManager(KmpDispatchers.IO)
    private val tcpSocket = atomic<Socket?>(null)
    private val writeChannel = atomic<ByteWriteChannel?>(null)

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        try {
            // TODO: Use user settings
            peripheralPreferencesService.awaitSettings()

            val socket =
                aSocket(selectorManager)
                    .tcp()
                    .connect(tcpIp, peripheralPreferencesService.settings.value.reacueReaScriptPort)
            tcpSocket.update { socket }

            launch {
                val unknownBuffer = ByteArray(16384)
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
                            else -> reader.readAvailable(unknownBuffer) // Attempt to drain unknown message
                        }

                    if (event is IemEvent) {
                        println(event)
                        send(event)
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

    override suspend fun refresh() {
        writeChannel.value?.apply {
            writeByte(SupportedSchemaVersion)
            writeByte(TcpMessageType.Refresh.value)
            writeInt(0)
        }
    }

    override suspend fun setTrackVolume(trackId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.TrackVolChanged.value)
                writeInt(6)
                writeShort(trackId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setTrackPan(trackId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.TrackVolChanged.value)
                writeInt(6)
                writeShort(trackId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setTrackMute(trackId: Int, value: Boolean) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.TrackMuteChanged.value)
                writeInt(3)
                writeShort(trackId.toShort())
                writeByte(if (value) 1 else 0)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.ReceiveVolChanged.value)
                writeInt(8)
                writeShort(trackId.toShort())
                writeShort(receiveId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.ReceivePanChanged.value)
                writeInt(8)
                writeShort(trackId.toShort())
                writeShort(receiveId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, value: Boolean) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.ReceiveMuteChanged.value)
                writeInt(5)
                writeShort(trackId.toShort())
                writeShort(receiveId.toShort())
                writeByte(if (value) 1 else 0)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setOutputVolume(trackId: Int, hardwareOutId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.HwOutVolChanged.value)
                writeInt(8)
                writeShort(trackId.toShort())
                writeShort(hardwareOutId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setOutputPan(trackId: Int, hardwareOutId: Int, value: Float) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.HwOutPanChanged.value)
                writeInt(8)
                writeShort(trackId.toShort())
                writeShort(hardwareOutId.toShort())
                writeFloat(value)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    override suspend fun setOutputMute(trackId: Int, hardwareOutId: Int, value: Boolean) {
        try {
            writeChannel.value?.apply {
                writeByte(SupportedSchemaVersion)
                writeByte(TcpMessageType.HwOutMuteChanged.value)
                writeInt(5)
                writeShort(trackId.toShort())
                writeShort(hardwareOutId.toShort())
                writeByte(if (value) 1 else 0)
            }
        } catch (e: Exception) {
            Logger.e { "NetworkIemService - $e" }
        }
    }

    private fun closeSocket() {
        tcpSocket.value?.close()
        tcpSocket.update { null }
        writeChannel.update { null }
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
}
