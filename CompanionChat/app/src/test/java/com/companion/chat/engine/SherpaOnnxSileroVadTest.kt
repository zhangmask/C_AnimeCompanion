package com.companion.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxSileroVadTest {

    @Test
    fun `默认 Silero VAD 参数匹配端侧录音配置`() {
        val config = SileroVadConfigValues(model = "/models/silero_vad.onnx")

        assertEquals("/models/silero_vad.onnx", config.model)
        assertEquals(16_000, config.sampleRate)
        assertEquals(512, config.windowSize)
        assertEquals(0.5f, config.threshold)
        assertEquals(0.5f, config.minSilenceDuration)
        assertEquals(0.25f, config.minSpeechDuration)
        assertEquals(15.0f, config.maxSpeechDuration)
        assertEquals(1, config.numThreads)
        assertEquals("cpu", config.provider)
        assertEquals(false, config.debug)
    }

    @Test
    fun `PCM16 转 FloatArray 时保持归一化范围`() {
        val floats = AudioPcmConverter.pcm16ToFloatArray(
            shortArrayOf(Short.MIN_VALUE, (-16_384).toShort(), 0, 16_384, Short.MAX_VALUE)
        )

        assertEquals(-1.0f, floats[0])
        assertEquals(-16_384 / Short.MAX_VALUE.toFloat(), floats[1])
        assertEquals(0.0f, floats[2])
        assertEquals(16_384 / Short.MAX_VALUE.toFloat(), floats[3])
        assertEquals(1.0f, floats[4])
        assertTrue(floats.all { sample -> sample in -1.0f..1.0f })
    }

    @Test
    fun `FloatArray 转 PCM16 时裁剪到合法范围`() {
        val shorts = AudioPcmConverter.floatArrayToPcm16(floatArrayOf(-2f, -1f, 0f, 1f, 2f))

        assertEquals(Short.MIN_VALUE, shorts[0])
        assertEquals(Short.MIN_VALUE, shorts[1])
        assertEquals(0, shorts[2].toInt())
        assertEquals(Short.MAX_VALUE, shorts[3])
        assertEquals(Short.MAX_VALUE, shorts[4])
    }
}
