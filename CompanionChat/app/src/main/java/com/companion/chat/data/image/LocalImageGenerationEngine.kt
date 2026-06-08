package com.companion.chat.data.image

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalImageGenerationEngine(
    context: Context? = null
) : ImageGenerationEngine {

    private val imageFileStore = context?.let { ImageFileStore(it) }
    private val _state = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    override val state: StateFlow<ImageGenerationState> = _state.asStateFlow()

    override suspend fun generate(
        prompt: String,
        config: ImageGenerationConfig,
        purpose: ImageGenerationPurpose
    ): Result<String> {
        return generate(
            request = ImageGenerationRequest(prompt = prompt, purpose = purpose),
            config = config
        )
    }

    override suspend fun generate(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext when (config.provider) {
            ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP -> generateStableDiffusion(request, config)
            else -> generateDreamLite(request, config)
        }
    }

    private fun generateDreamLite(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> {
        val store = imageFileStore
        if (store == null) {
            val error = "DreamLite 需要 Android Context 才能保存图片"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalStateException(error))
        }
        if (request.prompt.isBlank()) {
            val error = "DreamLite 提示词不能为空"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalArgumentException(error))
        }

        when (val status = DreamLiteModelPackage.inspect(config.localModelPath)) {
            DreamLiteModelStatus.Ready -> Unit
            DreamLiteModelStatus.DirectoryNotConfigured -> {
                val error = "DreamLite 模型目录未配置"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is DreamLiteModelStatus.InvalidConfig -> {
                val error = "DreamLite 配置无效：${status.message}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is DreamLiteModelStatus.MissingFiles -> {
                val error = "DreamLite 模型文件缺失：${status.fileNames.joinToString()}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
        }

        _state.value = ImageGenerationState.Generating
        return runCatching {
            // DreamLite UNet trained for 1024×1024 pixels (sample_size=128, vae_scale_factor=8).
            val width = 1024
            val height = 1024
            val steps = config.localSteps.takeIf { it > 0 } ?: 4
            val seed = config.localSeed ?: System.currentTimeMillis()

            val pngBytes = DreamLiteNative.generateImagePng(
                modelDir = config.localModelPath,
                prompt = request.prompt,
                width = width,
                height = height,
                steps = steps,
                seed = seed
            )
            val uri = store.saveBytes(pngBytes, request.purpose)
            _state.value = ImageGenerationState.Success(uri)
            uri
        }.onFailure { error ->
            _state.value = ImageGenerationState.Error(error.message ?: "DreamLite 出图失败")
        }
    }

    private fun generateStableDiffusion(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> {
        val store = imageFileStore
        if (store == null) {
            val error = "本地 Stable Diffusion 需要 Android Context 才能保存图片"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalStateException(error))
        }
        if (request.prompt.isBlank()) {
            val error = "本地 Stable Diffusion 提示词不能为空"
            _state.value = ImageGenerationState.Error(error)
            return Result.failure(IllegalArgumentException(error))
        }

        val runtimeConfig = when (val status = StableDiffusionModelPackage.inspect(config.localModelPath)) {
            is StableDiffusionModelStatus.Ready -> status.config
            StableDiffusionModelStatus.DirectoryNotConfigured -> {
                val error = "Stable Diffusion 模型目录未配置"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is StableDiffusionModelStatus.InvalidConfig -> {
                val error = "Stable Diffusion 配置无效：${status.message}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
            is StableDiffusionModelStatus.MissingFiles -> {
                val error = "Stable Diffusion 模型文件缺失：${status.fileNames.joinToString()}"
                _state.value = ImageGenerationState.Error(error)
                return Result.failure(IllegalStateException(error))
            }
        }

        _state.value = ImageGenerationState.Generating
        return runCatching {
            val pngBytes = StableDiffusionNative.generateTxt2ImgPng(
                modelPath = runtimeConfig.modelPath,
                vaePath = runtimeConfig.vaePath,
                taesdPath = runtimeConfig.taesdPath,
                loraPaths = runtimeConfig.loraPaths.toTypedArray(),
                prompt = request.prompt,
                negativePrompt = request.negativePrompt,
                width = config.localWidth.takeIf { it > 0 } ?: runtimeConfig.defaultWidth,
                height = config.localHeight.takeIf { it > 0 } ?: runtimeConfig.defaultHeight,
                steps = config.localSteps.takeIf { it > 0 } ?: runtimeConfig.defaultSteps,
                cfgScale = config.localCfgScale.takeIf { it >= 0f } ?: runtimeConfig.defaultCfgScale,
                seed = config.localSeed ?: runtimeConfig.defaultSeed ?: -1L,
                useVulkan = config.localUseVulkan && runtimeConfig.useVulkan
            )
            val uri = store.saveBytes(pngBytes, request.purpose)
            _state.value = ImageGenerationState.Success(uri)
            uri
        }.onFailure { error ->
            _state.value = ImageGenerationState.Error(error.message ?: "本地 Stable Diffusion 出图失败")
        }
    }
}
