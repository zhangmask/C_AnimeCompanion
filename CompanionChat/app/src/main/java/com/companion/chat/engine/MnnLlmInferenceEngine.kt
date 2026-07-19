package com.companion.chat.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.companion.chat.data.engine.DefaultModelConfig
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MnnLlmInferenceEngine(private val context: Context) : InferenceEngine {
    companion object {
        private const val TAG = "MnnLlmEngine"
        private val StopMarkers = listOf(
            "<|im_end|>",
            "<|endoftext|>"
        )
        private val RemovableMarkers = listOf(
            "<|im_start|>",
            "system",
            "user",
            "assistant",
            "\n\n\n"
        )
    }

    private val runtimeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mnn-llm-runtime").apply { isDaemon = true }
        }
    private val runtimeDispatcher: CoroutineDispatcher = runtimeExecutor.asCoroutineDispatcher()

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    override val state: StateFlow<InferenceState> = _state.asStateFlow()

    private var handle: Long = 0L
    private var currentConfig: EngineConfig? = null

    private fun logToFile(msg: String) {
        try {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$time] $msg\n"
            context.openFileOutput("mnn_llm_engine_log.txt", Context.MODE_APPEND).use { fos ->
                fos.write(line.toByteArray())
            }
            Log.i(TAG, msg)
        } catch (e: Exception) {
            Log.e(TAG, "写日志失败: ${e.message}")
        }
    }

    private fun defaultModelDir(): String {
        val externalDir = context.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
        return if (externalDir != null) {
            File(externalDir, "${DefaultModelConfig.MnnModelDir}/${DefaultModelConfig.MnnModel0_8B}").absolutePath
        } else {
            File(File(context.filesDir, DefaultModelConfig.ExternalModelsDir), "${DefaultModelConfig.MnnModelDir}/${DefaultModelConfig.MnnModel0_8B}").absolutePath
        }
    }

    private fun readImageBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (error: Exception) {
            logToFile("读取图片失败: $uri, error=${error.message}")
            null
        }
    }

    override suspend fun initialize(config: EngineConfig) = withContext(runtimeDispatcher) {
        val resolvedConfig = config.copy(
            modelPath = config.modelPath.ifBlank { defaultModelDir() },
            systemPrompt = config.systemPrompt.ifBlank { DefaultModelConfig.DefaultSystemPrompt }
        )
        if (_state.value is InferenceState.Ready && currentConfig == resolvedConfig && handle != 0L) {
            return@withContext
        }

        _state.value = InferenceState.Initializing
        releaseLoadedModel()

        MnnLlmJni.ensureLoaded()
        if (!MnnLlmJni.isLoaded()) {
            val message = "MNN LLM 原生库加载失败"
            logToFile(message)
            _state.value = InferenceState.Error(message)
            return@withContext
        }

        val modelDir = File(resolvedConfig.modelPath)
        val configFile = File(modelDir, "config.json")
        logToFile("=== 开始初始化 MNN LLM 引擎 ===")
        logToFile("模型目录: ${modelDir.absolutePath}")
        logToFile("config.json 存在: ${configFile.exists()}")

        when {
            !modelDir.exists() -> {
                val message = "MNN 模型目录不存在: ${modelDir.absolutePath}"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
            !configFile.exists() -> {
                val message = "MNN config.json 不存在: ${configFile.absolutePath}"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
        }

        try {
            handle = MnnLlmJni.nativeCreate(configFile.absolutePath)
            if (handle == 0L) {
                val message = "MNN 模型加载失败，返回空 handle"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
            currentConfig = resolvedConfig
            _state.value = InferenceState.Ready
            logToFile("=== MNN LLM 引擎初始化完成，状态: Ready ===")
        } catch (e: Exception) {
            releaseLoadedModel()
            val message = "MNN 模型初始化失败: ${e.message}"
            logToFile("!!! $message")
            logToFile("异常类型: ${e.javaClass.simpleName}")
            logToFile("堆栈: ${e.stackTraceToString().take(1000)}")
            _state.value = InferenceState.Error(message)
        }
    }

    override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        if (handle == 0L) {
            close(IllegalStateException("MNN 引擎未初始化"))
            return@callbackFlow
        }

        val promptMessages = messages.toPromptMessages()
        if (promptMessages.none { it.role == MessageRole.USER }) {
            close(IllegalStateException("没有可发送的用户文本消息"))
            return@callbackFlow
        }

        val lastUserMessage = promptMessages.lastOrNull { it.role == MessageRole.USER }
        val imageBytes = lastUserMessage?.images.orEmpty().mapNotNull(::readImageBytes)
        if (lastUserMessage?.images?.isNotEmpty() == true && imageBytes.size != lastUserMessage.images.size) {
            trySend("[图片读取失败，请重新选择图片]")
            close()
            return@callbackFlow
        }

        val roles = promptMessages.map { it.role.toNativeRole() }.toTypedArray()
        val contents = promptMessages.map { it.content }.toTypedArray()
        val config = currentConfig ?: EngineConfig(modelPath = "")

        // 系统提示词必须作为第一条消息注入（toPromptMessages 跳过了 system 消息）
        val systemPrompt = config.systemPrompt
        val finalRoles = if (systemPrompt.isNotBlank() && roles.firstOrNull() != "system") {
            arrayOf("system") + roles
        } else {
            roles
        }
        val finalContents = if (systemPrompt.isNotBlank() && roles.firstOrNull() != "system") {
            arrayOf(systemPrompt) + contents
        } else {
            contents
        }

        logToFile("发送推理请求: promptMessages=${promptMessages.size}, systemPromptLen=${systemPrompt.length}, maxTokens=${config.maxTokens}, imageCount=${imageBytes.size}")

        _state.value = InferenceState.Generating()
        val tokenSanitizer = TemplateTokenSanitizer(
            stopMarkers = StopMarkers,
            removableMarkers = RemovableMarkers
        )
        val job = launch(runtimeDispatcher) {
            try {
                val callback = object : MnnLlmJni.TokenCallback {
                    override fun onToken(text: String): Boolean {
                        val sanitized = tokenSanitizer.append(text)
                        if (sanitized.text.isNotEmpty()) {
                            trySend(sanitized.text)
                        }
                        if (sanitized.shouldStop) {
                            logToFile("检测到停止标记，停止生成")
                            MnnLlmJni.nativeCancel(handle)
                            return true
                        }
                        return false
                    }
                }
                if (imageBytes.isNotEmpty()) {
                    MnnLlmJni.nativeGenerateWithImages(
                        handle,
                        finalRoles,
                        finalContents,
                        imageBytes.toTypedArray(),
                        config.maxTokens,
                        config.temperature,
                        config.topK,
                        config.topP,
                        callback
                    )
                } else {
                    MnnLlmJni.nativeGenerate(
                        handle,
                        finalRoles,
                        finalContents,
                        config.maxTokens,
                        config.temperature,
                        config.topK,
                        config.topP,
                        callback
                    )
                }
                val tail = tokenSanitizer.flush()
                if (tail.isNotEmpty()) {
                    trySend(tail)
                }
                logToFile("推理完成")
            } catch (e: CancellationException) {
                MnnLlmJni.nativeCancel(handle)
                logToFile("推理被取消")
                throw e
            } catch (e: Exception) {
                val message = "推理出错: ${e.message}"
                logToFile("$message (${e.javaClass.simpleName})")
                trySend("[$message]")
            } finally {
                _state.value = InferenceState.Ready
                close()
            }
        }

        awaitClose {
            job.cancel()
            cancel()
        }
    }

    private fun List<ChatMessage>.toPromptMessages(): List<ChatMessage> {
        val nonStreamingText = filter { !it.isStreaming && it.content.isNotBlank() }
        val firstUserIndex = nonStreamingText.indexOfFirst { it.role == MessageRole.USER }
        val conversationMessages = if (firstUserIndex >= 0) {
            nonStreamingText.drop(firstUserIndex)
        } else {
            nonStreamingText
        }
        val recentMessages = conversationMessages.takeLast(DefaultModelConfig.MaxPromptMessages)
        val firstRecentUserIndex = recentMessages.indexOfFirst { it.role == MessageRole.USER }
        return if (firstRecentUserIndex >= 0) {
            recentMessages.drop(firstRecentUserIndex)
        } else {
            recentMessages
        }
    }

    private fun MessageRole.toNativeRole(): String = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }

    override fun cancel() {
        if (handle != 0L) {
            MnnLlmJni.nativeCancel(handle)
        }
    }

    override fun getCurrentConfig(): EngineConfig? = currentConfig

    override suspend fun rebuildConversation(systemPrompt: String): Boolean = withContext(runtimeDispatcher) {
        if (handle == 0L) return@withContext false
        MnnLlmJni.nativeReset(handle)
        currentConfig = currentConfig?.copy(systemPrompt = systemPrompt)
        logToFile("MNN LLM 更新 system prompt 并重置 KV 缓存: ${systemPrompt.take(80)}")
        true
    }

    override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean {
        return rebuildConversation(systemPrompt)
    }

    override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = withContext(runtimeDispatcher) {
        if (handle == 0L) return@withContext false
        MnnLlmJni.nativeReset(handle)
        logToFile("MNN LLM 重置 KV 缓存以支持消息回放: messageCount=${messages.size}")
        true
    }

    override fun release() {
        releaseLoadedModel()
        runtimeExecutor.shutdown()
        _state.value = InferenceState.Idle
    }

    private fun releaseLoadedModel() {
        if (handle != 0L) {
            try {
                MnnLlmJni.nativeRelease(handle)
            } catch (e: Exception) {
                logToFile("释放 MNN LLM 引擎出错: ${e.message}")
            }
        }
        handle = 0L
        currentConfig = null
    }
}
