@file:OptIn(ExperimentalSerializationApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    @Serializable @SerialName("2") data class Refreshed(@CborLabel(1) val tracks: List<Track>) : IemEvent()

    @Serializable
    @SerialName("3")
    data class TrackNameUpdated(@CborLabel(1) val trackId: Int, @CborLabel(2) val name: String) : IemEvent()

    @Serializable
    @SerialName("4")
    data class ReceiveRegistered(
        @CborLabel(1) val trackId: Int,
        @CborLabel(2) val receiveId: Int,
        @CborLabel(3) val srcTrackId: Int,
    ) : IemEvent()

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

    @Serializable(with = IemErrorEventSerializer::class)
    @SerialName("8")
    class Error(@CborLabel(1) val error: Any) : IemEvent()
}

@Serializable
data class Track(
    @CborLabel(1) val id: Int,
    @CborLabel(2) val name: String,
    @CborLabel(3) val sendCount: Int,
    @CborLabel(4) val receiveCount: Int,
    @CborLabel(5) val hardwareOutCount: Int,
) {
    val isIem = hardwareOutCount > 0 && receiveCount > 0
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
