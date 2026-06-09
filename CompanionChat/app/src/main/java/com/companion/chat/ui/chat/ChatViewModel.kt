package com.companion.chat.ui.chat

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.companion.chat.AppContainer
import com.companion.chat.appContainer
import com.companion.chat.companion.CompanionRuntime
import com.companion.chat.companion.CompanionTurnEvent
import com.companion.chat.companion.PreferenceLearningCoordinator
import com.companion.chat.companion.PreferenceLearningAdapter
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.context.ContextManager
import com.companion.chat.data.context.DefaultContextManager
import com.companion.chat.data.context.ContextSettings
import com.companion.chat.data.embedding.OnnxEmbeddingEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.engine.VoiceInputEvent
import com.companion.chat.data.engine.VoiceOutputState
import com.companion.chat.data.image.ImageGenerationPurpose
import com.companion.chat.data.image.ImageGenerationRequest
import com.companion.chat.data.image.ImageGenerationState
import com.companion.chat.data.engine.TtsQueueMode
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.ConversationSession
import com.companion.chat.data.model.DEFAULT_SESSION_TITLE
import com.companion.chat.data.model.DEFAULT_WELCOME_MESSAGE
import com.companion.chat.data.model.MessageRole
import com.companion.chat.data.model.createDefaultSession
import com.companion.chat.data.preferences.SecondEngineManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.voice.VoiceOutputSettingsRepository
import com.companion.chat.engine.RoleAwareVoiceOutputEngine
import com.companion.chat.engine.TtsFallbackEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DateFilter { ALL, TODAY, YESTERDAY, WEEK, MONTH }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedImages: List<Uri> = emptyList(),
    val isGenerating: Boolean = false,
    val isVoiceStarting: Boolean = false,
    val isVoiceListening: Boolean = false,
    val isVoiceWarmedUp: Boolean = false,
    val isVoiceSpeaking: Boolean = false,
    val isVoiceAutoSending: Boolean = false,
    val voiceInputError: String = "",
    val lastVoiceTranscript: String = "",
    val imageGenerationState: ImageGenerationState = ImageGenerationState.Idle,
    val imageGenerationError: String = "",
    val engineState: InferenceState = InferenceState.Idle,
    val showVoicePermissionDialog: Boolean = false,
    val diagnosticLog: String = "",
    val sessions: List<ConversationSession> = emptyList(),
    val currentSessionId: String = "",
    val showSessionDrawer: Boolean = false,
    val sessionSearchQuery: String = "",
    val dateFilter: DateFilter = DateFilter.ALL,
    val editingSessionId: String = "",
    val editingTitle: String = "",
    val availableRoleCards: List<RoleCard> = emptyList(),
    val assistantAvatarUri: String = "",
    val isCompressingContext: Boolean = false,
    val compressionMessage: String = ""
) {
    val hasSpeakableAssistantMessage: Boolean
        get() = messages.any { message ->
            message.role == MessageRole.ASSISTANT &&
                !message.isStreaming &&
                message.content.isNotBlank()
        }
}

/** UI 一次性事件 */
sealed class ChatUiEvent {
    data class ShowToast(val message: String, val duration: Int = 3000) : ChatUiEvent()
}

