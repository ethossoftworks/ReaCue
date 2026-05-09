package com.ethossoftworks.reaperbleiem.service.iem

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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

sealed class IemEvent {
    @Serializable data object Refreshing : IemEvent()

    @Serializable data class Refreshed(val tracks: List<Track>) : IemEvent()

    @Serializable data class TrackNameUpdated(val trackId: Int, val name: String) : IemEvent()

    @Serializable data class ReceiveRegistered(val trackId: Int, val receiveId: Int, val srcTrackId: Int) : IemEvent()

    @Serializable data class ReceivePanUpdated(val trackId: Int, val receiveId: Int, val value: Float) : IemEvent()

    @Serializable data class ReceiveVolumeUpdated(val trackId: Int, val receiveId: Int, val value: Float) : IemEvent()

    @Serializable data class OutputVolumeUpdated(val trackId: Int, val value: Float) : IemEvent()

    @Serializable(with = IemErrorEventSerializer::class) class Error(val error: Any) : IemEvent()

    companion object
}

@Serializable
data class Track(val id: Int, val name: String, val sendCount: Int, val receiveCount: Int, val hardwareOutCount: Int) {
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
