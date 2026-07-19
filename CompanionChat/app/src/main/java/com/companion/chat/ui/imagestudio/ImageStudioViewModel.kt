package com.companion.chat.ui.imagestudio

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.companion.chat.AppContainer
import com.companion.chat.appContainer
import com.companion.chat.data.engine.VoiceInputEvent
import com.companion.chat.data.image.ImageGenerationPurpose
import com.companion.chat.data.image.ImageGenerationRequest
import com.companion.chat.data.image.ImageGenerationState
import com.companion.chat.data.local.entity.ImageStudioMessageEntity
import com.companion.chat.data.local.entity.RoleCard
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ImageStudioMessage(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val fullPrompt: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val errorMessage: String? = null,
    /** 引用的上一条消息 ID（引用修改持久化） */
    val referenceMessageId: String? = null
)

data class ImageStudioUiState(
    val roleCard: RoleCard? = null,
    val messages: List<ImageStudioMessage> = emptyList(),
    val inputText: String = "",
    val selectedImages: List<Uri> = emptyList(),
    val referenceMessage: ImageStudioMessage? = null,
    val galleryImages: List<String> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val isVoiceListening: Boolean = false,
    val toast: String? = null
)

sealed class ImageStudioEvent {
    data class Toast(val message: String) : ImageStudioEvent()
    data class ScrollToBottom(val animate: Boolean = true) : ImageStudioEvent()
}

