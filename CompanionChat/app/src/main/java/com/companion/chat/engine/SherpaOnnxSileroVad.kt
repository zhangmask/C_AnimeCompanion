package com.companion.chat.engine

import android.content.res.AssetManager
import android.util.Log

internal data class SileroVadConfigValues(
    val model: String,
    val threshold: Float = 0.5f,
    val minSilenceDuration: Float = 0.5f,
    val minSpeechDuration: Float = 0.25f,
    val windowSize: Int = 512,
    val maxSpeechDuration: Float = 15.0f,
    val sampleRate: Int = 16_000,
    val numThreads: Int = 1,
    val provider: String = "cpu",
    val debug: Boolean = false
)

internal class SherpaOnnxSileroVad(
    private val assetManager: AssetManager?,
    private val configValues: SileroVadConfigValues
) {
    init {
        SherpaOnnxNativeLoader.ensureLoaded()
    }

    private val vadClass = Class.forName("com.k2fsa.sherpa.onnx.Vad")
    private val vad: Any = runCatching {
        val config = buildVadModelConfig(configValues)
        vadClass
            .getConstructor(AssetManager::class.java, config.javaClass)
            .newInstance(assetManager, config)
    }.getOrElse { throwable ->
        Log.e(TAG, "sherpa-onnx Silero VAD 初始化失败", throwable)
        throw IllegalStateException("本地 Silero VAD 初始化失败: ${throwable.message}", throwable)
    }

    fun acceptWaveform(samples: FloatArray) {
        vadClass.getMethod("acceptWaveform", FloatArray::class.java).invoke(vad, samples)
    }

    fun drainSegments(): List<RecordedAudio> {
        val segments = mutableListOf<RecordedAudio>()
        val emptyMethod = vadClass.getMethod("empty")
        val frontMethod = vadClass.getMethod("front")
        val popMethod = vadClass.getMethod("pop")

        while (emptyMethod.invoke(vad) == false) {
            val segment = frontMethod.invoke(vad)
            val samples = segment?.javaClass
                ?.getMethod("getSamples")
                ?.invoke(segment) as? FloatArray
            if (samples != null && samples.isNotEmpty()) {
                segments += RecordedAudio(
                    pcm16 = AudioPcmConverter.floatArrayToPcm16(samples),
                    sampleRate = configValues.sampleRate
                )
            }
            popMethod.invoke(vad)
        }

        return segments
    }

    fun flush() {
        vadClass.getMethod("flush").invoke(vad)
    }

    fun release() {
        runCatching {
            vadClass.getMethod("release").invoke(vad)
        }
    }

    private fun buildVadModelConfig(configValues: SileroVadConfigValues): Any {
        val sileroConfig = newInstance(
            className = "com.k2fsa.sherpa.onnx.SileroVadModelConfig",
            propertyValues = mapOf(
                "model" to configValues.model,
                "threshold" to configValues.threshold,
                "minSilenceDuration" to configValues.minSilenceDuration,
                "minSpeechDuration" to configValues.minSpeechDuration,
                "windowSize" to configValues.windowSize,
                "maxSpeechDuration" to configValues.maxSpeechDuration
            )
        )
        return newInstance(
            className = "com.k2fsa.sherpa.onnx.VadModelConfig",
            propertyValues = mapOf(
                "sileroVadModelConfig" to sileroConfig,
                "sampleRate" to configValues.sampleRate,
                "numThreads" to configValues.numThreads,
                "provider" to configValues.provider,
                "debug" to configValues.debug
            )
        )
    }

    private fun newInstance(className: String, propertyValues: Map<String, Any?>): Any {
        val instance = Class.forName(className).getConstructor().newInstance()
        propertyValues.forEach { (propertyName, value) ->
            val setterName = "set${propertyName.replaceFirstChar { it.uppercaseChar() }}"
            val setter = instance.javaClass.methods
                .firstOrNull { method -> method.name == setterName && method.parameterTypes.size == 1 }
            setter?.invoke(instance, value)
        }
        return instance
    }

    private companion object {
        const val TAG = "SileroVad"
    }
}
