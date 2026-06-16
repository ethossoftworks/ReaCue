package com.ethossoftworks.reaperbleiem.service.talkback

import kotlinx.coroutines.flow.Flow

/**
 * Captures microphone audio for talkback. The flow is cold: capture starts when collection begins and the recorder is
 * released when collection is cancelled. Emitted [ByteArray]s are little-endian signed 16-bit mono PCM at
 * [TALKBACK_SAMPLE_RATE], roughly one ~20 ms frame per emission — the exact wire format ReaCue.eel expects.
 */
interface IMicrophoneCaptureService {
    fun capture(): Flow<ByteArray>
}

// 8 kHz mono (~128 kbps). 16 kHz/256 kbps exceeds the iOS-central BLE L2CAP throughput (confirmed by testing).
const val TALKBACK_SAMPLE_RATE = 8000
const val TALKBACK_FRAME_SAMPLES = 160 // 20 ms at 8 kHz
