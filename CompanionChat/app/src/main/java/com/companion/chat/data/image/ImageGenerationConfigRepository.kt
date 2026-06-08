package com.companion.chat.data.image

import android.content.Context
import android.content.SharedPreferences

class ImageGenerationConfigRepository(
    private val sharedPreferences: SharedPreferences,
    private val defaultLocalModelDirectoryProvider: (ImageGenerationProvider) -> String = { "" }
) {
    constructor(context: Context) : this(
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultLocalModelDirectoryProvider = { provider ->
            val relativeDirectory = when (provider) {
                ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP ->
                    StableDiffusionModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY
                else -> DreamLiteModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY
            }
            // DreamLite uses internal storage for reliable access;
            // Stable Diffusion uses external storage (legacy behaviour).
            if (provider == ImageGenerationProvider.LOCAL_DREAMLITE) {
                java.io.File(context.applicationContext.filesDir, relativeDirectory).absolutePath
            } else {
                context.applicationContext.getExternalFilesDir(relativeDirectory)?.absolutePath.orEmpty()
            }
        }
    )

    fun getConfig(): ImageGenerationConfig {
        val provider = runCatching {
            ImageGenerationProvider.valueOf(
                sharedPreferences.getString(
                    KEY_PROVIDER,
                    ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP.name
                ).orEmpty()
            )
        }.getOrDefault(ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP)
        return ImageGenerationConfig(
            baseUrl = sharedPreferences.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = sharedPreferences.getString(KEY_API_KEY, "").orEmpty(),
            model = sharedPreferences.getString(KEY_MODEL, "").orEmpty(),
            provider = provider,
            localModelPath = run {
                val stored = sharedPreferences.getString(KEY_LOCAL_MODEL_PATH, null)?.trim().orEmpty()
                val defaultPath = defaultLocalModelDirectoryProvider(provider)
                // Use stored path only if it is non-blank AND belongs to the
                // current provider's directory tree; otherwise fall back to default.
                val expectedRelative = when (provider) {
                    ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP ->
                        StableDiffusionModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY
                    else -> DreamLiteModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY
                }
                if (stored.isNotBlank() && stored.endsWith(expectedRelative)) stored else defaultPath
            },
            localWidth = sharedPreferences.getInt(KEY_LOCAL_WIDTH, 512).coerceIn(128, 2048),
            localHeight = sharedPreferences.getInt(KEY_LOCAL_HEIGHT, 512).coerceIn(128, 2048),
            localSteps = sharedPreferences.getInt(KEY_LOCAL_STEPS, 4).coerceIn(1, 50),
            localCfgScale = sharedPreferences.getFloat(KEY_LOCAL_CFG_SCALE, 1.0f).coerceIn(0f, 30f),
            localSeed = sharedPreferences.getLong(KEY_LOCAL_SEED, Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE },
            localUseVulkan = sharedPreferences.getBoolean(KEY_LOCAL_USE_VULKAN, true),
            requestTemplate = sharedPreferences.getString(
                KEY_REQUEST_TEMPLATE,
                ImageGenerationConfig.DEFAULT_REQUEST_TEMPLATE
            ).orEmpty().ifBlank { ImageGenerationConfig.DEFAULT_REQUEST_TEMPLATE },
            responseImageFieldPath = sharedPreferences.getString(
                KEY_RESPONSE_FIELD_PATH,
                ImageGenerationConfig.DEFAULT_RESPONSE_FIELD_PATH
            ).orEmpty().ifBlank { ImageGenerationConfig.DEFAULT_RESPONSE_FIELD_PATH },
            timeoutMillis = sharedPreferences.getInt(KEY_TIMEOUT_MILLIS, 60_000)
                .coerceIn(5_000, 180_000)
        )
    }

    fun updateConfig(config: ImageGenerationConfig) {
        sharedPreferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_LOCAL_MODEL_PATH, config.localModelPath.trim())
            .putInt(KEY_LOCAL_WIDTH, config.localWidth.coerceIn(128, 2048))
            .putInt(KEY_LOCAL_HEIGHT, config.localHeight.coerceIn(128, 2048))
            .putInt(KEY_LOCAL_STEPS, config.localSteps.coerceIn(1, 50))
            .putFloat(KEY_LOCAL_CFG_SCALE, config.localCfgScale.coerceIn(0f, 30f))
            .putBoolean(KEY_LOCAL_USE_VULKAN, config.localUseVulkan)
            .putString(KEY_REQUEST_TEMPLATE, config.requestTemplate.trim())
            .putString(KEY_RESPONSE_FIELD_PATH, config.responseImageFieldPath.trim())
            .putInt(KEY_TIMEOUT_MILLIS, config.timeoutMillis.coerceIn(5_000, 180_000))
            .also { editor ->
                if (config.localSeed == null) {
                    editor.remove(KEY_LOCAL_SEED)
                } else {
                    editor.putLong(KEY_LOCAL_SEED, config.localSeed)
                }
            }.apply()
    }

    fun getDreamLiteModelStatus(config: ImageGenerationConfig = getConfig()): DreamLiteModelStatus {
        return DreamLiteModelPackage.inspect(config.localModelPath)
    }

    fun getStableDiffusionModelStatus(
        config: ImageGenerationConfig = getConfig()
    ): StableDiffusionModelStatus {
        return StableDiffusionModelPackage.inspect(config.localModelPath)
    }

    /** Returns the default model directory for the given provider. */
    fun getDefaultPathForProvider(provider: ImageGenerationProvider): String {
        return defaultLocalModelDirectoryProvider(provider)
    }

    private companion object {
        const val PREFS_NAME = "image_generation_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_PROVIDER = "provider"
        const val KEY_LOCAL_MODEL_PATH = "local_model_path"
        const val KEY_LOCAL_WIDTH = "local_width"
        const val KEY_LOCAL_HEIGHT = "local_height"
        const val KEY_LOCAL_STEPS = "local_steps"
        const val KEY_LOCAL_CFG_SCALE = "local_cfg_scale"
        const val KEY_LOCAL_SEED = "local_seed"
        const val KEY_LOCAL_USE_VULKAN = "local_use_vulkan"
        const val KEY_REQUEST_TEMPLATE = "request_template"
        const val KEY_RESPONSE_FIELD_PATH = "response_image_field_path"
        const val KEY_TIMEOUT_MILLIS = "timeout_millis"
    }
}
