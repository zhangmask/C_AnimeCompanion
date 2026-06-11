package com.companion.chat.data.engine

import com.companion.chat.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class BackendType {
    CPU,
    GPU
}

enum class ModelRuntime {
    LITERT_LM,
    LLAMA_CPP_GGUF
}

data class EngineConfig(
    val modelPath: String,
    val mmprojPath: String = "",
    val runtime: ModelRuntime = ModelRuntime.LLAMA_CPP_GGUF,
    val backend: BackendType = BackendType.CPU,
    val contextSize: Int = 2048,
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val systemPrompt: String = "",
    val useGpu: Boolean = false
)

sealed class InferenceState {
    data object Idle : InferenceState()
    data object Initializing : InferenceState()
    data object Ready : InferenceState()
    data class Generating(val partialText: String = "") : InferenceState()
    data class Error(val message: String) : InferenceState()
}

interface InferenceEngine {
    val state: StateFlow<InferenceState>

    suspend fun initialize(config: EngineConfig)

    fun getCurrentConfig(): EngineConfig?

    suspend fun rebuildConversation(systemPrompt: String): Boolean

    suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean

    suspend fun replayMessages(messages: List<ChatMessage>): Boolean

    fun sendMessageStream(
        messages: List<ChatMessage>
    ): Flow<String>

    fun cancel()

    fun release()
}