class ImageStudioViewModel(
    application: Application,
    private val container: AppContainer = application.appContainer
) : AndroidViewModel(application) {

    private val roleCardRepository = container.roleCardRepository
    private val imageGenerationEngineSelector = container.imageGenerationEngineSelector
    private val imageGenerationConfigRepository = container.imageGenerationConfigRepository
    private val voiceInputEngine = container.voiceInputEngine
    private val imageStudioMessageRepository = container.imageStudioMessageRepository

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImageStudioEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ImageStudioEvent> = _events.asSharedFlow()

    private var loadedRoleId: Long = 0L

    private fun ImageStudioMessage.toEntity(roleCardId: Long, position: Int): ImageStudioMessageEntity =
        ImageStudioMessageEntity(
            id = id,
            roleCardId = roleCardId,
            prompt = prompt,
            fullPrompt = fullPrompt,
            imageUri = imageUri,
            isError = isError,
            errorMessage = errorMessage,
            referenceMessageId = referenceMessageId,
            timestamp = timestamp,
            position = position
        )

    private fun ImageStudioMessageEntity.toUiMessage(): ImageStudioMessage {
        // 从 DB 加载时，若既无图片又无错误，说明生成被中断（如应用被杀），
        // 标记为错误状态以便用户点击重试，避免永久转圈。
        val wasInterrupted = imageUri == null && !isError
        return ImageStudioMessage(
            id = id,
            prompt = prompt,
            fullPrompt = fullPrompt,
            imageUri = imageUri,
            timestamp = timestamp,
            isError = isError || wasInterrupted,
            errorMessage = errorMessage ?: if (wasInterrupted) "生成已中断，请重试" else null,
            referenceMessageId = referenceMessageId
        )
    }

    init {
        collectVoiceInputEvents()
    }

    fun loadRoleCard(roleId: Long) {
        if (roleId <= 0L) return
        loadedRoleId = roleId
        viewModelScope.launch {
            val roleCard = roleCardRepository.getRoleCard(roleId)
            val savedMessages = imageStudioMessageRepository.loadMessages(roleId).map { it.toUiMessage() }
            _uiState.update {
                it.copy(
                    roleCard = roleCard,
                    galleryImages = roleCard?.galleryImageUris ?: emptyList(),
                    messages = savedMessages
                )
            }
            logToFile("ImageStudio 加载角色卡: roleId=$roleId, name=${roleCard?.name}, gallerySize=${roleCard?.galleryImageUris?.size}, messages=${savedMessages.size}")
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun addSelectedImage(uri: Uri) {
        _uiState.update { it.copy(selectedImages = it.selectedImages + uri) }
    }

    fun removeSelectedImage(uri: Uri) {
        _uiState.update { it.copy(selectedImages = it.selectedImages - uri) }
    }

    fun setReference(message: ImageStudioMessage?) {
        _uiState.update { it.copy(referenceMessage = message) }
    }

    fun clearReference() {
        _uiState.update { it.copy(referenceMessage = null) }
    }

    /**
     * img2img: 从图片 URI 推导对应的 latents 文件路径。
     * 每次生图时 C++ 管线会保存 {image_stem}.latents.bin，
     * 引用修改时加载该文件作为 UNet 的 [noise | image_latents] 条件输入，
     * 实现真正的 edit-mode 图生图（参考图作为条件，用户指令驱动修改）。
     *
     * 返回 Pair(path, exists)：
     * - path 非空但 exists=false：参考图是早期生成的（latents 保存特性上线前），
     *   无法用于 img2img，调用方应向用户报错而不是静默回退 txt2img。
     * - path 为空：URI 不是本地 .png 文件，无法推导 latents 路径。
     */
    private fun latentsPathFromUri(imageUri: String?): Pair<String, Boolean> {
        if (imageUri.isNullOrEmpty()) return "" to false
        return try {
            val path = Uri.parse(imageUri).path ?: return "" to false
            if (!path.endsWith(".png")) return "" to false
            val latentsPath = path.removeSuffix(".png") + ".latents.bin"
            latentsPath to java.io.File(latentsPath).exists()
        } catch (e: Exception) { "" to false }
    }

    fun generateImage() {
        val state = _uiState.value
        val userInput = state.inputText.trim()
        if (userInput.isBlank() && state.selectedImages.isEmpty()) return
        if (state.isGenerating) return

        val roleCard = state.roleCard
        val referenceMessage = state.referenceMessage

        // img2img: 如果有引用消息，推导其 latents 路径传给引擎。
        // 若参考图缺少 latents 文件（早期生成的图），直接报错而非静默回退 txt2img，
        // 否则用户会困惑"为什么改图指令没生效"。
        val (referenceLatentsPath, latentsExists) = latentsPathFromUri(referenceMessage?.imageUri)
        if (referenceMessage != null && referenceMessage.imageUri != null &&
            referenceLatentsPath.isNotEmpty() && !latentsExists) {
            val errorMsg = "参考图缺少 latents 数据（早期生成的图不支持修改），请选择近期生成的图片"
            logToFile("ImageStudio img2img 失败：$errorMsg (path=$referenceLatentsPath)")
            _uiState.update {
                it.copy(
                    error = errorMsg,
                    isGenerating = false
                )
            }
            _events.tryEmit(ImageStudioEvent.ScrollToBottom())
            return
        }
        val isImg2Img = referenceLatentsPath.isNotEmpty() && latentsExists
        // strength is ignored in edit mode (C++ pipeline runs full denoising
        // from step 0 with [noise | image_latents] conditioning). Kept for
        // API compatibility — the native call still receives it but does not
        // use it to skip steps.
        val strength = 1.0f

        val fullPrompt = buildPrompt(roleCard, userInput, referenceMessage)

        val pendingMessage = ImageStudioMessage(
            prompt = userInput,
            fullPrompt = fullPrompt,
            referenceMessageId = referenceMessage?.id
        )
        _uiState.update {
            it.copy(
                messages = it.messages + pendingMessage,
                isGenerating = true,
                inputText = "",
                selectedImages = emptyList(),
                referenceMessage = null,
                error = null
            )
        }
        _events.tryEmit(ImageStudioEvent.ScrollToBottom())

        viewModelScope.launch {
            val config = imageGenerationConfigRepository.getConfig()
            logToFile("ImageStudio 生图请求: prompt=$fullPrompt, provider=${config.provider}, img2img=$isImg2Img, strength=$strength, refLatents=$referenceLatentsPath")

            // 持久化 pending 消息（先生成位置再保存，确保重启后仍可见）
            val position = imageStudioMessageRepository.nextPosition(loadedRoleId)
            imageStudioMessageRepository.saveMessage(pendingMessage.toEntity(loadedRoleId, position))

            imageGenerationEngineSelector.generate(
                request = ImageGenerationRequest(
                    prompt = fullPrompt,
                    purpose = ImageGenerationPurpose.ROLE_GALLERY,
                    referenceLatentsPath = referenceLatentsPath,
                    strength = strength
                ),
                config = config
            ).onSuccess { uri ->
                logToFile("ImageStudio 生图成功: $uri")
                imageStudioMessageRepository.updateResult(
                    id = pendingMessage.id,
                    imageUri = uri,
                    isError = false,
                    errorMessage = null
                )
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == pendingMessage.id) it.copy(imageUri = uri) else it
                        },
                        isGenerating = false
                    )
                }
                _events.tryEmit(ImageStudioEvent.ScrollToBottom())
            }.onFailure { error ->
                logToFile("ImageStudio 生图失败: ${error.message}")
                val errorMsg = error.message ?: "生图失败"
                imageStudioMessageRepository.updateResult(
                    id = pendingMessage.id,
                    imageUri = null,
                    isError = true,
                    errorMessage = errorMsg
                )
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == pendingMessage.id) {
                                it.copy(isError = true, errorMessage = errorMsg)
                            } else it
                        },
                        isGenerating = false,
                        error = error.message
                    )
                }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            imageStudioMessageRepository.deleteMessage(messageId)
        }
        _uiState.update { current ->
            current.copy(messages = current.messages.filter { it.id != messageId })
        }
    }

    fun retryMessage(messageId: String) {
        val state = _uiState.value
        val message = state.messages.find { it.id == messageId } ?: return
        _uiState.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(isError = false, errorMessage = null, imageUri = null) else it
                },
                isGenerating = true
            )
        }

        // img2img: 重试时也需要恢复引用消息的 latents 路径
        val (referenceLatentsPath, latentsExists) = message.referenceMessageId?.let { refId ->
            val refMsg = state.messages.find { it.id == refId }
            latentsPathFromUri(refMsg?.imageUri)
        } ?: ("" to false)
        val isImg2Img = referenceLatentsPath.isNotEmpty() && latentsExists
        // strength is ignored in edit mode (see generateImage() for details).
        val strength = 1.0f

        viewModelScope.launch {
            val config = imageGenerationConfigRepository.getConfig()
            logToFile("ImageStudio 重试: img2img=$isImg2Img, strength=$strength, refLatents=$referenceLatentsPath")
            imageGenerationEngineSelector.generate(
                request = ImageGenerationRequest(
                    prompt = message.fullPrompt,
                    purpose = ImageGenerationPurpose.ROLE_GALLERY,
                    referenceLatentsPath = referenceLatentsPath,
                    strength = strength
                ),
                config = config
            ).onSuccess { uri ->
                imageStudioMessageRepository.updateResult(
                    id = messageId,
                    imageUri = uri,
                    isError = false,
                    errorMessage = null
                )
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == messageId) it.copy(imageUri = uri) else it
                        },
                        isGenerating = false
                    )
                }
            }.onFailure { error ->
                val errorMsg = error.message ?: "重试失败"
                imageStudioMessageRepository.updateResult(
                    id = messageId,
                    imageUri = null,
                    isError = true,
                    errorMessage = errorMsg
                )
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == messageId) {
                                it.copy(isError = true, errorMessage = errorMsg)
                            } else it
                        },
                        isGenerating = false
                    )
                }
            }
        }
    }

    fun saveToGallery(imageUri: String) {
        if (loadedRoleId <= 0L) return
        viewModelScope.launch {
            val success = roleCardRepository.addGalleryImage(loadedRoleId, imageUri)
            if (success) {
                val updated = roleCardRepository.getRoleCard(loadedRoleId)
                _uiState.update {
                    it.copy(galleryImages = updated?.galleryImageUris ?: emptyList())
                }
                _events.tryEmit(ImageStudioEvent.Toast("已保存到角色形象"))
                logToFile("ImageStudio 保存到画廊: $imageUri")
            } else {
                _events.tryEmit(ImageStudioEvent.Toast("保存失败"))
            }
        }
    }

    fun deleteGalleryImage(imageUri: String) {
        if (loadedRoleId <= 0L) return
        viewModelScope.launch {
            val success = roleCardRepository.removeGalleryImage(loadedRoleId, imageUri)
            if (success) {
                val updated = roleCardRepository.getRoleCard(loadedRoleId)
                _uiState.update {
                    it.copy(galleryImages = updated?.galleryImageUris ?: emptyList())
                }
                _events.tryEmit(ImageStudioEvent.Toast("已删除"))
                logToFile("ImageStudio 从画廊删除: $imageUri")
            }
        }
    }

    fun addUploadedImageToGallery(uri: Uri) {
        saveToGallery(uri.toString())
    }

    fun toggleVoiceListening() {
        val state = _uiState.value
        if (state.isVoiceListening) {
            voiceInputEngine.stopListening()
        } else {
            voiceInputEngine.startListening()
        }
    }

    private fun collectVoiceInputEvents() {
        viewModelScope.launch {
            voiceInputEngine.events.collectLatest { event ->
                when (event) {
                    is VoiceInputEvent.Listening -> {
                        _uiState.update { it.copy(isVoiceListening = true) }
                    }
                    is VoiceInputEvent.NotListening -> {
                        _uiState.update { it.copy(isVoiceListening = false) }
                    }
                    is VoiceInputEvent.FinalResult -> {
                        _uiState.update {
                            it.copy(
                                inputText = it.inputText + event.text,
                                isVoiceListening = false
                            )
                        }
                    }
                    is VoiceInputEvent.Error -> {
                        _uiState.update { it.copy(isVoiceListening = false) }
                        _events.tryEmit(ImageStudioEvent.Toast(event.message))
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun buildPrompt(
        roleCard: RoleCard?,
        userInput: String,
        reference: ImageStudioMessage?
    ): String {
        val stylePrefix = roleCard?.imageStylePrompt?.takeIf { it.isNotBlank() }
            ?: "anime style, 2d illustration, vibrant colors, detailed, masterpiece"

        // NOTE: persona (Chinese character description) is intentionally NOT injected
        // into the image prompt. It changes text embeddings and reduces image contrast
        // (std 70.68 -> 55.77, causing the "grayish" output). Image generation should
        // only use imageStylePrompt + user input for visual consistency.
        return if (reference != null) {
            // img2img (real edit mode): C++ pipeline uses "[Edit]: A diptych...
            // Compared to the right side, the left one has {prompt}" template.
            // The {prompt} slot should contain ONLY the user's modification
            // instruction (e.g. "变成全身照"). The reference image's content is
            // fed to the UNet via [noise | image_latents] conditioning, NOT via
            // text — so we must NOT prepend refPrompt/stylePrefix here. Doing so
            // would lock the composition (e.g. "portrait" overrides "full body")
            // and contradict the user's instruction. Pass userInput as-is.
            userInput.take(300)
        } else {
            val cappedInput = userInput.take(300)
            buildString {
                append(stylePrefix)
                append(", ")
                append(cappedInput)
            }
        }
    }

    private fun logToFile(message: String) {
        runCatching {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            getApplication<Application>().openFileOutput("viewmodel_log.txt", android.content.Context.MODE_APPEND).use { output ->
                output.write("[$time] $message\n".toByteArray())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputEngine.stopListening()
    }
}
