package com.companion.chat.data.engine

object DefaultModelConfig {
    const val ExternalModelsDir = "models"
    const val GgufModelFileName = "Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf"
    const val GgufMmprojFileName = "mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf"
    const val LiteRtModelFileName = "gemma-4-E2B-it.litertlm"
    const val DefaultSystemPrompt = "你是一个友善的AI助手，请用中文回答用户的问题。"
    const val MaxPromptMessages = 6

    const val DefaultContextSize = 2048
    const val DefaultMaxTokens = 256
    const val DefaultTemperature = 0.7f
    const val DefaultTopK = 40
    const val DefaultTopP = 0.95f
}
