package com.ethossoftworks.reaperbleiem.service.talkback

/**
 * IMA ADPCM codec for simple audio compression. Compresses 16-bit mono PCM ~4:1 (16 kHz / 256 kbps -> ~64 kbps)
 * so full 16 kHz audio fits the iOS-central BLE L2CAP link (which can't sustain raw 256 kbps).
 *
 * Block layout (little-endian): sampleCount (u16), predictor (i16), stepIndex (u8), reserved (u8), then
 * ceil(sampleCount / 2) bytes of 4-bit codes (low nibble first).
 */
const val ADPCM_HEADER_SIZE = 6

private val STEP_TABLE =
    intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
        107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871,
        5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623,
        27086, 29794, 32767,
    )

private val INDEX_TABLE = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)

class AdpcmEncoder {
    private var predictor = 0
    private var stepIndex = 0

    fun encode(pcm: ByteArray): ByteArray {
        val sampleCount = pcm.size / 2
        val out = ByteArray(ADPCM_HEADER_SIZE + (sampleCount + 1) / 2)
        out[0] = (sampleCount and 0xFF).toByte()
        out[1] = ((sampleCount shr 8) and 0xFF).toByte()
        out[2] = (predictor and 0xFF).toByte()
        out[3] = ((predictor shr 8) and 0xFF).toByte()
        out[4] = stepIndex.toByte()
        out[5] = 0

        var outPos = ADPCM_HEADER_SIZE
        var pendingLow = 0
        var havePending = false
        var i = 0
        while (i < sampleCount) {
            val sample = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
            val code = encodeSample(sample)
            if (!havePending) {
                pendingLow = code
                havePending = true
            } else {
                out[outPos++] = ((code shl 4) or pendingLow).toByte()
                havePending = false
            }
            i++
        }
        if (havePending) out[outPos] = pendingLow.toByte()
        return out
    }

    private fun encodeSample(sample: Int): Int {
        val step = STEP_TABLE[stepIndex]
        var diff = sample - predictor
        var sign = 0
        if (diff < 0) {
            sign = 8
            diff = -diff
        }
        var code = 0
        var s = step
        if (diff >= s) {
            code = code or 4
            diff -= s
        }
        s = s shr 1
        if (diff >= s) {
            code = code or 2
            diff -= s
        }
        s = s shr 1
        if (diff >= s) code = code or 1

        var diffq = step shr 3
        if (code and 4 != 0) diffq += step
        if (code and 2 != 0) diffq += step shr 1
        if (code and 1 != 0) diffq += step shr 2
        predictor = (if (sign != 0) predictor - diffq else predictor + diffq).coerceIn(-32768, 32767)
        stepIndex = (stepIndex + INDEX_TABLE[code]).coerceIn(0, 88)
        return code or sign
    }
}

object AdpcmDecoder {
    /** Decodes one self-contained block (as produced by [AdpcmEncoder.encode]) back to little-endian PCM16 bytes. */
    fun decode(block: ByteArray): ByteArray {
        if (block.size < ADPCM_HEADER_SIZE) return ByteArray(0)
        val sampleCount = (block[0].toInt() and 0xFF) or ((block[1].toInt() and 0xFF) shl 8)
        var predictor = (block[2].toInt() and 0xFF) or (block[3].toInt() shl 8)
        var stepIndex = (block[4].toInt() and 0xFF).coerceIn(0, 88)

        val out = ByteArray(sampleCount * 2)
        var inPos = ADPCM_HEADER_SIZE
        var curByte = 0
        var haveHigh = false
        var i = 0
        while (i < sampleCount) {
            val code: Int
            if (!haveHigh) {
                if (inPos >= block.size) break
                curByte = block[inPos++].toInt() and 0xFF
                code = curByte and 0x0F
                haveHigh = true
            } else {
                code = (curByte shr 4) and 0x0F
                haveHigh = false
            }

            val step = STEP_TABLE[stepIndex]
            var diffq = step shr 3
            if (code and 4 != 0) diffq += step
            if (code and 2 != 0) diffq += step shr 1
            if (code and 1 != 0) diffq += step shr 2
            predictor = (if (code and 8 != 0) predictor - diffq else predictor + diffq).coerceIn(-32768, 32767)
            stepIndex = (stepIndex + INDEX_TABLE[code]).coerceIn(0, 88)

            out[i * 2] = (predictor and 0xFF).toByte()
            out[i * 2 + 1] = ((predictor shr 8) and 0xFF).toByte()
            i++
        }
        return out
    }
}
