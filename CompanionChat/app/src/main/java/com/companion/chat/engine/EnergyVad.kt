package com.companion.chat.engine

/**
 * 简易能量 VAD，不依赖 sherpa-onnx。
 * 通过 RMS 能量阈值检测语音起止：能量高于 threshold 视为说话，
 * 持续低于 threshold 达 minSilenceDuration 视为结束。
 * 接口与 SherpaOnnxSileroVad 一致，便于在 recordUntilSilence 中替换。
 */
internal class EnergyVad(
    private val sampleRate: Int = 16_000,
    private val threshold: Float = 0.02f,
    private val minSilenceDuration: Float = 0.6f,
    private val minSpeechDuration: Float = 0.25f,
    private val maxSpeechDuration: Float = 15.0f
) {
    private enum class State { WAITING_FOR_SPEECH, IN_SPEECH, IN_SILENCE_AFTER_SPEECH }

    private var state = State.WAITING_FOR_SPEECH
    private val speechBuffer = mutableListOf<Float>()
    private val pendingSegments = mutableListOf<RecordedAudio>()
    private var silenceFrameCount = 0
    private var speechFrameCount = 0
    private var totalSamplesProcessed = 0

    private val minSilenceFrames = (minSilenceDuration * sampleRate).toInt()
    private val minSpeechFrames = (minSpeechDuration * sampleRate).toInt()
    private val maxSpeechFrames = (maxSpeechDuration * sampleRate).toInt()

    fun acceptWaveform(samples: FloatArray) {
        if (samples.isEmpty()) return
        var i = 0
        while (i < samples.size) {
            val chunk = minOf(samples.size - i, sampleRate / 20) // 50ms windows
            val energy = rmsEnergy(samples, i, chunk)
            val isSpeech = energy > threshold

            when (state) {
                State.WAITING_FOR_SPEECH -> {
                    if (isSpeech) {
                        state = State.IN_SPEECH
                        speechFrameCount = 0
                        speechBuffer.clear()
                        appendSamples(samples, i, chunk)
                        speechFrameCount += chunk
                    }
                }
                State.IN_SPEECH -> {
                    appendSamples(samples, i, chunk)
                    speechFrameCount += chunk
                    if (!isSpeech) {
                        silenceFrameCount = chunk
                        state = State.IN_SILENCE_AFTER_SPEECH
                    } else {
                        silenceFrameCount = 0
                    }
                    if (speechFrameCount >= maxSpeechFrames) {
                        flushSpeechSegment()
                    }
                }
                State.IN_SILENCE_AFTER_SPEECH -> {
                    appendSamples(samples, i, chunk)
                    if (isSpeech) {
                        silenceFrameCount = 0
                        speechFrameCount += chunk
                        state = State.IN_SPEECH
                    } else {
                        silenceFrameCount += chunk
                        if (silenceFrameCount >= minSilenceFrames && speechFrameCount >= minSpeechFrames) {
                            flushSpeechSegment()
                        }
                    }
                }
            }
            i += chunk
            totalSamplesProcessed += chunk
        }
    }

    fun drainSegments(): List<RecordedAudio> {
        if (pendingSegments.isEmpty()) return emptyList()
        val result = pendingSegments.toList()
        pendingSegments.clear()
        return result
    }

    fun flush() {
        if (state == State.IN_SPEECH || state == State.IN_SILENCE_AFTER_SPEECH) {
            if (speechFrameCount >= minSpeechFrames) {
                flushSpeechSegment()
            } else {
                speechBuffer.clear()
                speechFrameCount = 0
                state = State.WAITING_FOR_SPEECH
            }
        }
    }

    fun release() {
        speechBuffer.clear()
        pendingSegments.clear()
    }

    private fun flushSpeechSegment() {
        if (speechBuffer.isEmpty()) {
            state = State.WAITING_FOR_SPEECH
            return
        }
        val pcm16 = AudioPcmConverter.floatArrayToPcm16(speechBuffer.toFloatArray())
        pendingSegments.add(RecordedAudio(pcm16, sampleRate))
        speechBuffer.clear()
        speechFrameCount = 0
        silenceFrameCount = 0
        state = State.WAITING_FOR_SPEECH
    }

    private fun appendSamples(samples: FloatArray, start: Int, length: Int) {
        for (j in 0 until length) {
            speechBuffer.add(samples[start + j])
        }
    }

    private fun rmsEnergy(samples: FloatArray, start: Int, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (j in 0 until length) {
            val s = samples[start + j].toDouble()
            sum += s * s
        }
        return Math.sqrt(sum / length).toFloat()
    }
}
