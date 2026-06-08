package com.companion.chat.engine

import android.content.res.AssetManager
import android.util.Log

internal class SherpaOnnxSenseVoiceRecognizer(
    private val assetManager: AssetManager?,
    private val modelFiles: SenseVoiceModelFiles
) {
    init {
        SherpaOnnxNativeLoader.ensureLoaded()
    }

    fun transcribe(audio: RecordedAudio): String {
        if (audio.isEmpty) return ""

        return runCatching {
            val offlineRecognizerClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            val offlineStreamClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineStream")
            val config = buildOfflineRecognizerConfig()
            val recognizer = offlineRecognizerClass
                .getConstructor(AssetManager::class.java, config.javaClass)
                .newInstance(assetManager, config)
            val stream = offlineRecognizerClass
                .getMethod("createStream")
                .invoke(recognizer)

            offlineStreamClass
                .getMethod("acceptWaveform", FloatArray::class.java, Int::class.javaPrimitiveType)
                .invoke(stream, AudioPcmConverter.pcm16ToFloatArray(audio.pcm16), audio.sampleRate)
            offlineRecognizerClass
                .getMethod("decode", offlineStreamClass)
                .invoke(recognizer, stream)

            val result = offlineRecognizerClass
                .getMethod("getResult", offlineStreamClass)
                .invoke(recognizer, stream)
            val text = result?.javaClass?.getMethod("getText")?.invoke(result)?.toString().orEmpty()
            runCatching { offlineStreamClass.getMethod("release").invoke(stream) }
            runCatching { offlineRecognizerClass.getMethod("release").invoke(recognizer) }
            text
        }.getOrElse { throwable ->
            Log.e(TAG, "sherpa-onnx SenseVoice 识别失败", throwable)
            throw IllegalStateException("本地 SenseVoice 识别失败: ${throwable.message}", throwable)
        }
    }

    private fun buildOfflineRecognizerConfig(): Any {
        val senseVoiceConfig = newInstance(
            className = "com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig",
            propertyValues = mapOf(
                "model" to modelFiles.model,
                "language" to "auto",
                "useInverseTextNormalization" to true
            )
        )
        val modelConfig = newInstance(
            className = "com.k2fsa.sherpa.onnx.OfflineModelConfig",
            propertyValues = mapOf(
                "senseVoice" to senseVoiceConfig,
                "numThreads" to 2,
                "debug" to false,
                "tokens" to modelFiles.tokens
            )
        )
        return newInstance(
            className = "com.k2fsa.sherpa.onnx.OfflineRecognizerConfig",
            propertyValues = mapOf(
                "modelConfig" to modelConfig
            )
        )
    }

    private fun newInstance(className: String, propertyValues: Map<String, Any?>): Any {
        val instance = Class.forName(className).getConstructor().newInstance()
        propertyValues.forEach { (propertyName, value) ->
            runCatching {
                val setterName = "set${propertyName.replaceFirstChar { it.uppercaseChar() }}"
                instance.javaClass.methods
                    .firstOrNull { method -> method.name == setterName && method.parameterTypes.size == 1 }
                    ?.invoke(instance, value)
            }
        }
        return instance
    }

    private companion object {
        const val TAG = "SenseVoiceRecognizer"
    }
}
