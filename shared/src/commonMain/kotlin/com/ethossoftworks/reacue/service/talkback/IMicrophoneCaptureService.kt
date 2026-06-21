package com.ethossoftworks.reacue.service.talkback

import kotlinx.coroutines.flow.Flow

/**
 * Captures microphone audio for talkback. The flow is cold: capture starts when collection begins and the recorder is
 * released when collection is cancelled. Emitted [ByteArray]s are little-endian signed 16-bit mono PCM at
 * [TALKBACK_SAMPLE_RATE], roughly one ~20 ms frame per emission — the exact wire format ReaCue.eel expects.
 */
interface IMicrophoneCaptureService {
    fun capture(): Flow<ByteArray>
}

// 16 kHz mono wideband voice. Raw PCM is 256 kbps (too much for iOS BLE), so the L2CAP link carries IMA-ADPCM
// (~64 kbps); the macOS host decodes back to 16 kHz PCM before Reaper. See ImaAdpcm.
const val TALKBACK_SAMPLE_RATE = 16000
const val TALKBACK_FRAME_SAMPLES = 320 // 20 ms at 16 kHz
