package com.companion.chat.data.engine

import android.content.Context
import java.io.File

data class ModelConfig(
    val runtime: ModelRuntime = ModelRuntime.LLAMA_CPP_GGUF,
    val modelPath: String = "",
    val backend: BackendType = BackendType.CPU,
    val contextSize: Int = DefaultModelConfig.DefaultContextSize,
    val maxTokens: Int = DefaultModelConfig.DefaultMaxTokens,
    val temperature: Float = DefaultModelConfig.DefaultTemperature,
    val topK: Int = DefaultModelConfig.DefaultTopK,
    val topP: Float = DefaultModelConfig.DefaultTopP,
    val useGpu: Boolean = false
)

class ModelConfigRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): ModelConfig {
        val runtime = sharedPreferences.getString(KEY_RUNTIME, null)
            ?.let { value -> runCatching { ModelRuntime.valueOf(value) }.getOrNull() }
            ?: ModelRuntime.LLAMA_CPP_GGUF
        val backend = sharedPreferences.getString(KEY_BACKEND, null)
            ?.let { value -> runCatching { BackendType.valueOf(value) }.getOrNull() }
            ?: BackendType.CPU
        val modelPath = sharedPreferences.getString(KEY_MODEL_PATH, null)
            ?.trim()
            .orEmpty()

        return ModelConfig(
            runtime = runtime,
            modelPath = modelPath,
            backend = backend,
            contextSize = sharedPreferences.getInt(KEY_CONTEXT_SIZE, DefaultModelConfig.DefaultContextSize),
            maxTokens = sharedPreferences.getInt(KEY_MAX_TOKENS, DefaultModelConfig.DefaultMaxTokens),
            temperature = sharedPreferences.getFloat(KEY_TEMPERATURE, DefaultModelConfig.DefaultTemperature),
            topK = sharedPreferences.getInt(KEY_TOP_K, DefaultModelConfig.DefaultTopK),
            topP = sharedPreferences.getFloat(KEY_TOP_P, DefaultModelConfig.DefaultTopP),
            useGpu = sharedPreferences.getBoolean(KEY_USE_GPU, false)
        ).normalized()
    }

    fun updateConfig(config: ModelConfig) {
        val normalized = config.normalized()
        sharedPreferences.edit()
            .putString(KEY_RUNTIME, normalized.runtime.name)
            .putString(KEY_MODEL_PATH, normalized.modelPath)
            .putString(KEY_BACKEND, normalized.backend.name)
            .putInt(KEY_CONTEXT_SIZE, normalized.contextSize)
            .putInt(KEY_MAX_TOKENS, normalized.maxTokens)
            .putFloat(KEY_TEMPERATURE, normalized.temperature)
            .putInt(KEY_TOP_K, normalized.topK)
            .putFloat(KEY_TOP_P, normalized.topP)
            .putBoolean(KEY_USE_GPU, normalized.useGpu)
            .apply()
    }

    fun resolveModelPath(config: ModelConfig = getConfig()): String {
        val explicitPath = config.modelPath.trim()
        if (explicitPath.isNotBlank()) return explicitPath

        val fileName = when (config.runtime) {
            ModelRuntime.LLAMA_CPP_GGUF -> DefaultModelConfig.GgufModelFileName
            ModelRuntime.LITERT_LM -> DefaultModelConfig.LiteRtModelFileName
        }
        val externalDir = appContext.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
        return if (externalDir != null) {
            File(externalDir, fileName).absolutePath
        } else {
            File(File(appContext.filesDir, DefaultModelConfig.ExternalModelsDir), fileName).absolutePath
        }
    }

    fun resolveMmprojPath(): String {
        val externalDir = appContext.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
        return if (externalDir != null) {
            File(externalDir, DefaultModelConfig.GgufMmprojFileName).absolutePath
        } else {
            File(File(appContext.filesDir, DefaultModelConfig.ExternalModelsDir), DefaultModelConfig.GgufMmprojFileName).absolutePath
        }
    }

    fun toEngineConfig(
        systemPrompt: String,
        config: ModelConfig = getConfig()
    ): EngineConfig {
        val normalized = config.normalized()
        return EngineConfig(
            modelPath = resolveModelPath(normalized),
            mmprojPath = if (normalized.runtime == ModelRuntime.LLAMA_CPP_GGUF) resolveMmprojPath() else "",
            runtime = normalized.runtime,
            backend = normalized.backend,
            contextSize = normalized.contextSize,
            maxTokens = normalized.maxTokens,
            temperature = normalized.temperature,
            topK = normalized.topK,
            topP = normalized.topP,
            systemPrompt = systemPrompt,
            useGpu = normalized.useGpu
        )
    }

    private fun ModelConfig.normalized(): ModelConfig {
        return copy(
            modelPath = modelPath.trim(),
            contextSize = contextSize.coerceIn(512, 32768),
            maxTokens = maxTokens.coerceIn(1, 1024),
            temperature = temperature.coerceIn(0.0f, 2.0f),
            topK = topK.coerceIn(1, 200),
            topP = topP.coerceIn(0.01f, 1.0f)
        )
    }

    companion object {
        private const val PREFS_NAME = "model_config"
        private const val KEY_RUNTIME = "runtime"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_BACKEND = "backend"
        private const val KEY_CONTEXT_SIZE = "context_size"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_USE_GPU = "use_gpu"
    }
}
