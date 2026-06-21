package com.ethossoftworks.reacue.service.talkback

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Captures 16 kHz mono PCM16 directly from [AudioRecord] (no resampling needed). Uses VOICE_COMMUNICATION so the
 * platform applies echo cancellation / noise suppression — appropriate for talkback. Requires the RECORD_AUDIO
 * permission to have been granted before collection begins.
 */
class AndroidMicrophoneCaptureService : IMicrophoneCaptureService {

    @SuppressLint("MissingPermission")
    override fun capture(): Flow<ByteArray> =
        flow {
                val frameBytes = TALKBACK_FRAME_SAMPLES * 2
                val minBuffer =
                    AudioRecord.getMinBufferSize(
                        TALKBACK_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                // Keep the internal buffer small to minimize capture latency (just enough to ride out scheduling).
                val bufferSize = maxOf(minBuffer, frameBytes * 2)

                val record =
                    AudioRecord(
                        // VOICE_RECOGNITION: low-latency path with minimal DSP. VOICE_COMMUNICATION's AEC/NS/AGC
                        // chain adds significant latency and isn't needed for talkback (the phone never hears the
                        // IEMs).
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        TALKBACK_SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Logger.e { "AudioRecord failed to initialize (missing RECORD_AUDIO permission?)" }
                    record.release()
                    return@flow
                }

                try {
                    record.startRecording()
                    val buffer = ByteArray(frameBytes)
                    while (currentCoroutineContext().isActive) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) emit(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
                    }
                } finally {
                    runCatching { record.stop() }
                    record.release()
                }
            }
            .flowOn(Dispatchers.IO)
}
