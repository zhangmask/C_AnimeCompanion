package com.companion.chat.data.image

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ImageGenerationEngineSelector(
    private val httpEngine: ImageGenerationEngine,
    private val localEngine: ImageGenerationEngine
) {
    private val _state = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    val state: StateFlow<ImageGenerationState> = _state.asStateFlow()

    suspend fun generate(
        request: ImageGenerationRequest,
        config: ImageGenerationConfig
    ): Result<String> {
        val provider = chooseProvider(config)
        _state.value = ImageGenerationState.Generating
        val result = when (provider) {
            ImageGenerationProvider.HTTP -> httpEngine.generate(request, config.copy(provider = provider))
            ImageGenerationProvider.LOCAL_DREAMLITE,
            ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP ->
                localEngine.generate(request, config.copy(provider = provider))
        }
        _state.value = result.fold(
            onSuccess = { ImageGenerationState.Success(it) },
            onFailure = { ImageGenerationState.Error(it.message ?: "图片生成失败") }
        )
        return result
    }

    fun chooseProvider(config: ImageGenerationConfig): ImageGenerationProvider {
        if (
            config.provider == ImageGenerationProvider.LOCAL_DREAMLITE ||
            config.provider == ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP
        ) {
            return config.provider
        }
        return if (config.baseUrl.isNotBlank()) ImageGenerationProvider.HTTP else config.provider
    }
}
