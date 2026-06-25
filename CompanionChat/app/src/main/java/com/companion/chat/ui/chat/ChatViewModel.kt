package com.companion.chat.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.companion.chat.AppContainer
import com.companion.chat.appContainer
import com.companion.chat.companion.CompanionRuntime
import com.companion.chat.companion.CompanionTurnEvent
import com.companion.chat.companion.PreferenceLearningCoordinator
import com.companion.chat.companion.PreferenceLearningAdapter
import com.companion.chat.data.memory.MemoryExtractLoop
import com.companion.chat.locale.AppLanguage
import com.companion.chat.data.context.ContextConfigRepository
import com.companion.chat.data.context.ContextManager
import com.companion.chat.data.context.DefaultContextManager
import com.companion.chat.data.context.ContextSettings
import com.companion.chat.data.embedding.OnnxEmbeddingEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.service.InferenceForegroundService
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
import com.companion.chat.data.util.ImagePersistenceUtil
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
import com.companion.chat.locale.StringsKey

enum class DateFilter { ALL, TODAY, YESTERDAY, WEEK, MONTH }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val inputHint: String = "",
    val selectedImages: List<Uri> = emptyList(),
    val isGenerating: Boolean = false,
    val isSuggesting: Boolean = false,
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

    /** 语言文案 helper：从持久化读取当前语言，取对应翻译。面向用户的字符串用 [tr]。 */
    private val languageRepo = com.companion.chat.locale.LanguageRepository(application)
    private fun tr(key: com.companion.chat.locale.StringsKey, vararg args: Any): String =
        com.companion.chat.locale.Strings.get(languageRepo.getLanguage(), key, *args)

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
        memoryExtractLoop = container.memoryExtractLoop,
        engineStateProvider = { inferenceEngine.state.value },
        currentEngineConfigProvider = { inferenceEngine.getCurrentConfig() },
        baseSystemPromptProvider = { baseSystemPrompt },
        logger = ::logToFile,
        roleCardIdProvider = { currentRoleCardId }
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
        roleCardPromptBuilder = container.roleCardPromptBuilder,
        appLanguage = languageRepo.getLanguage()
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
            _uiState.update { it.copy(diagnosticLog = (it.diagnosticLog + line + "\n").let { if (it.count { c -> c == '\n' } > 200) it.substringAfter("\n") else it }) }
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

    private var inferenceForegroundServiceStarted = false

    private fun startInferenceForegroundService() {
        if (inferenceForegroundServiceStarted) return
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, InferenceForegroundService::class.java).apply {
                action = InferenceForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
            inferenceForegroundServiceStarted = true
            logToFile("推理前台服务已启动")
        } catch (e: Exception) {
            logToFile("启动推理前台服务失败: ${e.message}")
        }
    }

    private fun looksLikeKnowledgeQuery(message: String): Boolean {
        val msg = message.trim()
        return msg.contains("什么是") || msg.contains("怎么") || msg.contains("如何") ||
            msg.contains("为什么") || msg.contains("是什么") || msg.contains("定理") ||
            msg.contains("公式") || msg.contains("定义") || msg.contains("原理")
    }

    /** 判断最近一条用户消息是否为知识问答 */
    private fun lastUserMessageIsKnowledgeQuery(): Boolean {
        val lastUser = _uiState.value.messages.lastOrNull { it.role == MessageRole.USER }
        return lastUser != null && looksLikeKnowledgeQuery(lastUser.content)
    }

    /** 自动拆分知识回答和情感承接：找到最后一个情感转折点 */
    private fun splitKnowledgeAndEmotion(content: String): Pair<String, String>? {
        val pivotKeywords = listOf("你对", "你最近", "说起来", "不过", "话说", "换个话题", "还是说", "是不是")
        for (keyword in pivotKeywords) {
            val idx = content.lastIndexOf(keyword)
            if (idx > content.length / 3) {
                val knowledge = content.substring(0, idx).trim()
                val emotion = content.substring(idx).trim()
                if (knowledge.length > 10 && emotion.length > 5) {
                    return knowledge to emotion
                }
            }
        }
        return null
    }

    /** 过滤AI回复中的内部机制标记，不让用户看到 */
    private fun stripInternalMarkers(content: String): String {
        return content
            .replace(Regex("[（(]内心切换[^）)]*[）)]"), "")
            .replace(Regex("[（(]切换回[^）)]*[）)]"), "")
            .replace(Regex("[（(]分支[^）)]*[）)]"), "")
            .replace(Regex("[（(]模式[^）)]*[）)]"), "")
            .replace(Regex("[（(]任务模式[^）)]*[）)]"), "")
            .replace(Regex("[（(]陪伴模式[^）)]*[）)]"), "")
            .trim()
    }

    private fun collectInferenceState() {
        inferenceStateJob?.cancel()
        inferenceStateJob = viewModelScope.launch {
            inferenceEngine.state.collectLatest { state ->
                _uiState.update { it.copy(engineState = state) }
                if (state is InferenceState.Idle) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
                if (state is InferenceState.Ready) {
                    startInferenceForegroundService()
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
                            _events.tryEmit(ChatUiEvent.ShowToast(tr(StringsKey.toast_moss_fallback)))
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
                        imageGenerationState = ImageGenerationState.Error(error.message ?: tr(StringsKey.snackbar_image_failed)),
                        imageGenerationError = error.message ?: tr(StringsKey.snackbar_image_failed)
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
            val activeRoleName = currentRoleCardId?.let { rid ->
                state.availableRoleCards.firstOrNull { it.id == rid }?.name
            }
            val newSession = ConversationSession(
                title = activeRoleName?.takeIf { it.isNotBlank() } ?: DEFAULT_SESSION_TITLE,
                roleCardId = currentRoleCardId,
                messages = emptyList()
            )
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

        // 持久化图片到私有目录
        val persistedImages = if (state.selectedImages.isNotEmpty()) {
            val context = getApplication<Application>()
            ImagePersistenceUtil.persistImages(context, state.selectedImages)
        } else {
            emptyList()
        }

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = state.inputText.trim(),
            images = persistedImages
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
            // 改造后：移除规则即时提取，交由阶段四 LLM 统一提取
            generateResponse(userMessage.content.trim())
        }
    }

    private suspend fun generateResponse(userInput: String) {
        val engineState = inferenceEngine.state.value
        if (engineState !is InferenceState.Ready) {
            updateAssistantMessage(tr(StringsKey.err_model_not_loaded))
            return
        }

        // Start auto-TTS (will be delayed 0.5s before first sentence is spoken)
        startAutoTts()

        try {
            // 内在分支：知识问答用独立知识agent单轮回答，再让陪伴agent做情感承接
            if (looksLikeKnowledgeQuery(userInput)) {
                logToFile("内在分支: 检测到知识查询, 启动两阶段推理")
                // 阶段一：知识agent单轮回答（纯知识prompt，不注入记忆和偏好）
                val knowledgePrompt = "你是一个简洁准确的知识助手。请用简洁的中文回答以下问题，只回答事实，不要加任何情感或闲聊：\n$userInput"
                val knowledgeResult = StringBuilder()
                inferenceEngine.sendMessageStream(
                    listOf(ChatMessage(role = MessageRole.USER, content = knowledgePrompt))
                ).collect { token ->
                    appendAssistantToken(token)
                    knowledgeResult.append(token)
                }
                finishStreamingForBranch()
                logToFile("内在分支: 知识回答完成: ${knowledgeResult.toString().take(80)}")

                // 重建Conversation恢复陪伴上下文（清除知识agent的上下文污染）
                val messages = _uiState.value.messages
                val memoryContext = buildMemoryContext(userInput)
                contextSettings = contextConfigRepository.getSettings()
                val rebuildResult = companionRuntime.rebuildConversationWithContext(
                    stableMessages = messages.filterNot { it.isStreaming },
                    baseSystemPrompt = baseSystemPrompt,
                    settings = contextSettings,
                    userPreferences = memoryContext.confirmedPreferencePrompt,
                    persistentMemoryPrompt = memoryContext.persistentPrompt,
                    memoryPrompt = memoryContext.retrievedPrompt,
                    forceRebuild = true
                )
                logToFile("内在分支: Conversation重建: ${if (rebuildResult.rebuildSucceeded == true) "成功" else "失败"}")

                // 阶段二：陪伴agent读取上下文做情感承接
                val emotionPrompt = "用户刚才突然问了一个知识问题（$userInput），你刚才已经给出了准确回答。现在请用一句简短温暖的话自然地回到陪伴对话，不要重复知识内容，只要一句情感承接即可。"
                val emotionPlaceholder = ChatMessage(role = MessageRole.ASSISTANT, content = "", isStreaming = true)
                _uiState.update { it.copy(messages = it.messages + emotionPlaceholder, isGenerating = true) }
                val emotionResult = StringBuilder()
                inferenceEngine.sendMessageStream(
                    listOf(ChatMessage(role = MessageRole.USER, content = emotionPrompt))
                ).collect { token ->
                    appendAssistantToken(token)
                    emotionResult.append(token)
                }
                logToFile("内在分支: 情感承接完成: ${emotionResult.toString().take(80)}")
            } else {
                // 正常陪伴对话
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
            }
        } catch (e: Exception) {
            updateAssistantMessage(tr(StringsKey.err_inference, e.message ?: ""))
        } finally {
            finishStreaming()
        }
    }

    private suspend fun buildMemoryContext(userInput: String): MemoryContext {
        return try {
            val confirmedPreferencePrompt = buildConfirmedPreferencePrompt(currentRoleCardId)
            val companionMemoryContext = companionRuntime.buildMemoryContext(userInput, currentRoleCardId)
            val persistentPrompt = companionMemoryContext.persistentPrompt
            val memoryPrompt = companionMemoryContext.retrievedPrompt
            if (confirmedPreferencePrompt.isNotBlank()) {
                logToFile("confirmed 偏好注入: count=${
            if (currentRoleCardId != null) preferenceRepository.getConfirmedPreferencesForRole(roleCardId = currentRoleCardId).size
            else preferenceRepository.getConfirmedPreferences().size
        }.size}")
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


    private fun appendAssistantToken(token: String) {
        // 过滤内部机制标记，不让用户看到
        val cleanToken = token
            .replace(Regex("[（(]内心切换[^）)]*[）)]"), "")
            .replace(Regex("[（(]切换回[^）)]*[）)]"), "")
            .replace(Regex("[（(]分支[^）)]*[）)]"), "")
            .replace(Regex("[（(]模式[^）)]*[）)]"), "")
        if (cleanToken.isBlank()) return
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

    /** 内在分支中间步骤的finish：结束streaming但不触发偏好学习等后续逻辑 */
    private fun finishStreamingForBranch() {
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val lastIndex = updatedMessages.lastIndex
            if (lastIndex >= 0 && updatedMessages[lastIndex].isStreaming) {
                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(isStreaming = false)
            }
            state.copy(messages = updatedMessages, isGenerating = false, isVoiceAutoSending = false)
        }
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
                val lastMessage = updatedMessages[lastIndex]
                val cleanContent = stripInternalMarkers(lastMessage.content)

                // 内在分支拆分：按 || 分隔符拆为知识回答 + 情感承接两条消息
                if (cleanContent.contains("||")) {
                    val parts = cleanContent.split("||", limit = 2)
                    val knowledgePart = parts[0].trim()
                    val emotionPart = parts.getOrNull(1)?.trim().orEmpty()
                    // 第一条：知识回答
                    updatedMessages[lastIndex] = lastMessage.copy(isStreaming = false, content = knowledgePart)
                    // 第二条：情感承接
                    if (emotionPart.isNotBlank()) {
                        updatedMessages.add(ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = emotionPart
                        ))
                    }
                    logToFile("内在分支拆分(||): 知识=${knowledgePart.take(50)}, 情感=${emotionPart.take(50)}")
                } else if (lastUserMessageIsKnowledgeQuery()) {
                    // fallback：用户问了知识问题但AI没用||，尝试按情感转折点拆分
                    val splitResult = splitKnowledgeAndEmotion(cleanContent)
                    if (splitResult != null) {
                        updatedMessages[lastIndex] = lastMessage.copy(isStreaming = false, content = splitResult.first)
                        updatedMessages.add(ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = splitResult.second
                        ))
                        logToFile("内在分支拆分(自动): 知识=${splitResult.first.take(50)}, 情感=${splitResult.second.take(50)}")
                    } else {
                        updatedMessages[lastIndex] = lastMessage.copy(isStreaming = false, content = cleanContent)
                    }
                } else {
                    updatedMessages[lastIndex] = lastMessage.copy(isStreaming = false, content = cleanContent)
                }

                // 如果是建议消息，将内容复制到输入框并删除建议消息
                if (lastMessage.isSuggestion) {
                    val suggestionContent = lastMessage.content
                    // 删除建议消息（用户消息和助手消息）
                    val suggestionMessages = updatedMessages.filter { it.isSuggestion }
                    updatedMessages.removeAll(suggestionMessages)
                    return@update state.copy(
                        messages = updatedMessages,
                        inputText = suggestionContent,
                        inputHint = tr(StringsKey.hint_suggestion_ready),
                        isGenerating = false,
                        isVoiceAutoSending = false
                    )
                }
            }
            state.copy(messages = updatedMessages, isGenerating = false, isVoiceAutoSending = false)
        }

        // 记录AI回复日志和分支检测
        val lastAssistantMsg = _uiState.value.messages.lastOrNull()
        if (lastAssistantMsg?.role == MessageRole.ASSISTANT && lastAssistantMsg.content.isNotBlank()) {
            val reply = lastAssistantMsg.content.take(200)
            logToFile("AI回复: $reply")
            // 检测内在分支：用户从陪伴转向知识问答
            val lastUserMsg = _uiState.value.messages.takeLast(5)
                .lastOrNull { it.role == MessageRole.USER }
            if (lastUserMsg != null && looksLikeKnowledgeQuery(lastUserMsg.content)) {
                logToFile("内在分支触发: 用户从陪伴对话转向知识查询, 已内在处理并自然回归")
            }
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
                voiceInputError = tr(StringsKey.toast_record_permission_denied)
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
                    it.copy(engineState = InferenceState.Error(tr(StringsKey.err_init_exception, e.message ?: "")))
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

    private suspend fun buildConfirmedPreferencePrompt(roleCardId: Long? = null): String {
        return companionRuntime.buildConfirmedPreferencePrompt(roleCardId)
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
                    compressionMessage = tr(StringsKey.msg_compressing_context)
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

    // ── 对话建议功能 ──

    private var isGeneratingSuggestion = false

    /**
     * 生成对话建议
     * 将建议请求作为普通消息发送，但隐藏建议消息，只将建议结果显示在输入框
     */
    fun generateSuggestion() {
        if (isGeneratingSuggestion) return
        if (_uiState.value.isGenerating) return

        val engineState = inferenceEngine.state.value
        if (engineState !is InferenceState.Ready) {
            logToFile("对话建议: 引擎未就绪, state=$engineState")
            return
        }

        isGeneratingSuggestion = true
        _uiState.update {
            it.copy(
                isSuggesting = true,
                inputText = "",
                inputHint = tr(StringsKey.hint_suggestion_loading)
            )
        }

        viewModelScope.launch {
            try {
                // 找到最近一次用户发送的对话
                val recentMessages = _uiState.value.messages.takeLast(20)
                val lastUserMessage = recentMessages.lastOrNull { msg -> msg.role == MessageRole.USER }

                if (lastUserMessage == null) {
                    resetSuggestionState()
                    logToFile("对话建议: 没有找到用户消息")
                    return@launch
                }

                // 构建提示词
                val prompt = buildSuggestionPrompt(recentMessages, lastUserMessage)
                logToFile("对话建议: 开始生成，prompt=${prompt.take(100)}")

                // 使用现有的对话机制发送建议请求
                // 将建议消息添加到消息列表，但标记为建议类型
                val suggestionUserMessage = ChatMessage(
                    role = MessageRole.USER,
                    content = prompt,
                    isSuggestion = true
                )
                val suggestionAssistantPlaceholder = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "",
                    isStreaming = true,
                    isSuggestion = true
                )

                _uiState.update {
                    it.copy(
                        messages = it.messages + suggestionUserMessage + suggestionAssistantPlaceholder
                    )
                }

                // 使用 generateResponse 生成建议
                generateResponse(prompt)
            } catch (e: Exception) {
                logToFile("对话建议生成失败: ${e.message}")
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        inputText = "",
                        inputHint = tr(StringsKey.hint_suggestion_failed)
                    )
                }
            } finally {
                isGeneratingSuggestion = false
                _uiState.update { it.copy(isSuggesting = false) }
            }
        }
    }

    private fun resetSuggestionState() {
        isGeneratingSuggestion = false
        _uiState.update {
            it.copy(
                isSuggesting = false,
                isGenerating = false,
                inputText = "",
                inputHint = tr(StringsKey.hint_input_msg)
            )
        }
    }

    /**
     * 构建对话建议提示词
     */
    private fun buildSuggestionPrompt(
        recentMessages: List<ChatMessage>,
        lastUserMessage: ChatMessage
    ): String {
        val conversationHistory = recentMessages.takeLast(10).joinToString("\n") { msg ->
            val role = if (msg.role == MessageRole.USER) "用户" else "助手"
            "$role: ${msg.content.take(150)}"
        }

        return """根据对话历史，帮用户想一个简短的回复建议（20字以内），让对话能继续深入或打开新话题。

对话历史:
$conversationHistory

用户最后说: ${lastUserMessage.content.take(100)}

直接给出建议内容，不要解释："""
    }

    companion object {
        private val DEFAULT_BASE_SYSTEM_PROMPT =
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
