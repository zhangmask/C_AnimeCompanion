package com.companion.chat.engine

import android.util.Log

object MnnLlmJni {
    private const val TAG = "MnnLlmJni"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("MNN")
                System.loadLibrary("mnn_llm_jni")
                loaded = true
                Log.i(TAG, "MNN LLM JNI loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load MNN LLM: ${e.message}")
            }
        }
    }

    fun isLoaded(): Boolean = loaded

    interface TokenCallback {
        fun onToken(text: String): Boolean
    }

    external fun nativeCreate(configPath: String): Long

    external fun nativeGenerate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): Boolean

    external fun nativeGenerateWithImages(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        imageBytes: Array<ByteArray>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    ): Boolean

    external fun nativeReset(handle: Long)

    external fun nativeCancel(handle: Long)

    external fun nativeRelease(handle: Long)
}
