package com.companion.chat.engine

internal object LlamaCppNative {
    init {
        System.loadLibrary("companion_llama")
    }

    interface TokenCallback {
        fun onTokenBytes(bytes: ByteArray)

        fun onPerformanceLog(message: String)
    }

    external fun loadModel(
        modelPath: String,
        mmprojPath: String,
        contextSize: Int,
        systemPrompt: String
    ): Long

    external fun generate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    )

    external fun generateMultimodal(
        handle: Long,
        prompt: String,
        imageBytes: Array<ByteArray>,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        callback: TokenCallback
    )

    external fun cancel(handle: Long)

    external fun releaseModel(handle: Long)

    external fun systemInfo(): String
}
