package com.companion.chat.engine

import android.content.Context
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.ModelRuntime

class InferenceEngineFactory(
    private val context: Context
) {
    fun create(runtime: ModelRuntime): InferenceEngine {
        return when (runtime) {
            ModelRuntime.LLAMA_CPP_GGUF -> LlamaCppInferenceEngine(context)
            ModelRuntime.LITERT_LM -> LiteRTLMInferenceEngine(context)
            ModelRuntime.MNN_LLM -> MnnLlmInferenceEngine(context)
            ModelRuntime.CUSTOM_API -> CustomApiInferenceEngine()
        }
    }
}
