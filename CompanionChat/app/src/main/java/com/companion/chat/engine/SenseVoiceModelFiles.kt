package com.companion.chat.engine

import java.io.File

internal data class SenseVoiceModelFiles(
    val model: String,
    val tokens: String,
    val vad: String
)

internal fun resolveSenseVoiceModelFiles(modelDirectory: String): SenseVoiceModelFiles {
    val directory = File(modelDirectory)
    return SenseVoiceModelFiles(
        model = File(directory, "model.int8.onnx").absolutePath,
        tokens = File(directory, "tokens.txt").absolutePath,
        vad = File(directory, "silero_vad.onnx").absolutePath
    )
}
