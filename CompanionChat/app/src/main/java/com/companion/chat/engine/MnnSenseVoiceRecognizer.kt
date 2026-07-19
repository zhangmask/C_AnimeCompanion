package com.companion.chat.engine

import android.util.Log
import java.io.File

/**
 * MNN-based SenseVoice recognizer.
 * Replaces sherpa-onnx for ASR inference using MNN Module API.
 * Feature extraction (kaldi-style mel + 7-frame stacking) and CTC decoding
 * are implemented in native code (mnn_asr_jni.cpp).
 *
 * Model and tokens are read in Kotlin and passed as bytes to JNI to bypass
 * Android scoped storage restrictions on native file I/O.
 */
internal class MnnSenseVoiceRecognizer(
    private val modelFiles: MnnAsrModelFiles
) {
    private var initialized = false

    init {
        MnnAsrJni.ensureLoaded()
    }

    fun transcribe(audio: RecordedAudio): String {
        if (audio.isEmpty) return ""

        return runCatching {
            if (!MnnAsrJni.isLoaded()) {
                throw IllegalStateException("MNN ASR JNI not loaded")
            }
            // Initialize model on first use (read files in Kotlin, pass bytes to JNI)
            if (!initialized) {
                val modelBytes = File(modelFiles.modelPath).readBytes()
                val tokensContent = File(modelFiles.tokensPath).readText()
                Log.i(TAG, "MNN ASR init: model=${modelFiles.modelPath} (${modelBytes.size} bytes), tokens=${modelFiles.tokensPath}")
                val initOk = MnnAsrJni.nativeInitFromBytes(modelBytes, tokensContent)
                if (!initOk) {
                    throw IllegalStateException("MNN ASR model init failed: ${modelFiles.modelPath}")
                }
                initialized = true
            }
            MnnAsrJni.nativeTranscribe(
                AudioPcmConverter.pcm16ToFloatArray(audio.pcm16),
                audio.sampleRate
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "MNN SenseVoice 识别失败", throwable)
            throw IllegalStateException("本地 MNN SenseVoice 识别失败: ${throwable.message}", throwable)
        }
    }

    fun release() {
        if (MnnAsrJni.isLoaded()) {
            MnnAsrJni.nativeRelease()
        }
        initialized = false
    }

    private companion object {
        const val TAG = "MnnSenseVoiceRecognizer"
    }
}

internal data class MnnAsrModelFiles(
    val modelPath: String,
    val tokensPath: String
)

internal fun resolveMnnAsrModelFiles(modelDirectory: String): MnnAsrModelFiles {
    val directory = File(modelDirectory)
    return MnnAsrModelFiles(
        modelPath = File(directory, "sensevoice_wq8.mnn").absolutePath,
        tokensPath = File(directory, "tokens.txt").absolutePath
    )
}
