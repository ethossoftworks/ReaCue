@file:OptIn(ExperimentalSerializationApi::class)
@file:UseSerializers(PersistentMapSerializer::class)

package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.PersistentMapSerializer
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleScanRecord
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

interface IIemService {
    fun subscribe(context: IemContext): Flow<IemEvent>

    suspend fun refresh()

    suspend fun setOutputVolume(trackId: Int, value: Float)

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float)

    suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float)
}

sealed class IemContext {
    data object Peripheral : IemContext()

    data class Central(val peripheral: KmpBleScanRecord) : IemContext()
}

@Serializable
@SerialName("IemEvent")
sealed class IemEvent {
    @Serializable @SerialName("0") data object Refresh : IemEvent()

    @Serializable @SerialName("1") data object Refreshing : IemEvent()

    @Serializable
    @SerialName("2")
    data class Refreshed(
        @CborLabel(1) val projectName: String,
        @CborLabel(2) val tracks: PersistentMap<Int, Track>,
        @CborLabel(3) val faderInfo: FaderInfo,
    ) : IemEvent()

    @Serializable
    @SerialName("3")
    data class TrackNameUpdated(@CborLabel(1) val trackId: Int, @CborLabel(2) val name: String) : IemEvent()

    @Serializable
    @SerialName("4")
    data class ReceivePanUpdated(
        @CborLabel(1) val trackId: Int,
        @CborLabel(2) val receiveId: Int,
        @CborLabel(3) val value: Float,
    ) : IemEvent()

    @Serializable
    @SerialName("5")
    data class ReceiveVolumeUpdated(
        @CborLabel(1) val trackId: Int,
        @CborLabel(2) val receiveId: Int,
        @CborLabel(3) val value: Float,
    ) : IemEvent()

    @Serializable
    @SerialName("6")
    data class OutputVolumeUpdated(@CborLabel(1) val trackId: Int, @CborLabel(2) val value: Float) : IemEvent()

    @SerialName("7") @Serializable data object Reset : IemEvent()

    @SerialName("8")
    @Serializable
    data class PasscodeRequired(@CborLabel(1) val passcode: CompletableDeferred<String>) : IemEvent()

    @Serializable
    @SerialName("9")
    sealed class Error : IemEvent() {
        @SerialName("10")
        @Serializable(with = IemUnknownErrorEventSerializer::class)
        data class Unknown(@CborLabel(1) val error: Any) : Error()

        @SerialName("11") @Serializable data object BleProtocolMismatch : Error()

        @SerialName("12") @Serializable data object DisconnectedPeripheral : Error()
    }
}

@Serializable
data class Track(
    @CborLabel(1) val id: Int,
    @CborLabel(2) val name: String,
    @CborLabel(4) val receives: PersistentMap<Int, Mix>,
    @CborLabel(5) val hardwareOuts: PersistentMap<Int, Mix>,
) {
    val isIem = hardwareOuts.isNotEmpty() && receives.isNotEmpty()
}

object IemUnknownErrorEventSerializer : KSerializer<IemEvent.Error.Unknown> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IemError.Unknown", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): IemEvent.Error.Unknown {
        return IemEvent.Error.Unknown(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: IemEvent.Error.Unknown) {
        encoder.encodeString(value.error.toString())
    }
}

@Serializable
data class FaderInfo(
    @CborLabel(1) val curve: Float = -1f,
    @CborLabel(2) val minDb: Float = -72f,
    @CborLabel(3) val maxDb: Float = 12f,
) {

    val sliderStep by lazy {
        val normalizedZero = dbToNormalized(0f)
        val detentsBelowZero = 100f
        normalizedZero / detentsBelowZero
    }

    fun normalizedToDb(value: Float): Float =
        when (curve) {
            -1f -> {
                if (value <= 0f) return Float.NEGATIVE_INFINITY
                val x = value.coerceIn(0f, 1f)
                val taper = (x * x * x + x * x * x * x * x * x) / 2f
                return 20f * log10(taper) + maxDb
            }

            else -> {
                if (value <= 0f) return Float.NEGATIVE_INFINITY
                val x = value.coerceIn(0f, 1f)
                val x0 = (1000f * (-minDb) / (maxDb - minDb)).roundToInt() / 1000f
                return if (x >= x0) {
                    maxDb * ((x - x0) / (1f - x0)).pow(curve)
                } else {
                    minDb * ((x0 - x) / x0).pow(curve)
                }
            }
        }

    fun dbToNormalized(db: Float): Float =
        when (curve) {
            -1f -> {
                val g = 10f.pow((db - maxDb) / 20f)
                val u = (-1f + sqrt(1f + 8f * g)) / 2f
                return u.pow(1f / 3f).coerceIn(0f, 1f)
            }

            else -> {
                val x0 = (1000f * (-minDb) / (maxDb - minDb)).roundToInt() / 1000f
                return if (db >= 0f) {
                    x0 + (1f - x0) * (db / maxDb).pow(1f / curve)
                } else {
                    x0 - x0 * (db / minDb).pow(1f / curve)
                }
            }
        }
}

@Serializable
data class Mix(
    @CborLabel(1) val id: Int, // The id for the hardware out or receive
    @CborLabel(2)
    val trackId: Int, // The track ID the mix is sending to or receiving from -1 means it's a hardware output
    @CborLabel(3) val volume: Float = 0f,
    @CborLabel(4) val pan: Float = .5f,
    @CborLabel(5) val isMuted: Boolean = false,
)
