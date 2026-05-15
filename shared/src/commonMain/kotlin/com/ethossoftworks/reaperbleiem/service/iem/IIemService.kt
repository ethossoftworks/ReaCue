@file:OptIn(ExperimentalSerializationApi::class)
@file:UseSerializers(PersistentMapSerializer::class)

package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.PersistentMapSerializer
import kotlinx.collections.immutable.PersistentMap
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
    fun subscribe(): Flow<IemEvent>

    suspend fun refresh()

    suspend fun setOutputVolume(trackId: Int, value: Float)

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float)

    suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float)

    suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean)
}

@Serializable
@SerialName("IemEvent")
sealed class IemEvent {
    @Serializable @SerialName("0") data object Refresh : IemEvent()

    @Serializable @SerialName("1") data object Refreshing : IemEvent()

    @Serializable
    @SerialName("2")
    data class Refreshed(@CborLabel(1) val tracks: PersistentMap<Int, Track>) : IemEvent()

    @Serializable
    @SerialName("3")
    data class TrackNameUpdated(@CborLabel(1) val trackId: Int, @CborLabel(2) val name: String) : IemEvent()

    @Serializable
    @SerialName("5")
    data class ReceivePanUpdated(
        @CborLabel(1) val trackId: Int,
        @CborLabel(2) val receiveId: Int,
        @CborLabel(3) val value: Float,
    ) : IemEvent()

    @Serializable
    @SerialName("6")
    data class ReceiveVolumeUpdated(
        @CborLabel(1) val trackId: Int,
        @CborLabel(2) val receiveId: Int,
        @CborLabel(3) val value: Float,
    ) : IemEvent()

    @Serializable
    @SerialName("7")
    data class OutputVolumeUpdated(@CborLabel(1) val trackId: Int, @CborLabel(2) val value: Float) : IemEvent()

    @SerialName("8") data object Reset : IemEvent()

    @Serializable(with = IemErrorEventSerializer::class)
    @SerialName("9")
    data class Error(@CborLabel(1) val error: Any) : IemEvent()
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

object IemErrorEventSerializer : KSerializer<IemEvent.Error> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IemError", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): IemEvent.Error {
        return IemEvent.Error(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: IemEvent.Error) {
        encoder.encodeString(value.error.toString())
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
