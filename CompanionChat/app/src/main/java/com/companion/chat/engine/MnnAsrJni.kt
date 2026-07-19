package com.companion.chat.engine

import android.util.Log

object MnnAsrJni {
    private const val TAG = "MnnAsrJni"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("MNN")
                System.loadLibrary("mnn_asr_jni")
                loaded = true
                Log.i(TAG, "MNN ASR JNI loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load MNN ASR: ${e.message}")
            }
        }
    }

    fun isLoaded(): Boolean = loaded

    external fun nativeInitFromBytes(modelBytes: ByteArray, tokensContent: String): Boolean

    external fun nativeTranscribe(audioData: FloatArray, sampleRate: Int): String

    external fun nativeRelease()
}
