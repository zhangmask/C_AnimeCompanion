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

class LlamaCppInferenceEngine(private val context: Context) : InferenceEngine {
    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val MissingMmprojMessage = "GGUF 图片输入需要 mmproj 文件，请先推送: "
        private const val MultimodalContextSize = 8192
        private val StopMarkers = listOf(
            "<end_of_turn>",
            "<start_of_turn>",
            "<|endoftext|>",
            "<|eot_id|>"
        )
        private val RemovableMarkers = listOf(
            "<|assistant|>",
            "<|user|>",
            "<|system|>",
            "<assistant>",
            "<user>",
            "<system>",
            "</assistant>",
            "</user>",
            "</system>"
        )
    }

    private val runtimeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "llama-cpp-runtime").apply { isDaemon = true }
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
            context.openFileOutput("llama_engine_log.txt", Context.MODE_APPEND).use { fos ->
                fos.write(line.toByteArray())
            }
            Log.i(TAG, msg)
        } catch (e: Exception) {
            Log.e(TAG, "写日志失败: ${e.message}")
        }
    }

    private fun defaultModelPath(): String {
        val externalDir = context.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
        return if (externalDir != null) {
            File(externalDir, DefaultModelConfig.GgufModelFileName).absolutePath
        } else {
            File(File(context.filesDir, DefaultModelConfig.ExternalModelsDir), DefaultModelConfig.GgufModelFileName).absolutePath
        }
    }

    private fun defaultMmprojPath(): String {
        val externalDir = context.getExternalFilesDir(DefaultModelConfig.ExternalModelsDir)
        return if (externalDir != null) {
            File(externalDir, DefaultModelConfig.GgufMmprojFileName).absolutePath
        } else {
            File(File(context.filesDir, DefaultModelConfig.ExternalModelsDir), DefaultModelConfig.GgufMmprojFileName).absolutePath
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
            modelPath = config.modelPath.ifBlank { defaultModelPath() },
            mmprojPath = config.mmprojPath.ifBlank { defaultMmprojPath() },
            contextSize = config.contextSize.coerceAtLeast(MultimodalContextSize),
            systemPrompt = config.systemPrompt.ifBlank { DefaultModelConfig.DefaultSystemPrompt }
        )
        if (_state.value is InferenceState.Ready && currentConfig == resolvedConfig) {
            return@withContext
        }

        _state.value = InferenceState.Initializing
        releaseLoadedModel()

        val modelFile = File(resolvedConfig.modelPath)
        logToFile("=== 开始初始化 llama.cpp 引擎 ===")
        logToFile("llama.cpp systemInfo: ${LlamaCppNative.systemInfo()}")
        logToFile("模型路径: ${modelFile.absolutePath}")
        logToFile("mmproj路径: ${resolvedConfig.mmprojPath}")
        logToFile("模型文件存在: ${modelFile.exists()}")
        logToFile("模型可读: ${modelFile.canRead()}")
        logToFile("模型文件大小: ${modelFile.length()} bytes")

        when {
            !modelFile.exists() -> {
                val message = "GGUF 模型文件不存在: ${modelFile.absolutePath}"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
            !modelFile.canRead() -> {
                val message = "GGUF 模型文件不可读: ${modelFile.absolutePath}"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
            modelFile.length() <= 0L -> {
                val message = "GGUF 模型文件为空: ${modelFile.absolutePath}"
                logToFile(message)
                _state.value = InferenceState.Error(message)
                return@withContext
            }
        }

        try {
            handle = LlamaCppNative.loadModel(
                resolvedConfig.modelPath,
                resolvedConfig.mmprojPath,
                resolvedConfig.contextSize,
                resolvedConfig.systemPrompt,
                resolvedConfig.useGpu
            )
            currentConfig = resolvedConfig
            _state.value = InferenceState.Ready
            logToFile("=== llama.cpp 引擎初始化完成，状态: Ready ===")
        } catch (e: Exception) {
            releaseLoadedModel()
            val message = "GGUF 模型初始化失败: ${e.message}"
            logToFile("!!! $message")
            logToFile("异常类型: ${e.javaClass.simpleName}")
            logToFile("堆栈: ${e.stackTraceToString().take(1000)}")
            _state.value = InferenceState.Error(message)
        }
    }

    override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val activeHandle = handle
        if (activeHandle == 0L) {
            close(IllegalStateException("llama.cpp 引擎未初始化"))
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
        val mmprojFile = File(config.mmprojPath.ifBlank { defaultMmprojPath() })
        if (imageBytes.isNotEmpty() && (!mmprojFile.exists() || !mmprojFile.canRead() || mmprojFile.length() <= 0L)) {
            val message = MissingMmprojMessage + mmprojFile.absolutePath
            logToFile(message)
            trySend("[$message]")
            close()
            return@callbackFlow
        }
        logToFile("发送推理请求: promptMessages=${promptMessages.size}, maxTokens=${config.maxTokens}, contextSize=${config.contextSize}")

        _state.value = InferenceState.Generating()
        val tokenSanitizer = TemplateTokenSanitizer(
            stopMarkers = StopMarkers,
            removableMarkers = RemovableMarkers
        )
        val job = launch(runtimeDispatcher) {
            try {
                val repetitionGuard = RepetitionGuard()
                val callback = object : LlamaCppNative.TokenCallback {
                    override fun onTokenBytes(bytes: ByteArray) {
                        val sanitized = tokenSanitizer.append(bytes.toString(Charsets.UTF_8))
                        if (sanitized.text.isNotEmpty()) {
                            trySend(sanitized.text)
                            if (repetitionGuard.shouldStop(sanitized.text)) {
                                logToFile("检测到重复生成，提前停止")
                                LlamaCppNative.cancel(activeHandle)
                                return
                            }
                        }
                        if (sanitized.shouldStop) {
                            LlamaCppNative.cancel(activeHandle)
                        }
                    }

                    override fun onPerformanceLog(message: String) {
                        logToFile(message)
                    }
                }
                if (imageBytes.isNotEmpty()) {
                    LlamaCppNative.generateMultimodal(
                        activeHandle,
                        buildMultimodalPrompt(lastUserMessage?.content.orEmpty(), imageBytes.size),
                        imageBytes.toTypedArray(),
                        config.maxTokens,
                        config.temperature,
                        config.topK,
                        config.topP,
                        callback
                    )
                } else {
                    LlamaCppNative.generate(
                        activeHandle,
                        roles,
                        contents,
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
                LlamaCppNative.cancel(activeHandle)
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

    private fun buildMultimodalPrompt(userText: String, imageCount: Int): String {
        val markerPrefix = buildString {
            repeat(imageCount) {
                append("<__media__>")
                append('\n')
            }
        }
        val question = userText.ifBlank { "请描述这张图片。" }
        return buildString {
            append("<start_of_turn>user\n")
            append(markerPrefix)
            append("请只根据图片内容回答，不要猜测图片之外的信息。")
            append("先说你看到的主体，再补充关键细节。")
            append("如果用户的问题含糊，请把它理解为“这张图里是什么”。\n")
            append("用户问题：")
            append(question)
            append("\n<end_of_turn>\n<start_of_turn>model\n")
        }
    }

    override fun cancel() {
        val activeHandle = handle
        if (activeHandle != 0L) {
            LlamaCppNative.cancel(activeHandle)
        }
    }

    override fun getCurrentConfig(): EngineConfig? {
        return currentConfig
    }

    override suspend fun rebuildConversation(systemPrompt: String): Boolean = withContext(runtimeDispatcher) {
        val config = currentConfig ?: return@withContext false
        currentConfig = config.copy(systemPrompt = systemPrompt)
        logToFile("llama.cpp 更新 system prompt: ${systemPrompt.take(80)}")
        true
    }

    override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean {
        return rebuildConversation(systemPrompt)
    }

    override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = withContext(runtimeDispatcher) {
        logToFile("llama.cpp 使用 prompt 重组上下文，跳过 Conversation 回放: messageCount=${messages.size}")
        true
    }

    override fun release() {
        releaseLoadedModel()
        runtimeExecutor.shutdown()
        _state.value = InferenceState.Idle
    }

    private fun releaseLoadedModel() {
        val activeHandle = handle
        if (activeHandle != 0L) {
            try {
                LlamaCppNative.releaseModel(activeHandle)
            } catch (e: Exception) {
                logToFile("释放 llama.cpp 引擎出错: ${e.message}")
            }
        }
        handle = 0L
        currentConfig = null
    }
}

private class RepetitionGuard {
    private val generated = StringBuilder()

    fun shouldStop(chunk: String): Boolean {
        generated.append(chunk)
        if (generated.length > MaxTrackedChars) {
            generated.delete(0, generated.length - MaxTrackedChars)
        }

        val normalizedLines = generated
            .lineSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toList()
        val lastLine = normalizedLines.lastOrNull() ?: return false
        val trailingSameLineCount = normalizedLines
            .asReversed()
            .takeWhile { it == lastLine }
            .count()
        if (trailingSameLineCount >= MaxSameTrailingLines) {
            return true
        }

        val repeatedSentenceCount = generated
            .toString()
            .takeLast(RepeatedTailWindow)
            .split('。', '？', '！', '\n')
            .map { it.trim() }
            .filter { it.length >= 3 }
            .groupingBy { it }
            .eachCount()
            .values
            .maxOrNull()
            ?: 0
        return repeatedSentenceCount >= MaxRepeatedSentenceCount
    }

    private companion object {
        const val MaxTrackedChars = 1200
        const val RepeatedTailWindow = 700
        const val MaxSameTrailingLines = 4
        const val MaxRepeatedSentenceCount = 6
    }
}
