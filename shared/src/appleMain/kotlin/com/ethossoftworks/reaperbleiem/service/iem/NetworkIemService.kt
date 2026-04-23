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
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.discard
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.indexOf
import kotlinx.io.readFloat
import kotlinx.io.readString
import kotlinx.io.writeFloat
import kotlinx.io.writeString
import kotlin.time.Duration.Companion.milliseconds

class NetworkIemService(
    val restDomain: String = "http://localhost:8000",
    val oscIp: String = "127.0.0.1",
    val oscNotificationPort: Int = 9000,
    val oscCommandPort: Int = 8000,
) {

    private val scope = CoroutineScope(KmpDispatchers.IO + SupervisorJob())
    private val httpClient = HttpClient(CIO)
    private val selectorManager = SelectorManager(KmpDispatchers.IO)
    private val oscSocket = atomic<BoundDatagramSocket?>(null)
    private val paddingBuffer = byteArrayOf(0x00, 0x00, 0x00)

    suspend fun start() {
        val tracks = getIemTracks()
        if (tracks.isEmpty()) {
            // TODO: Handle this
        }

        try {
            oscSocket.update { aSocket(selectorManager).udp().bind(oscIp, oscNotificationPort) }

            scope.launch {
                oscSocket.value?.incoming?.consumeAsFlow()?.collect {
                    // TODO: Handle this better
                    println(oscParsePacket(it))
                }
            }

            delay(16.milliseconds)
            oscSetTrackCount(tracks.maxOf { it.index })
            oscReset()
        } catch (t: Throwable) {
            Logger.e("NetworkIemService", t)
        }
    }

    suspend fun stop() {
        oscSocket.value?.close()
        oscSocket.update { null }
    }

    private suspend fun getIemTracks(): List<TrackDto> {
        val response = httpClient.get("$restDomain/_/TRACK")
        if (!response.status.isSuccess()) return emptyList()

        return response.bodyAsText().split("\n").mapNotNull { track ->
            if (!track.startsWith("TRACK")) return@mapNotNull null
            val tokens = track.split("\t")

            val trackDto = TrackDto(
                index = tokens[1].toInt(),
                name = tokens[2],
                sendCount = tokens[10].toInt(),
                receiveCount = tokens[11].toInt(),
                hardwareOutCount = tokens[12].toInt(),
            )

            if (trackDto.hardwareOutCount == 0 || trackDto.receiveCount == 0) return@mapNotNull null

            trackDto
        }
    }

    /**
     * https://opensoundcontrol.stanford.edu/spec-1_0.html
     * OSC Message Format - Address pattern, type tag, zero or more arguments
     * OSC Bundle Format - #bundle, OSC Time Tag (8 bytes), size of n1 element (4 bytes), contents of n1 element...
     */
    private suspend fun oscParsePacket(data: Datagram): List<OscMessageDto> {
        val typeByte = data.packet.peek().readByte()
        return when (typeByte) {
            OscBundleByte -> oscParseBundle(data.packet)
            else -> listOf(oscParseMessage(data.packet))
        }
    }

    private suspend fun oscParseBundle(source: Source): List<OscMessageDto> {
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

    private suspend fun oscParseMessage(source: Source, size: Int = Int.MAX_VALUE): OscMessageDto {
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

                    'n', 'f' -> {
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

        return OscMessageDto(address = address, arguments = arguments)
    }

    private suspend fun oscSetTrackCount(trackCount: Int) = oscSendCommand("/device/track/count", trackCount)
    private suspend fun oscReset() = oscSendCommand<Unit>("/action/41743")

    private suspend inline fun <reified T> oscSendCommand(path: String, argument: T? = null) {
        val socket = oscSocket.value ?: return
        val datagram = Datagram(
            packet = buildPacket {
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
}

private data class TrackDto(
    val name: String,
    val index: Int,
    val sendCount: Int,
    val receiveCount: Int,
    val hardwareOutCount: Int,
)

private data class OscMessageDto(
    val address: String,
    val arguments: List<Any>,
)

private val OscBundleByte = 0x23.toByte()