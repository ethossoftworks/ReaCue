package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.outsidesource.oskitkmp.concurrency.KmpDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.discard
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.indexOf
import kotlinx.io.readFloat
import kotlinx.io.readString
import kotlinx.io.writeFloat
import kotlinx.io.writeString

class NetworkIemService(
    val restDomain: String = "http://localhost:8000",
    val oscIp: String = "127.0.0.1",
    val oscNotificationPort: Int = 9000,
    val oscCommandPort: Int = 8000,
) : IIemService {

    private val httpClient = HttpClient(CIO)
    private val selectorManager = SelectorManager(KmpDispatchers.IO)
    private val oscSocket = atomic<BoundDatagramSocket?>(null)
    private val paddingBuffer = byteArrayOf(0x00, 0x00, 0x00)
    private val events = MutableSharedFlow<IemEvent>()
    private val trackCache = atomic<Map<Int, Track>>(emptyMap())

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        try {
            val socketListeningStarted = CompletableDeferred<Unit>()
            val eventsListeningStarted = CompletableDeferred<Unit>()

            oscSocket.update { aSocket(selectorManager).udp().bind(oscIp, oscNotificationPort) }

            launch {
                oscSocket.value
                    ?.incoming
                    ?.consumeAsFlow()
                    ?.onStart { socketListeningStarted.complete(Unit) }
                    ?.collect { packet -> oscParsePacket(packet).toIemEvents().forEach { send(it) } }
            }

            launch { events.onStart { eventsListeningStarted.complete(Unit) }.collect { send(it) } }

            launch {
                try {
                    startWebPolling()
                } catch (t: Throwable) {
                    send(IemEvent.Error(t))
                    this@callbackFlow.cancel()
                }
            }

            socketListeningStarted.await()
            eventsListeningStarted.await()
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.e("NetworkIemService", t)
            send(IemEvent.Error(t))
            closeSocket()
            close()
        }

        awaitClose { closeSocket() }
    }

    override suspend fun refresh() {
        events.emit(IemEvent.Refreshing)
        val tracks = getTracks()
        trackCache.update { tracks }
        val projectName = getProjectName()
        val faderInfo = getFaderInfo()

        events.emit(IemEvent.Refreshed(projectName = projectName, tracks = tracks, faderInfo = faderInfo))
        oscSetTrackNotificationCount(tracks.size - 1) // -1 because master is reported regardless
        oscSetReceiveNotificationCount(tracks.values.maxOf { if (it.isIem) it.receives.size else 0 })
        oscReset()
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        oscSendCommand("/track/$trackId/send/1/volume", value)
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        oscSendCommand("/track/$trackId/recv/$receiveId/volume", value)
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        oscSendCommand("/track/$trackId/recv/$receiveId/pan", value)
    }

    private fun closeSocket() {
        oscSocket.value?.close()
        oscSocket.update { null }
    }

    private suspend fun getTracks(): PersistentMap<Int, Track> {
        val tracksResponse = httpClient.get("$restDomain/_/TRACK")
        if (!tracksResponse.status.isSuccess()) return persistentMapOf()
        val tracks = persistentMapOf<Int, Track>().builder()

        tracksResponse.bodyAsText().split("\n").forEach { track ->
            if (!track.startsWith("TRACK")) return@forEach
            val tokens = track.split("\t")

            val trackId = tokens[1].toInt()
            val receiveCount = tokens[11].toInt()
            val hardwareOutCount = tokens[12].toInt()

            val request = buildString {
                for (i in 0 until hardwareOutCount) {
                    append("GET/TRACK/$trackId/SEND/$i;")
                }
                for (i in 1..receiveCount) {
                    append("GET/TRACK/$trackId/SEND/-$i;")
                }
            }

            val mixesResponse = httpClient.get("$restDomain/_/$request")
            if (!mixesResponse.status.isSuccess()) return@forEach

            val hardwareOuts = persistentMapOf<Int, Mix>().builder()
            val receives = persistentMapOf<Int, Mix>().builder()

            mixesResponse.bodyAsText().split("\n").mapNotNull { mix ->
                if (!mix.startsWith("SEND")) return@mapNotNull null
                val tokens = mix.split("\t")

                val id = tokens[2].toInt().absoluteValue
                val volume = normalizeWebVolume(tokens[4].toDouble())
                val pan = normalizeWebPan(tokens[5].toDouble())
                val trackId = tokens[6].toInt()

                val mix = Mix(id = id, volume = volume, pan = pan, trackId = trackId)
                if (trackId == -1) hardwareOuts[id] = mix else receives[mix.id] = mix
            }

            tracks[trackId] =
                Track(id = trackId, name = tokens[2], receives = receives.build(), hardwareOuts = hardwareOuts.build())
        }

        return tracks.build()
    }

    private suspend fun startWebPolling() {
        var projectName: String? = null

        while (currentCoroutineContext().isActive) {
            delay(500.milliseconds)
            val newName = getProjectName()
            if (newName != projectName && projectName != null) {
                projectName = newName
                refresh()
            } else {
                projectName = newName
            }
        }
    }

    private suspend fun getProjectName(): String {
        return try {
            val response = httpClient.get("$restDomain/_/GET/EXTSTATE/ReaCue/ProjectName")
            if (!response.status.isSuccess()) return "Unknown"
            response.bodyAsText().split("\t")[3].trim()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            "Unknown"
        }
    }

    private suspend fun getFaderInfo(): FaderInfo {
        return try {
            val response = httpClient.get("$restDomain/_/GET/EXTSTATE/ReaCue/FaderInfo")
            if (!response.status.isSuccess()) return FaderInfo()
            val rawFaderInfo = response.bodyAsText().split("\t")[3].split(";")

            FaderInfo(
                curve = rawFaderInfo[0].toFloat(),
                minDb = rawFaderInfo[1].toFloat(),
                maxDb = rawFaderInfo[2].toFloat(),
            )
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            FaderInfo()
        }
    }

    private fun List<OscMessage>.toIemEvents(): List<IemEvent> = mapNotNull { message ->
        val parts = message.address.trimStart('/').split("/")
        val trackId = parts[1].toInt()

        when (parts[2]) {
            "name" -> IemEvent.TrackNameUpdated(trackId = trackId, name = message.arguments[0] as String)
            "send" -> {
                if (trackCache.value[trackId]?.isIem != true) return@mapNotNull null
                IemEvent.OutputVolumeUpdated(trackId = trackId, message.arguments[0] as Float)
            }

            "recv" -> {
                if (trackCache.value[trackId]?.isIem != true) return@mapNotNull null
                val receiveId = parts[3].toInt()
                when (parts[4]) {
                    "pan" ->
                        IemEvent.ReceivePanUpdated(
                            trackId = trackId,
                            receiveId = receiveId,
                            value = message.arguments[0] as Float,
                        )

                    "volume" ->
                        IemEvent.ReceiveVolumeUpdated(
                            trackId = trackId,
                            receiveId = receiveId,
                            value = message.arguments[0] as Float,
                        )

                    else -> return@mapNotNull null
                }
            }

            else -> null
        }
    }

    /**
     * https://opensoundcontrol.stanford.edu/spec-1_0.html OSC Message Format - Address pattern, type tag, zero or more
     * arguments OSC Bundle Format - #bundle, OSC Time Tag (8 bytes), size of n1 element (4 bytes), contents of n1
     * element...
     */
    private fun oscParsePacket(data: Datagram): List<OscMessage> {
        val typeByte = data.packet.peek().readByte()
        return when (typeByte) {
            OscBundleByte -> oscParseBundle(data.packet)
            else -> listOf(oscParseMessage(data.packet))
        }
    }

    private fun oscParseBundle(source: Source): List<OscMessage> {
        source.skip(8) // skip "#bundle"
        source.skip(8) // skip time tag

        return buildList {
            while (!source.exhausted()) {
                val size = source.readInt()
                val typeByte = source.peek().readByte()
                when (typeByte) {
                    OscBundleByte -> addAll(oscParseBundle(source))
                    else -> add(oscParseMessage(source, size))
                }
            }
        }
    }

    private fun oscParseMessage(source: Source, size: Int = Int.MAX_VALUE): OscMessage {
        var read = 0

        val (address, addressBytesRead) = source.readOscString()
        read += addressBytesRead

        val (types, typeBytesRead) = source.readOscString()
        read += typeBytesRead

        val arguments = buildList {
            for (i in 1 until types.length) {
                if (read >= size || source.exhausted()) break

                val tag = types[i]
                when (tag) {
                    'i' -> {
                        add(source.readInt())
                        read += 4
                    }

                    'n',
                    'f' -> {
                        add(source.readFloat())
                        read += 4
                    }

                    's' -> {
                        val (str, strBytes) = source.readOscString()
                        add(str)
                        read += strBytes
                    }
                }
            }
        }

        if (size != Int.MAX_VALUE && read < size) {
            source.discard((size - read).toLong())
        }

        return OscMessage(address = address, arguments = arguments)
    }

    private suspend fun oscSetTrackNotificationCount(trackCount: Int) =
        oscSendCommand("/device/track/count", trackCount)

    private suspend fun oscSetReceiveNotificationCount(trackCount: Int) =
        oscSendCommand("/device/receive/count", trackCount)

    private suspend fun oscReset() = oscSendCommand<Unit>("/action/41743")

    private suspend inline fun <reified T> oscSendCommand(path: String, argument: T? = null) {
        val socket = oscSocket.value ?: return
        val datagram =
            Datagram(
                packet =
                    buildPacket {
                        writeOscString(path)

                        when (argument) {
                            is Float -> {
                                writeOscString(",f")
                                writeFloat(argument)
                            }

                            is Int -> {
                                writeOscString(",i")
                                writeInt(argument)
                            }
                        }
                    },
                address = InetSocketAddress(oscIp, oscCommandPort),
            )
        socket.send(datagram)
    }

    private fun Sink.writeOscString(value: String) {
        writeString(value + "\u0000")
        writePadding(value.length + 1) // +1 for null termination
    }

    private fun Sink.writePadding(tokenLength: Int) {
        if (tokenLength % 4 == 0) return
        write(paddingBuffer, 0, 4 - (tokenLength % 4))
    }

    private fun Source.readOscString(): Pair<String, Int> {
        val nullIndex = indexOf(0x00.toByte())
        val value = readString(nullIndex)
        val paddingRead = readPadding(nullIndex.toInt())
        return value to ((nullIndex + paddingRead).toInt())
    }

    private fun Source.readPadding(tokenLength: Int): Int {
        val totalBytes = (tokenLength + 4) and 3.inv()
        discard((totalBytes - tokenLength).toLong())
        return totalBytes - tokenLength
    }

    private fun normalizeWebVolume(value: Double): Float {
        if (value <= 0.0) return 0.0.toFloat()
        val maxGainLinear = 10.0.pow(12.0 / 20.0)
        val oscValue = (value / maxGainLinear).pow(0.25)
        return oscValue.coerceIn(0.0, 1.0).toFloat()
    }

    private fun normalizeWebPan(value: Double): Float {
        return ((value + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
    }
}

private data class OscMessage(val address: String, val arguments: List<Any>)

private val OscBundleByte = 0x23.toByte()
