@file:OptIn(ExperimentalForeignApi::class)

package com.ethossoftworks.reacue.service.talkback

import co.touchlab.kermit.Logger
import kotlin.math.floor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive

/**
 * Captures mic audio via AVAudioEngine at the native hardware rate (typically 48 kHz float) and downsamples to
 * 16 kHz mono int16 with a continuous linear-interpolation resampler, emitting little-endian PCM16 frames.
 *
 * We deliberately do NOT use AVAudioConverter: its per-tap "no data now" usage discarded the resampler's boundary
 * samples each callback, so it consistently output below 16 kHz (the receiver then underran -> stutter). This manual
 * resampler keeps a continuous fractional read position across taps, so output rate is exactly inputRate/16000 with
 * no sample loss.
 */
class IosMicrophoneCaptureService : IMicrophoneCaptureService {

    override fun capture(): Flow<ByteArray> =
        callbackFlow {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
            session.setActive(true, error = null)

            val engine = AVAudioEngine()
            val input = engine.inputNode
            // Enable Apple's voice-processing I/O (AGC + noise suppression + AEC) so iOS capture level and noise floor
            // match Android's processed source. Must be set before reading the format / starting the engine, since it
            // can change the input format. AEC is harmless here (the app plays no audio to cancel).
            input.setVoiceProcessingEnabled(true, null)
            val inputFormat = input.inputFormatForBus(0.convert())
            val inputRate = inputFormat.sampleRate
            // Input samples consumed per output sample (e.g. 48000/16000 = 3.0).
            val step = inputRate / TALKBACK_SAMPLE_RATE.toDouble()
            Logger.i { "Talkback iOS capture: input ${inputRate} Hz, step $step" }

            // Continuous resampler state (persists across tap callbacks).
            // nextOutPos: virtual position (in input samples, absolute) of the next output sample to emit.
            // consumed: total input samples consumed before the current buffer starts.
            // prevLast: last sample of the previous buffer, for interpolation across the buffer boundary.
            var nextOutPos = 0.0
            var consumed = 0.0
            var prevLast = 0.0f

            // Diagnostics: how many frames the mic produced vs how many actually made it into the flow. A large
            // "dropped" count means trySend is discarding frames because the downstream consumer can't keep up.
            var producedFrames = 0
            var droppedFrames = 0

            input.installTapOnBus(0.convert(), bufferSize = 1024.convert(), format = inputFormat) { buffer, _ ->
                if (buffer == null) return@installTapOnBus
                val n = buffer.frameLength.toInt()
                val ch = buffer.floatChannelData?.get(0) ?: return@installTapOnBus

                // Emit every output sample whose interpolation window (i0, i0+1) is available in this buffer.
                val outputs = ArrayList<Int>(n)
                while (true) {
                    val localPos = nextOutPos - consumed // position within this buffer; may be in [-1, n)
                    val i0 = floor(localPos).toInt()
                    if (i0 + 1 > n - 1) break // need the next buffer to interpolate
                    val frac = (localPos - i0).toFloat()
                    val s0 = if (i0 < 0) prevLast else ch[i0]
                    val s1 = ch[i0 + 1]
                    val sample = s0 + (s1 - s0) * frac
                    val v = (sample * 32767f).toInt().coerceIn(-32768, 32767)
                    outputs.add(v)
                    nextOutPos += step
                }

                consumed += n
                if (n > 0) prevLast = ch[n - 1]

                if (outputs.isNotEmpty()) {
                    val bytes = ByteArray(outputs.size * 2)
                    var b = 0
                    outputs.forEach { v ->
                        bytes[b++] = (v and 0xFF).toByte()
                        bytes[b++] = ((v shr 8) and 0xFF).toByte()
                    }
                    producedFrames++
                    if (trySend(bytes).isFailure) droppedFrames++
                    if (producedFrames <= 5 || producedFrames % 10 == 0) {
                        Logger.i {
                            "Talkback iOS capture: produced=$producedFrames dropped=$droppedFrames " +
                                "frameSamples=${outputs.size} tapFrames=$n"
                        }
                    }
                }
            }

            engine.prepare()
            engine.startAndReturnError(null)

            awaitClose {
                input.removeTapOnBus(0.convert())
                engine.stop()
                session.setActive(false, error = null)
            }
        }
}
