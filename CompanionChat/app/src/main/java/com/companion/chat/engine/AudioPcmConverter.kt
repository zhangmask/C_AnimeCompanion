package com.companion.chat.engine

import kotlin.math.roundToInt

internal object AudioPcmConverter {
    fun pcm16ToFloatArray(samples: ShortArray, length: Int = samples.size): FloatArray {
        val safeLength = length.coerceIn(0, samples.size)
        return FloatArray(safeLength) { index ->
            if (samples[index] == Short.MIN_VALUE) {
                -1f
            } else {
                samples[index] / Short.MAX_VALUE.toFloat()
            }
        }
    }

    fun floatArrayToPcm16(samples: FloatArray): ShortArray {
        return ShortArray(samples.size) { index ->
            val sample = samples[index].coerceIn(-1f, 1f)
            if (sample <= -1f) {
                Short.MIN_VALUE
            } else {
                (sample * Short.MAX_VALUE)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }
}