class ChatViewModel(
    application: Application,
    private val container: AppContainer = application.appContainer
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentRoleCardId: Long?
        get() = _uiState.value.sessions
            .firstOrNull { it.id == _uiState.value.currentSessionId }
            ?.roleCardId

    /** 一次性 UI 事件流 */
    private val _events = MutableSharedFlow<ChatUiEvent>()
    val events: SharedFlow<ChatUiEvent> = _events.asSharedFlow()

    private val modelConfigRepository = container.modelConfigRepository
    private val inferenceEngineFactory = container.inferenceEngineFactory
    var inferenceEngine = inferenceEngineFactory.create(modelConfigRepository.getConfig().runtime)
        private set
    val voiceInputEngine = container.voiceInputEngine
    private val contextConfigRepository = container.contextConfigRepository
    private val contextManager: ContextManager by lazy {
        val baseManager = container.contextManager
        if (baseManager is DefaultContextManager) {
            baseManager.withLlmSummary()
        } else {
            baseManager
        }
    }
    private val promptAssembler = container.promptAssembler
    private val sessionRepository = container.chatSessionRepository
    private val memoryRepository = container.memoryRepository
    private val preferenceRepository = container.preferenceRepository
    private val roleCardRepository = container.roleCardRepository
    val voiceOutputEngine = container.voiceOutputEngine
    private val imageGenerationConfigRepository = container.imageGenerationConfigRepository
    private val imageGenerationEngine = container.imageGenerationEngine
    private val imageGenerationEngineSelector = container.imageGenerationEngineSelector
    private val preferenceMemoryDeriver = container.preferenceMemoryDeriver
    private val unifiedExtractionPromptBuilder = container.unifiedExtractionPromptBuilder
    private val unifiedExtractionParser = container.unifiedExtractionParser
    private val voiceOutputSettingsRepository: VoiceOutputSettingsRepository = container.voiceOutputSettingsRepository
    private val secondEngineManager = SecondEngineManager(
        primaryEngineStateProvider = { inferenceEngine.state.value },
        engineFactory = { inferenceEngineFactory.create(modelConfigRepository.getConfig().runtime) },
        timeoutMillis = STAGE4_SUMMARY_TIMEOUT_MILLIS
    )
    private val preferenceLearningCoordinator = PreferenceLearningCoordinator(
        scope = viewModelScope,
        contextConfigRepository = contextConfigRepository,
        memoryRepository = memoryRepository,
        preferenceRepository = preferenceRepository,
        preferenceMemoryDeriver = preferenceMemoryDeriver,
        unifiedExtractionPromptBuilder = unifiedExtractionPromptBuilder,
        unifiedExtractionParser = unifiedExtractionParser,
        secondEngineManager = secondEngineManager,
        engineStateProvider = { inferenceEngine.state.value },
        currentEngineConfigProvider = { inferenceEngine.getCurrentConfig() },
        baseSystemPromptProvider = { baseSystemPrompt },
        logger = ::logToFile
    )
    private val companionRuntime = CompanionRuntime(
        roleCardRepository = roleCardRepository,
        skillRepository = container.skillRepository,
        preferenceRepository = preferenceRepository,
        memoryRepository = memoryRepository,
        userProfileRepository = container.userProfileRepository,
        contextManager = contextManager,
        inferenceEngineProvider = { inferenceEngine },
        postTurnLearning = PreferenceLearningAdapter(preferenceLearningCoordinator),
        promptAssembler = promptAssembler,
        memoryPromptBuilder = container.memoryPromptBuilder,
        roleCardPromptBuilder = container.roleCardPromptBuilder
    )
    private var contextSettings: ContextSettings = ContextConfigRepository.DEFAULT_SETTINGS
    private var baseSystemPrompt: String = DEFAULT_BASE_SYSTEM_PROMPT

    private var generateJob: Job? = null
    private var voiceCollectJob: Job? = null
    private var inferenceStateJob: Job? = null
    private var shouldSpeakNextAssistantResponse = false

    // ── Streaming auto-TTS state ──
    private var autoTtsActive = false
    private var autoTtsSpokenEnd = 0
    private var autoTtsInitialDelayJob: Job? = null
    private var autoTtsReady = false

    init {
        logToFile("=== ChatViewModel 创建 ===")
        collectInferenceState()
        collectVoiceEvents()
        collectVoiceOutputState()
        collectImageGenerationState()
        collectTtsFallbackEvents()
        loadContextSettings()
        loadSessionsFromStorage()
        loadAvailableRoles()
        voiceInputEngine.warmUp()
        initializeEmbeddingEngine()

        viewModelScope.launch {
            refreshBaseSystemPrompt()
            logToFile("ChatViewModel 初始化完成，开始自动初始化引擎")
            initializeEngine(systemPrompt = baseSystemPrompt)
        }
    }

    private fun initializeEmbeddingEngine() {
        viewModelScope.launch {
            try {
                val engine = container.embeddingEngine
                engine.initialize(
                    OnnxEmbeddingEngine.DEFAULT_MODEL_PATH,
                    OnnxEmbeddingEngine.DEFAULT_VOCAB_PATH
                )
                logToFile("嵌入引擎初始化完成")
            } catch (e: Exception) {
                logToFile("嵌入引擎初始化失败: ${e.message}")
            }
        }
    }

    private fun logToFile(msg: String) {
        try {
            val app = getApplication<Application>()
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$time] $msg"
            app.openFileOutput("viewmodel_log.txt", Context.MODE_APPEND).use { fos ->
                fos.write("$line\n".toByteArray())
            }
            _uiState.update { it.copy(diagnosticLog = it.diagnosticLog + line + "\n") }
        } catch (e: Exception) {
            try {
                val app = getApplication<Application>()
                app.openFileOutput("viewmodel_log.txt", Context.MODE_PRIVATE).use { fos ->
                    fos.write("LOG_INIT_ERROR: ${e.message}\n".toByteArray())
                }
                _uiState.update { it.copy(diagnosticLog = it.diagnosticLog + "LOG_INIT_ERROR: ${e.message}\n") }
            } catch (_: Exception) {}
        }
    }

    private fun loadContextSettings() {
        contextSettings = contextConfigRepository.getSettings()
        logToFile(
            "上下文设置已加载: retainedRounds=${contextSettings.retainedRounds}, " +
                "compressionBuffer=${contextSettings.compressionBuffer}"
        )
    }

    private suspend fun refreshBaseSystemPrompt() {
        baseSystemPrompt = companionRuntime.refreshBasePrompt()
    }

    fun refreshSystemPromptOnResume() {
        viewModelScope.launch {
            refreshBaseSystemPrompt()
            logToFile("系统提示已刷新（页面恢复），准备重建对话上下文")
            rebuildConversationForPromptChange(reason = "页面恢复后刷新用户信息")
        }
    }

    internal fun debugBaseSystemPrompt(): String = baseSystemPrompt

    private fun collectInferenceState() {
        inferenceStateJob?.cancel()
        inferenceStateJob = viewModelScope.launch {
            inferenceEngine.state.collectLatest { state ->
                _uiState.update { it.copy(engineState = state) }
                if (state is InferenceState.Idle) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private fun collectVoiceEvents() {
        voiceCollectJob = viewModelScope.launch {
            voiceInputEngine.events.collectLatest { event ->
                when (event) {
                    is VoiceInputEvent.WarmedUp -> {
                        _uiState.update { it.copy(isVoiceWarmedUp = true) }
                    }
                    is VoiceInputEvent.PartialResult -> {
                        _uiState.update { it.copy(inputText = event.text, voiceInputError = "") }
                    }
                    is VoiceInputEvent.FinalResult -> {
                        val transcript = event.text.trim()
                        _uiState.update {
                            it.copy(
                                inputText = transcript,
                                isVoiceStarting = false,
                                isVoiceListening = false,
                                voiceInputError = "",
                                lastVoiceTranscript = transcript
                            )
                        }
                        handleVoiceTranscript(transcript)
                    }
                    is VoiceInputEvent.Listening -> {
                        _uiState.update {
                            it.copy(
                                isVoiceStarting = false,
                                isVoiceListening = true,
                                voiceInputError = ""
                            )
                        }
                    }
                    is VoiceInputEvent.NotListening -> {
                        _uiState.update { it.copy(isVoiceStarting = false, isVoiceListening = false) }
                    }
                    is VoiceInputEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isVoiceStarting = false,
                                isVoiceListening = false,
                                voiceInputError = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun collectVoiceOutputState() {
        viewModelScope.launch {
            voiceOutputEngine.state.collectLatest { state ->
                _uiState.update {
                    it.copy(isVoiceSpeaking = state is VoiceOutputState.Speaking)
                }
            }
        }
    }

    /** 监听 TTS 回退事件，发送 Toast 提示 */
    private fun collectTtsFallbackEvents() {
        viewModelScope.launch {
            // 需要将 voiceOutputEngine 转换为 RoleAwareVoiceOutputEngine 才能访问 fallbackEvents
            val engine = voiceOutputEngine
            if (engine is RoleAwareVoiceOutputEngine) {
                engine.fallbackEvents.collect { event ->
                    when (event) {
                        is TtsFallbackEvent.FallbackToSystem -> {
                            logToFile("TTS 回退: ${event.reason}")
                            _events.tryEmit(ChatUiEvent.ShowToast("检测到 MOSS 延迟过大，已回退到系统 TTS"))
                        }
                    }
                }
            }
        }
    }

    private fun collectImageGenerationState() {
        viewModelScope.launch {
            imageGenerationEngineSelector.state.collectLatest { state ->
                _uiState.update {
                    it.copy(
                        imageGenerationState = state,
                        imageGenerationError = (state as? ImageGenerationState.Error)?.message.orEmpty()
                    )
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun addImage(uri: Uri) {
        _uiState.update { it.copy(selectedImages = it.selectedImages + uri) }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { it.copy(selectedImages = it.selectedImages - uri) }
    }

    fun generateChatSceneImage(prompt: String) {
        viewModelScope.launch {
            val resolvedPrompt = if (prompt.isNotBlank()) {
                prompt
            } else {
                buildImagePromptWithLLM()
            }
            logToFile("图片生成请求: promptLength=${resolvedPrompt.length}, prompt=$resolvedPrompt")
            imageGenerationEngineSelector.generate(
                request = ImageGenerationRequest(
                    prompt = resolvedPrompt,
                    purpose = ImageGenerationPurpose.CHAT_SCENE
                ),
                config = imageGenerationConfigRepository.getConfig()
            ).onSuccess { uri ->
                logToFile("图片生成成功: $uri")
                // Ensure a session exists so the image message can be persisted
                if (_uiState.value.currentSessionId.isBlank()) {
                    val newSession = createDefaultSession().copy(messages = emptyList())
                    _uiState.update {
                        it.copy(
                            sessions = listOf(newSession) + it.sessions,
                            currentSessionId = newSession.id
                        )
                    }
                    persistSession(newSession)
                }
                // Add the generated image as an assistant message (image only, no text)
                val imageMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "",
                    images = listOf(Uri.parse(uri))
                )
                _uiState.update {
                    it.copy(messages = it.messages + imageMessage)
                }
                // Persist the session so the image survives app restarts
                saveCurrentSession()
            }.onFailure { error ->
                logToFile("图片生成失败: ${error.message}")
                _uiState.update {
                    it.copy(
                        imageGenerationState = ImageGenerationState.Error(error.message ?: "图片生成失败"),
                        imageGenerationError = error.message ?: "图片生成失败"
                    )
                }
            }
        }
    }

    /**
     * Uses the text LLM to generate a short visual scene description based on
     * the recent conversation, then prepends a fixed anime style prefix.
     */
    private suspend fun buildImagePromptWithLLM(): String {
        val animeStylePrefix = "anime style, 2d illustration, vibrant colors, detailed, masterpiece"
        val fallbackPrompt = "$animeStylePrefix, a warm anime companion chat scene"

        val messages = _uiState.value.messages
        val lastUserIndex = messages.indexOfLast {
            it.role == MessageRole.USER && it.content.isNotBlank()
        }
        if (lastUserIndex < 0) return fallbackPrompt

        // Collect conversation context: user's last message + all assistant replies after it
        val userContent = messages[lastUserIndex].content
        val assistantReplies = messages
            .drop(lastUserIndex + 1)
            .filter { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() && !it.isStreaming }
            .map { it.content }

        val conversationSummary = buildString {
            append("用户说: $userContent")
            if (assistantReplies.isNotEmpty()) {
                append("\n助手回复: ${assistantReplies.joinToString(" ")}")
            }
        }

        // Ask the LLM to write a scene description
        val sceneDescriptionRequest = ChatMessage(
            role = MessageRole.USER,
            content = """请用一段简短的英文描述当前的对话场景，用于生成一张二次元风格的插图。
只需要描述：场景环境、人物正在做什么、画面视角和氛围。
不要包含对话内容，不要解释，直接输出英文场景描述，控制在50个单词以内。

当前对话内容：
$conversationSummary"""
        )

        val engineState = inferenceEngine.state.value
        if (engineState !is InferenceState.Ready) {
            logToFile("LLM 未就绪，使用 fallback 图像提示词")
            return "$animeStylePrefix, ${userContent.take(80)}"
        }

        return try {
            val sceneDescription = StringBuilder()
            inferenceEngine.sendMessageStream(listOf(sceneDescriptionRequest))
                .collect { token ->
                    sceneDescription.append(token)
                }
            val description = sceneDescription.toString().trim().take(200)
            logToFile("LLM 生成场景描述: $description")
            if (description.isNotBlank()) {
                "$animeStylePrefix, $description"
            } else {
                "$animeStylePrefix, ${userContent.take(80)}"
            }
        } catch (e: Exception) {
            logToFile("LLM 场景描述生成失败: ${e.message}，使用 fallback")
            "$animeStylePrefix, ${userContent.take(80)}"
        }
    }

    fun sendMessage() {
        submitCurrentMessage(autoSpeakResponse = false)
    }

    /**
     * Sends a "continue" prompt to let the AI append more to its last reply.
     * Does NOT add a visible user message — only triggers backend inference.
     */
    fun sendContinueMessage() {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            val messages = _uiState.value.messages

            // Find the latest user message
            val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER && it.content.isNotBlank() }
            val userContent = if (lastUserIndex >= 0) messages[lastUserIndex].content else ""

            // Collect all assistant replies after that user message (full content, no truncation)
            val previousReplies = if (lastUserIndex >= 0) {
                messages.drop(lastUserIndex + 1)
                    .filter { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() && !it.isStreaming }
                    .map { it.content }
            } else {
                emptyList()
            }

            val lastReply = previousReplies.lastOrNull().orEmpty()

            val continuePrompt = buildString {
                if (userContent.isNotBlank()) {
                    append("【用户的问题】\n$userContent\n\n")
                }
                if (previousReplies.isNotEmpty()) {
                    append("【你之前已经回复了以下内容】\n")
                    previousReplies.forEach { reply ->
                        append("${reply.trim()}\n\n")
                    }
                    append("【请你现在紧接着上面最后一段回复继续往下写，不要重复已有的内容，不要重新开始，直接从上次停下的地方继续。\n" +
                        "最后一段是：${lastReply.takeLast(80)}】")
                } else {
                    append("请继续刚才的话题，补充更多内容。")
                }
            }

            // Don't show user message — just add streaming assistant placeholder
            val assistantPlaceholder = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + assistantPlaceholder,
                    isGenerating = true
                )
            }
            generateJob?.cancel()
            shouldSpeakNextAssistantResponse = false
            generateJob = viewModelScope.launch {
                generateResponse(continuePrompt)
            }
        }
    }

    /**
     * Generates an image depicting the current conversation scene.
     */
    fun generateCurrentSceneImage() {
        generateChatSceneImage("")
    }

    private fun handleVoiceTranscript(transcript: String) {
        when (
            val decision = VoiceDrivenChatPolicy.evaluateTranscript(
                transcript = transcript,
                isGenerating = _uiState.value.isGenerating,
                isEngineReady = inferenceEngine.state.value is InferenceState.Ready
            )
        ) {
            VoiceTranscriptDecision.AutoSend -> {
                submitCurrentMessage(autoSpeakResponse = true)
            }
            is VoiceTranscriptDecision.HoldForUser -> {
                _uiState.update {
                    it.copy(
                        voiceInputError = decision.message,
                        isVoiceAutoSending = false
                    )
                }
            }
        }
    }

    private fun submitCurrentMessage(autoSpeakResponse: Boolean) {
        if (!autoSpeakResponse) {
            shouldSpeakNextAssistantResponse = false
        }
        var state = _uiState.value
        if (state.inputText.isBlank() && state.selectedImages.isEmpty()) {
            _uiState.update { it.copy(isVoiceAutoSending = false) }
            return
        }
        if (state.isGenerating) {
            _uiState.update { it.copy(isVoiceAutoSending = false) }
            return
        }
        companionRuntime.cancelPostTurnLearning()
        stopAutoTts()

        if (state.currentSessionId.isBlank()) {
            val newSession = ConversationSession(messages = emptyList())
            _uiState.update {
                it.copy(
                    sessions = listOf(newSession) + it.sessions,
                    currentSessionId = newSession.id,
                    messages = emptyList()
                )
            }
            persistSession(newSession)
            state = _uiState.value
        }

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = state.inputText.trim(),
            images = state.selectedImages.toList()
        )

        val assistantPlaceholder = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + assistantPlaceholder,
                inputText = "",
                selectedImages = emptyList(),
                isGenerating = true,
                isVoiceAutoSending = autoSpeakResponse
            )
        }
        saveCurrentSession()

        generateJob?.cancel()
        shouldSpeakNextAssistantResponse = autoSpeakResponse
        generateJob = viewModelScope.launch {
            if (!contextConfigRepository.getAutoPreferenceLearningEnabled()) {
                storeRuleBasedMemoriesForMessage(userMessage)
            }
            generateResponse(userMessage.content.trim())
        }
    }

    private suspend fun generateResponse(userInput: String) {
        val engineState = inferenceEngine.state.value
        if (engineState !is InferenceState.Ready) {
            updateAssistantMessage("模型未加载，请在设置中配置模型路径。")
            return
        }

        // Start auto-TTS (will be delayed 0.5s before first sentence is spoken)
        startAutoTts()

        try {
            val messages = _uiState.value.messages
            val memoryContext = buildMemoryContext(userInput)
            contextSettings = contextConfigRepository.getSettings()
            companionRuntime.runTurn(
                messages = messages,
                baseSystemPrompt = baseSystemPrompt,
                settings = contextSettings,
                userPreferences = memoryContext.confirmedPreferencePrompt,
                persistentMemoryPrompt = memoryContext.persistentPrompt,
                memoryPrompt = memoryContext.retrievedPrompt
            ).collect { event ->
                when (event) {
                    is CompanionTurnEvent.AssistantToken -> appendAssistantToken(event.token)
                }
            }
        } catch (e: Exception) {
            updateAssistantMessage("推理出错: ${e.message}")
        } finally {
            finishStreaming()
        }
    }

    private suspend fun buildMemoryContext(userInput: String): MemoryContext {
        return try {
            val confirmedPreferencePrompt = buildConfirmedPreferencePrompt()
            val companionMemoryContext = companionRuntime.buildMemoryContext(userInput, currentRoleCardId)
            val persistentPrompt = companionMemoryContext.persistentPrompt
            val memoryPrompt = companionMemoryContext.retrievedPrompt
            if (confirmedPreferencePrompt.isNotBlank()) {
                logToFile("confirmed 偏好注入: count=${preferenceRepository.getConfirmedPreferences().size}")
            }
            if (persistentPrompt.isNotBlank()) {
                logToFile("常驻长期记忆注入: count=${companionMemoryContext.persistentMemoryCount}")
            }
            if (memoryPrompt.isNotBlank()) {
                logToFile(
                    "动态记忆检索成功: count=${companionMemoryContext.retrievedMemoryCount}, " +
                        "query=${userInput.trim()}"
                )
            } else {
                logToFile("动态记忆检索为空: query=${userInput.trim()}")
            }
            MemoryContext(
                confirmedPreferencePrompt = confirmedPreferencePrompt,
                persistentPrompt = persistentPrompt,
                retrievedPrompt = memoryPrompt
            )
        } catch (e: Exception) {
            logToFile("发送前记忆检索失败: ${e.message}")
            MemoryContext()
        }
    }

    private data class MemoryContext(
        val confirmedPreferencePrompt: String = "",
        val persistentPrompt: String = "",
        val retrievedPrompt: String = ""
    )

    private suspend fun storeRuleBasedMemoriesForMessage(userMessage: ChatMessage) {
        try {
            if (userMessage.content.isBlank()) {
                return
            }
            val sessionId = _uiState.value.currentSessionId.ifBlank { return }
            val insertedMemories = memoryRepository.extractAndStoreMemories(
                userMessage = userMessage.content,
                sessionId = sessionId,
                roleCardId = currentRoleCardId
            )
            if (insertedMemories.isNotEmpty()) {
                logToFile("规则兜底记忆写入成功: count=${insertedMemories.size}")
            }
        } catch (e: Exception) {
            logToFile("规则兜底记忆写入失败: ${e.message}")
        }
    }

    private fun appendAssistantToken(token: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val lastIndex = updatedMessages.lastIndex
            if (lastIndex >= 0 && updatedMessages[lastIndex].isStreaming) {
                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                    content = updatedMessages[lastIndex].content + token
                )
            }
            state.copy(messages = updatedMessages)
        }

        // ── 流式TTS已禁用：改为生成完成后一次性播放 ──
        // 原逻辑：每检测到一句就调用 voiceOutputEngine.speak()
        // 新逻辑：等待 finishAutoTts() 在生成完成后统一播放
    }

    /** Find the index of the last sentence-ending character in [text]. Returns 0 if none found. */
    private fun findLastSentenceEnd(text: String): Int {
        var lastEnd = 0
        for (i in text.indices) {
            if (text[i] in SENTENCE_DELIMITERS) {
                lastEnd = i + 1
            }
        }
        return lastEnd
    }

    /** Start auto-TTS for the current streaming response. */
    private fun startAutoTts() {
        val autoPlay = voiceOutputSettingsRepository.getSettings().autoPlayTts
        if (!autoPlay) return

        autoTtsActive = true
        autoTtsSpokenEnd = 0
        autoTtsReady = false

        // After 0.5s delay, start speaking completed sentences
        autoTtsInitialDelayJob?.cancel()
        autoTtsInitialDelayJob = viewModelScope.launch {
            delay(500L)
            autoTtsReady = true
            val content = _uiState.value.messages.lastOrNull()?.content ?: return@launch
            val lastEnd = findLastSentenceEnd(content)
            if (lastEnd > 0) {
                val sentence = content.substring(0, lastEnd).trim()
                if (sentence.isNotEmpty()) {
                    voiceOutputEngine.speak(sentence, queueMode = TtsQueueMode.FLUSH)
                }
                autoTtsSpokenEnd = lastEnd
            }
        }
    }

    /** Finish auto-TTS: speak any remaining unsaid text. */
    private fun finishAutoTts() {
        if (!autoTtsActive) return
        autoTtsInitialDelayJob?.cancel()
        autoTtsInitialDelayJob = null

        val content = _uiState.value.messages.lastOrNull()?.content ?: ""
        val remaining = content.substring(autoTtsSpokenEnd).trim()
        if (remaining.isNotEmpty()) {
            viewModelScope.launch {
                voiceOutputEngine.speak(
                    remaining,
                    queueMode = if (autoTtsReady) TtsQueueMode.ADD else TtsQueueMode.FLUSH
                )
            }
        }

        autoTtsActive = false
        autoTtsReady = false
        autoTtsSpokenEnd = 0
    }

    /** Stop auto-TTS immediately (user cancelled or new message sent). */
    private fun stopAutoTts() {
        autoTtsInitialDelayJob?.cancel()
        autoTtsInitialDelayJob = null
        autoTtsActive = false
        autoTtsReady = false
        autoTtsSpokenEnd = 0
        voiceOutputEngine.stop()
    }

    private fun updateAssistantMessage(content: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val lastIndex = updatedMessages.lastIndex
            if (lastIndex >= 0 && updatedMessages[lastIndex].isStreaming) {
                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                    content = content,
                    isStreaming = false
                )
            }
            state.copy(messages = updatedMessages, isGenerating = false, isVoiceAutoSending = false)
        }
    }

    private fun finishStreaming() {
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val lastIndex = updatedMessages.lastIndex
            if (lastIndex >= 0 && updatedMessages[lastIndex].isStreaming) {
                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                    isStreaming = false
                )
            }
            state.copy(messages = updatedMessages, isGenerating = false, isVoiceAutoSending = false)
        }

        if (autoTtsActive) {
            // Auto-TTS was streaming — speak the remaining unsaid portion
            finishAutoTts()
        } else if (shouldSpeakNextAssistantResponse) {
            // Voice-input auto-speak fallback (when auto-play setting is off)
            val lastMessage = _uiState.value.messages.lastOrNull()
            if (lastMessage?.role == MessageRole.ASSISTANT && lastMessage.content.isNotBlank()) {
                speakMessage(lastMessage.content)
            }
        }
        shouldSpeakNextAssistantResponse = false

        saveCurrentSession()
        schedulePreferenceSummaryAfterDelay()
    }

    fun toggleVoiceListening() {
        if (_uiState.value.isVoiceListening || _uiState.value.isVoiceStarting) {
            voiceInputEngine.stopListening()
            _uiState.update {
                it.copy(
                    isVoiceStarting = false,
                    isVoiceListening = false,
                    showVoicePermissionDialog = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isVoiceStarting = true,
                    voiceInputError = "",
                    showVoicePermissionDialog = true
                )
            }
        }
    }

    fun onVoicePermissionGranted() {
        _uiState.update { it.copy(showVoicePermissionDialog = false) }
        voiceInputEngine.startListening()
    }

    fun onVoicePermissionDenied() {
        _uiState.update {
            it.copy(
                isVoiceStarting = false,
                showVoicePermissionDialog = false,
                voiceInputError = "缺少录音权限，无法使用语音输入"
            )
        }
    }

    fun clearVoiceInputError() {
        _uiState.update { it.copy(voiceInputError = "") }
    }

    fun speakMessage(text: String) {
        viewModelScope.launch {
            voiceOutputEngine.speak(text)
        }
    }

    fun speakLatestAssistantMessage() {
        val latestAssistantMessage = _uiState.value.messages.lastOrNull { message ->
            message.role == MessageRole.ASSISTANT &&
                !message.isStreaming &&
                message.content.isNotBlank()
        } ?: return

        speakMessage(latestAssistantMessage.content)
    }

    fun stopSpeaking() {
        voiceOutputEngine.stop()
    }

    fun cancelGeneration() {
        generateJob?.cancel()
        inferenceEngine.cancel()
        companionRuntime.cancelPostTurnLearning()
        shouldSpeakNextAssistantResponse = false
        stopAutoTts()
        _uiState.update { it.copy(isGenerating = false, isVoiceAutoSending = false) }
    }

    fun initializeEngine(modelPath: String = "", systemPrompt: String = "") {
        viewModelScope.launch {
            try {
                if (inferenceEngine.state.value is InferenceState.Generating) {
                    generateJob?.cancel()
                    inferenceEngine.cancel()
                    companionRuntime.cancelPostTurnLearning()
                    _uiState.update { it.copy(isGenerating = false) }
                    logToFile("模型配置变更: 已取消当前生成并准备重建引擎")
                }

                val app = getApplication<Application>()
                val modelConfig = modelConfigRepository.getConfig()
                val actualPath = modelPath.ifBlank { modelConfigRepository.resolveModelPath(modelConfig) }
                val file = java.io.File(actualPath)

                logToFile("getExternalFilesDir('models') = ${app.getExternalFilesDir("models")?.absolutePath}")
                logToFile("filesDir = ${app.filesDir.absolutePath}")
                logToFile("模型运行时 = ${modelConfig.runtime}")
                logToFile("实际模型路径 = $actualPath")
                logToFile("文件存在 = ${file.exists()}")
                logToFile("文件大小 = ${file.length()} bytes")

                app.getExternalFilesDir("models")?.listFiles()?.forEach { f ->
                    logToFile("models目录: ${f.name} (${f.length()} bytes)")
                }

                val resolvedSystemPrompt = systemPrompt.ifBlank {
                    baseSystemPrompt.ifBlank { DEFAULT_BASE_SYSTEM_PROMPT }
                }
                baseSystemPrompt = resolvedSystemPrompt
                val config = modelConfigRepository.toEngineConfig(
                    systemPrompt = resolvedSystemPrompt
                ).copy(modelPath = actualPath)
                if (config.runtime != inferenceEngine.getCurrentConfig()?.runtime) {
                    logToFile("切换模型运行时: ${inferenceEngine.getCurrentConfig()?.runtime} -> ${config.runtime}")
                    inferenceEngine.release()
                    inferenceEngine = inferenceEngineFactory.create(config.runtime)
                    collectInferenceState()
                }
                logToFile("开始调用 engine.initialize...")
                inferenceEngine.initialize(config)
                logToFile("engine.initialize 返回, state = ${inferenceEngine.state.value}")
            } catch (e: Exception) {
                logToFile("!!! initializeEngine 异常 !!! ${e.javaClass.simpleName}: ${e.message}")
                _uiState.update {
                    it.copy(engineState = InferenceState.Error("初始化异常: ${e.message}"))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generateJob?.cancel()
        voiceCollectJob?.cancel()
        inferenceStateJob?.cancel()
        companionRuntime.release()
        inferenceEngine.release()
        voiceInputEngine.release()
        voiceOutputEngine.release()
    }

    fun toggleSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = !it.showSessionDrawer) }
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = false, sessionSearchQuery = "") }
    }

    fun updateSessionSearchQuery(query: String) {
        _uiState.update { it.copy(sessionSearchQuery = query) }
    }

    fun createNewSession() {
        if (_uiState.value.currentSessionId.isNotBlank()) {
            triggerPreferenceSummaryNow(reason = "新建会话前")
            saveCurrentSession()
        }
        val newSession = createDefaultSession()
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions,
                currentSessionId = newSession.id,
                messages = newSession.messages,
                showSessionDrawer = false,
                sessionSearchQuery = ""
            )
        }
        persistSession(newSession)
        loadAvailableRoles()
    }

    suspend fun startRoleConversation(roleId: Long) {
        if (_uiState.value.currentSessionId.isNotBlank()) {
            triggerPreferenceSummaryNow(reason = "角色对话前")
            saveCurrentSession()
        }
        roleCardRepository.activateRoleCard(roleId)
        val roleCard = roleCardRepository.getRoleCard(roleId)
        val avatarUri = roleCard?.avatarImageUri.orEmpty()
        refreshBaseSystemPrompt()

        // Check if this role already has a conversation
        val existingSession = sessionRepository.getSessionByRoleCardId(roleId)
        if (existingSession != null) {
            _uiState.update {
                it.copy(
                    currentSessionId = existingSession.id,
                    messages = existingSession.messages,
                    assistantAvatarUri = avatarUri,
                    showSessionDrawer = false,
                    sessionSearchQuery = "",
                    inputText = "",
                    selectedImages = emptyList(),
                    sessions = it.sessions.toMutableList().apply {
                        // Move existing session to top if not already there
                        val idx = indexOfFirst { s -> s.id == existingSession.id }
                        if (idx > 0) {
                            removeAt(idx)
                            add(0, existingSession)
                        } else if (idx < 0) {
                            add(0, existingSession)
                        }
                    }
                )
            }
            rebuildConversationForPromptChange(reason = "切换到已有角色对话")
            loadAvailableRoles()
            return
        }

        val openingMessage = roleCard?.openingMessage
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WELCOME_MESSAGE
        val now = System.currentTimeMillis()
        val newSession = ConversationSession(
            title = roleCard?.name?.takeIf { it.isNotBlank() } ?: DEFAULT_SESSION_TITLE,
            roleCardId = roleId,
            messages = listOf(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = openingMessage,
                    timestamp = now
                )
            ),
            createdAt = now,
            updatedAt = now
        )
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions,
                currentSessionId = newSession.id,
                messages = newSession.messages,
                assistantAvatarUri = avatarUri,
                showSessionDrawer = false,
                sessionSearchQuery = "",
                inputText = "",
                selectedImages = emptyList()
            )
        }
        persistSession(newSession)
        rebuildConversationForPromptChange(reason = "角色对话开始")
        loadAvailableRoles()
    }

    fun launchStartRoleConversation(roleId: Long) {
        viewModelScope.launch { startRoleConversation(roleId) }
    }

    fun createRoleCardAndStartChat(
        name: String,
        description: String,
        avatar: String,
        persona: String,
        speakingStyle: String,
        background: String,
        rules: String,
        taboos: String,
        openingMessage: String,
        exampleDialogue: String,
        avatarImageUri: String,
        galleryImageUris: List<String>,
        imageStylePrompt: String,
        voiceProfileUri: String,
        voiceMode: String,
        voiceDisplayName: String
    ) {
        viewModelScope.launch {
            try {
                val roleId = roleCardRepository.createRoleCard(
                    name = name,
                    description = description,
                    avatar = avatar,
                    persona = persona,
                    speakingStyle = speakingStyle,
                    background = background,
                    rules = rules,
                    taboos = taboos,
                    openingMessage = openingMessage,
                    exampleDialogue = exampleDialogue,
                    avatarImageUri = avatarImageUri,
                    galleryImageUris = galleryImageUris,
                    imageStylePrompt = imageStylePrompt,
                    voiceProfileUri = voiceProfileUri,
                    voiceMode = voiceMode,
                    voiceDisplayName = voiceDisplayName
                )
                logToFile("角色卡创建成功: id=$roleId, name=$name")
                loadAvailableRoles()
                startRoleConversation(roleId)
            } catch (e: Exception) {
                logToFile("角色卡创建失败: ${e.message}")
            }
        }
    }

    fun setDateFilter(filter: DateFilter) {
        _uiState.update { it.copy(dateFilter = filter) }
    }

    fun startEditingTitle(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        _uiState.update { it.copy(editingSessionId = sessionId, editingTitle = session.title) }
    }

    fun updateEditingTitle(title: String) {
        _uiState.update { it.copy(editingTitle = title) }
    }

    fun confirmEditingTitle() {
        val state = _uiState.value
        if (state.editingSessionId.isBlank()) return
        val newTitle = state.editingTitle.trim().ifBlank { DEFAULT_SESSION_TITLE }
        val updatedSessions = state.sessions.map { session ->
            if (session.id == state.editingSessionId) {
                session.copy(title = newTitle)
            } else {
                session
            }
        }
        _uiState.update {
            it.copy(
                sessions = updatedSessions,
                editingSessionId = "",
                editingTitle = ""
            )
        }
        updatedSessions.firstOrNull { it.id == state.editingSessionId }?.let(::persistSession)
    }

    fun cancelEditingTitle() {
        _uiState.update { it.copy(editingSessionId = "", editingTitle = "") }
    }

    fun switchToSession(sessionId: String) {
        val state = _uiState.value
        if (sessionId == state.currentSessionId) {
            _uiState.update { it.copy(showSessionDrawer = false, sessionSearchQuery = "") }
            return
        }
        triggerPreferenceSummaryNow(
            reason = "切换会话",
            sessionId = state.currentSessionId,
            messages = state.messages
        )
        saveCurrentSession()
        val session = state.sessions.find { it.id == sessionId } ?: return
        val avatarUri = if (session.roleCardId != null) {
            viewModelScope.launch {
                val roleCard = roleCardRepository.getRoleCard(session.roleCardId)
                _uiState.update { it.copy(assistantAvatarUri = roleCard?.avatarImageUri.orEmpty()) }
            }
            "" // Will be updated asynchronously
        } else ""
        _uiState.update {
            it.copy(
                currentSessionId = sessionId,
                messages = session.messages,
                assistantAvatarUri = avatarUri,
                showSessionDrawer = false,
                sessionSearchQuery = "",
                inputText = "",
                selectedImages = emptyList()
            )
        }
    }

    fun deleteSession(sessionId: String) {
        val state = _uiState.value
        val remainingSessions = state.sessions.filterNot { it.id == sessionId }
        val nextSession = if (state.currentSessionId == sessionId) {
            remainingSessions.firstOrNull()
        } else {
            state.sessions.firstOrNull { it.id == state.currentSessionId }
        }

        _uiState.update {
            it.copy(
                sessions = remainingSessions,
                currentSessionId = nextSession?.id.orEmpty(),
                messages = nextSession?.messages ?: emptyList(),
                showSessionDrawer = false,
                sessionSearchQuery = "",
                editingSessionId = if (it.editingSessionId == sessionId) "" else it.editingSessionId,
                editingTitle = if (it.editingSessionId == sessionId) "" else it.editingTitle
            )
        }

        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                logToFile("删除会话失败: ${e.message}")
            }
        }
    }

    private fun saveCurrentSession() {
        val state = _uiState.value
        if (state.currentSessionId.isBlank()) return
        val filteredMessages = state.messages.filter {
            it.content != DEFAULT_WELCOME_MESSAGE || it.role != MessageRole.ASSISTANT
        }
        val title = filteredMessages.firstOrNull { it.role == MessageRole.USER }?.content?.take(20)
            ?: state.sessions.firstOrNull { it.id == state.currentSessionId }?.title
            ?: DEFAULT_SESSION_TITLE
        val updatedAt = System.currentTimeMillis()
        val updatedSessions = state.sessions.map { session ->
            if (session.id == state.currentSessionId) {
                session.copy(title = title, messages = state.messages, updatedAt = updatedAt)
            } else {
                session
            }
        }
        _uiState.update { it.copy(sessions = updatedSessions) }
        updatedSessions.firstOrNull { it.id == state.currentSessionId }?.let(::persistSession)
    }

    private fun loadSessionsFromStorage() {
        viewModelScope.launch {
            try {
                sessionRepository.ensureInitialized()
                val sessions = sessionRepository.getAllSessions()
                val existing = sessions.firstOrNull()
                if (existing != null) {
                    val avatarUri = if (existing.roleCardId != null) {
                        roleCardRepository.getRoleCard(existing.roleCardId)?.avatarImageUri.orEmpty()
                    } else ""
                    _uiState.update {
                        it.copy(
                            sessions = sessions,
                            messages = existing.messages,
                            currentSessionId = existing.id,
                            assistantAvatarUri = avatarUri
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            sessions = emptyList(),
                            currentSessionId = "",
                            messages = emptyList(),
                            assistantAvatarUri = ""
                        )
                    }
                }
            } catch (e: Exception) {
                logToFile("加载会话列表失败: ${e.message}")
                _uiState.update {
                    it.copy(
                        sessions = emptyList(),
                        currentSessionId = "",
                        messages = emptyList(),
                        assistantAvatarUri = ""
                    )
                }
            }
        }
    }

    fun loadAvailableRoles() {
        viewModelScope.launch {
            try {
                val roles = roleCardRepository.getAllRoleCards()
                _uiState.update { it.copy(availableRoleCards = roles) }
            } catch (e: Exception) {
                logToFile("加载角色卡列表失败: ${e.message}")
            }
        }
    }

    private fun persistSession(session: ConversationSession) {
        viewModelScope.launch {
            try {
                sessionRepository.replaceSession(session)
            } catch (e: Exception) {
                logToFile("保存会话列表失败: ${e.message}")
            }
        }
    }

    fun onAppBackgrounded() {
        triggerPreferenceSummaryNow(reason = "应用进入后台")
    }

    private fun schedulePreferenceSummaryAfterDelay() {
        companionRuntime.onTurnFinished(
            sessionIdProvider = { _uiState.value.currentSessionId },
            messagesProvider = { _uiState.value.messages }
        )
    }

    private fun triggerPreferenceSummaryNow(
        reason: String,
        sessionId: String = _uiState.value.currentSessionId,
        messages: List<ChatMessage> = _uiState.value.messages
    ) {
        companionRuntime.onConversationBoundary(
            reason = reason,
            sessionId = sessionId,
            messages = messages
        )
    }

    private suspend fun buildConfirmedPreferencePrompt(): String {
        return companionRuntime.buildConfirmedPreferencePrompt()
    }

    private suspend fun rebuildConversationWithContext(
        stableMessages: List<ChatMessage>,
        userPreferences: String,
        persistentMemoryPrompt: String,
        memoryPrompt: String,
        forceRebuild: Boolean,
        reason: String
    ) {
        contextSettings = contextConfigRepository.getSettings()
        val shouldCompress = stableMessages.size > contextSettings.compressionThreshold

        if (shouldCompress) {
            _uiState.update {
                it.copy(
                    isCompressingContext = true,
                    compressionMessage = "正在压缩上下文，请稍候..."
                )
            }
        }

        try {
            val rebuildResult = companionRuntime.rebuildConversationWithContext(
                stableMessages = stableMessages,
                baseSystemPrompt = baseSystemPrompt,
                settings = contextSettings,
                userPreferences = userPreferences,
                persistentMemoryPrompt = persistentMemoryPrompt,
                memoryPrompt = memoryPrompt,
                forceRebuild = forceRebuild
            )

            if (!rebuildResult.rebuildAttempted) {
                logToFile(
                    "发送前上下文检查: 未触发压缩, " +
                        "messageCount=${stableMessages.size}, threshold=${contextSettings.compressionThreshold}, " +
                        "contextInjected=false"
                )
                return
            }

            logToFile(
                "$reason: recentMessages=${rebuildResult.recentMessageCount}, " +
                    "summaryEmpty=${rebuildResult.historySummaryEmpty}, " +
                    "preferenceInjected=${rebuildResult.preferenceInjected}, " +
                    "persistentMemoryInjected=${rebuildResult.persistentMemoryInjected}, " +
                    "memoryInjected=${rebuildResult.memoryInjected}"
            )

            if (rebuildResult.rebuildSucceeded == false) {
                logToFile("$reason: Conversation 重建失败")
                return
            }

            if (rebuildResult.replaySucceeded == true) {
                logToFile("$reason: 最近消息回放成功")
            } else if (rebuildResult.replaySucceeded == false && rebuildResult.fallbackSucceeded == true) {
                logToFile("$reason: 最近消息回放失败，降级摘要注入成功")
            } else if (rebuildResult.replaySucceeded == false) {
                logToFile("$reason: 最近消息回放失败，降级摘要注入失败")
            }
        } finally {
            if (shouldCompress) {
                _uiState.update {
                    it.copy(
                        isCompressingContext = false,
                        compressionMessage = ""
                    )
                }
            }
        }
    }

    suspend fun activateRoleCard(roleId: Long) {
        baseSystemPrompt = companionRuntime.activateRoleCardAndRefreshPrompt(roleId)
        rebuildConversationForPromptChange(reason = "角色卡切换")
    }

    suspend fun activateSkill(skillId: Long) {
        baseSystemPrompt = companionRuntime.activateSkillAndRefreshPrompt(skillId)
        rebuildConversationForPromptChange(reason = "Skill 切换")
    }

    private suspend fun rebuildConversationForPromptChange(reason: String) {
        if (inferenceEngine.state.value is InferenceState.Generating) {
            logToFile("$reason: 当前正在生成，暂不重建 Conversation")
            return
        }
        if (inferenceEngine.getCurrentConfig() == null) {
            logToFile("$reason: 引擎尚未初始化，已仅更新基础 prompt")
            return
        }

        val stableMessages = _uiState.value.messages
            .filterNot { it.isStreaming }
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        val latestUserInput = stableMessages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        if (latestUserInput.isBlank()) {
            val rebuildSucceeded = inferenceEngine.rebuildConversation(baseSystemPrompt)
            if (rebuildSucceeded) {
                logToFile("$reason: 无用户消息，已仅使用基础 prompt 重建 Conversation")
            } else {
                logToFile("$reason: 无用户消息，基础 prompt 重建 Conversation 失败")
            }
            return
        }
        val memoryContext = buildMemoryContext(latestUserInput)

        rebuildConversationWithContext(
            stableMessages = stableMessages,
            userPreferences = memoryContext.confirmedPreferencePrompt,
            persistentMemoryPrompt = memoryContext.persistentPrompt,
            memoryPrompt = memoryContext.retrievedPrompt,
            forceRebuild = true,
            reason = reason
        )
    }

    companion object {
        private const val DEFAULT_BASE_SYSTEM_PROMPT =
            CompanionRuntime.DEFAULT_BASE_PROMPT
        private const val STAGE4_SUMMARY_TIMEOUT_MILLIS = 90_000L

        /** Characters that mark the end of a speakable sentence. */
        private val SENTENCE_DELIMITERS = setOf(
            '。', '！', '？', '；',
            '.', '!', '?', ';',
            '\n'
        )
    }
}
