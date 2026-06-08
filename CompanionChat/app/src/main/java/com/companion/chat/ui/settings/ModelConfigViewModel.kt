package com.companion.chat.ui.settings

import androidx.lifecycle.ViewModel
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.engine.ModelConfig
import com.companion.chat.data.engine.ModelConfigRepository
import com.companion.chat.data.engine.ModelRuntime
import com.companion.chat.data.image.DreamLiteModelStatus
import com.companion.chat.data.image.ImageGenerationConfig
import com.companion.chat.data.image.ImageGenerationConfigRepository
import com.companion.chat.data.image.ImageGenerationProvider
import com.companion.chat.data.image.StableDiffusionModelStatus
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModelConfigUiState(
    val retainedRounds: Int,
    val modelConfig: ModelConfig,
    val imageConfig: ImageGenerationConfig,
    val dreamLiteModelStatus: DreamLiteModelStatus,
    val stableDiffusionModelStatus: StableDiffusionModelStatus,
    val resolvedModelPath: String,
    val resolvedMmprojPath: String,
    val isMmprojReady: Boolean
)

class ModelConfigViewModel(
    private val modelConfigRepository: ModelConfigRepository,
    private val contextConfigRepository: ContextConfigRepository,
    private val imageConfigRepository: ImageGenerationConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    fun setRuntime(runtime: ModelRuntime) {
        updateModelConfig(_uiState.value.modelConfig.copy(runtime = runtime, modelPath = ""))
    }

    fun updateModelPath(path: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(modelPath = path))
    }

    fun updateContextSize(value: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(contextSize = value.toIntOrNull() ?: return))
    }

    fun updateMaxTokens(value: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(maxTokens = value.toIntOrNull() ?: return))
    }

    fun updateTemperature(value: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(temperature = value.toFloatOrNull() ?: return))
    }

    fun updateTopK(value: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(topK = value.toIntOrNull() ?: return))
    }

    fun updateTopP(value: String) {
        updateModelConfig(_uiState.value.modelConfig.copy(topP = value.toFloatOrNull() ?: return))
    }

    fun updateRetainedRounds(rounds: Int) {
        contextConfigRepository.updateRetainedRounds(rounds)
        refresh()
    }

    fun setImageProvider(provider: ImageGenerationProvider) {
        val config = _uiState.value.imageConfig
        val defaultPath = imageConfigRepository.getDefaultPathForProvider(provider)
        updateImageConfig(config.copy(provider = provider, localModelPath = defaultPath))
    }

    fun updateLocalModelPath(path: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localModelPath = path))
    }

    fun updateLocalWidth(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localWidth = value.toIntOrNull() ?: return))
    }

    fun updateLocalHeight(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localHeight = value.toIntOrNull() ?: return))
    }

    fun updateLocalSteps(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localSteps = value.toIntOrNull() ?: return))
    }

    fun updateLocalCfgScale(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localCfgScale = value.toFloatOrNull() ?: return))
    }

    fun updateLocalSeed(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(localSeed = value.toLongOrNull()))
    }

    fun setLocalUseVulkan(enabled: Boolean) {
        updateImageConfig(_uiState.value.imageConfig.copy(localUseVulkan = enabled))
    }

    fun updateImageBaseUrl(baseUrl: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(baseUrl = baseUrl))
    }

    fun updateImageApiKey(apiKey: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(apiKey = apiKey))
    }

    fun updateImageModel(model: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(model = model))
    }

    fun updateRequestTemplate(template: String) {
        updateImageConfig(
            _uiState.value.imageConfig.copy(
                requestTemplate = template.ifBlank { ImageGenerationConfig.DEFAULT_REQUEST_TEMPLATE }
            )
        )
    }

    fun updateResponseImageFieldPath(path: String) {
        updateImageConfig(
            _uiState.value.imageConfig.copy(
                responseImageFieldPath = path.ifBlank { ImageGenerationConfig.DEFAULT_RESPONSE_FIELD_PATH }
            )
        )
    }

    fun updateTimeoutMillis(value: String) {
        updateImageConfig(_uiState.value.imageConfig.copy(timeoutMillis = value.toIntOrNull() ?: return))
    }

    private fun updateModelConfig(config: ModelConfig) {
        modelConfigRepository.updateConfig(config)
        refresh()
    }

    private fun updateImageConfig(config: ImageGenerationConfig) {
        imageConfigRepository.updateConfig(config)
        refresh()
    }

    private fun refresh() {
        _uiState.update { buildUiState() }
    }

    private fun buildUiState(): ModelConfigUiState {
        val modelConfig = modelConfigRepository.getConfig()
        val imageConfig = imageConfigRepository.getConfig()
        val resolvedMmprojPath = modelConfigRepository.resolveMmprojPath()
        return ModelConfigUiState(
            retainedRounds = contextConfigRepository.getSettings().retainedRounds,
            modelConfig = modelConfig,
            imageConfig = imageConfig,
            dreamLiteModelStatus = imageConfigRepository.getDreamLiteModelStatus(imageConfig),
            stableDiffusionModelStatus = imageConfigRepository.getStableDiffusionModelStatus(imageConfig),
            resolvedModelPath = modelConfigRepository.resolveModelPath(modelConfig),
            resolvedMmprojPath = resolvedMmprojPath,
            isMmprojReady = File(resolvedMmprojPath).let { it.exists() && it.canRead() && it.length() > 0L }
        )
    }
}
