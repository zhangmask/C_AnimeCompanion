package com.companion.chat.engine

import android.util.Log

/**
 * Ensures native libraries are loaded correctly on Android 14+ where linker namespace
 * isolation is strict. DO NOT pre-load libonnxruntime.so separately — it must be loaded
 * in the same namespace as libonnxruntime4j_jni.so by the OrtEnvironment static initializer.
 */
internal object SherpaOnnxNativeLoader {
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            // Trigger OrtEnvironment class loading, which loads both
            // libonnxruntime.so and libonnxruntime4j_jni.so in the same namespace
            try {
                Class.forName("ai.onnxruntime.OrtEnvironment")
                Log.d("SherpaOnnxLoader", "OrtEnvironment loaded successfully")
            } catch (e: Exception) {
                Log.e("SherpaOnnxLoader", "Failed to load OrtEnvironment: ${e.message}")
            }
            loaded = true
        }
    }
}
