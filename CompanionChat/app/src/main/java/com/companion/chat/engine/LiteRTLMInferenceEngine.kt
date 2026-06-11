package com.companion.chat.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.companion.chat.data.engine.BackendType
import com.companion.chat.data.engine.DefaultModelConfig
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.EngineConfig as LiteRTConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiteRTLMInferenceEngine(private val context: Context) : InferenceEngine {

    companion object {
        private const val TAG = "LiteRTLMEngine"
        private const val DEFAULT_MODEL_FILE = DefaultModelConfig.LiteRtModelFileName
    }

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    override val state: StateFlow<InferenceState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentConfig: EngineConfig? = null
    private val rebuildMutex = Mutex()

    private fun createConversationConfig(
        systemPrompt: String,
        initialMessages: List<Message> = emptyList()
    ): ConversationConfig {
        return ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = initialMessages,
            samplerConfig = SamplerConfig(
                topK = currentConfig?.topK ?: DefaultModelConfig.DefaultTopK,
                topP = (currentConfig?.topP ?: DefaultModelConfig.DefaultTopP).toDouble(),
                temperature = (currentConfig?.temperature ?: DefaultModelConfig.DefaultTemperature).toDouble()
            )
        )
    }

    private fun toInitialMessage(message: ChatMessage, loadImages: Boolean = true): Message? {
        return when (message.role) {
            MessageRole.USER -> {
                val contentList = mutableListOf<Content>()
                // 只有当前消息才加载图片，历史消息只保留文字描述
                if (loadImages) {
                    message.images.mapNotNull(::uriToImageBytes).forEach { bytes ->
                        contentList += Content.ImageBytes(bytes)
                    }
                }
                val textContent = if (message.images.isNotEmpty() && !loadImages) {
                    // 历史消息中的图片只给文字提示
                    val imageHint = "[附带${message.images.size}张图片]"
                    if (message.content.isNotBlank()) "${message.content} $imageHint" else imageHint
                } else {
                    message.content
                }
                if (textContent.isNotBlank()) {
                    contentList += Content.Text(textContent)
                }
                if (contentList.isEmpty()) {
                    null
                } else {
                    Message.user(Contents.of(contentList))
                }
            }
            MessageRole.ASSISTANT -> {
                if (message.content.isBlank()) {
                    null
                } else {
                    Message.model(message.content)
                }
            }
            MessageRole.SYSTEM -> {
                if (message.content.isBlank()) {
                    null
                } else {
                    Message.system(message.content)
                }
            }
        }
    }

    private suspend fun replaceConversation(
        systemPrompt: String,
        initialMessages: List<Message>,
        reasonLabel: String
    ): Boolean = withContext(Dispatchers.IO) {
        rebuildMutex.withLock {
            val eng = engine
            val previousConfig = currentConfig
            if (eng == null || previousConfig == null) {
                logToFile("$reasonLabel 失败: 引擎未初始化或当前配置缺失")
                return@withLock false
            }

            try {
                conversation?.close()
                conversation = null
            } catch (closeError: Exception) {
                logToFile("$reasonLabel 关闭旧 Conversation 失败: ${closeError.message}")
            }

            return@withLock try {
                val newConversation = eng.createConversation(
                    createConversationConfig(
                        systemPrompt = systemPrompt,
                        initialMessages = initialMessages
                    )
                )
                conversation = newConversation
                currentConfig = previousConfig.copy(systemPrompt = systemPrompt)
                true
            } catch (createError: Exception) {
                logToFile("$reasonLabel 创建新 Conversation 失败: ${createError.javaClass.simpleName}: ${createError.message}")

                try {
                    val fallbackConversation = eng.createConversation(
                        createConversationConfig(
                            systemPrompt = previousConfig.systemPrompt
                        )
                    )
                    conversation = fallbackConversation
                    currentConfig = previousConfig
                    logToFile("$reasonLabel 降级恢复成功: 已恢复到空历史 Conversation")
                } catch (fallbackError: Exception) {
                    conversation = null
                    logToFile("$reasonLabel 降级恢复失败: ${fallbackError.javaClass.simpleName}: ${fallbackError.message}")
                }

                false
            }
        }
    }

    private fun logToFile(msg: String) {
        try {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$time] $msg\n"
            context.openFileOutput("engine_log.txt", android.content.Context.MODE_APPEND).use { fos ->
                fos.write(line.toByteArray())
            }
            Log.i(TAG, msg)
        } catch (e: Exception) {
            Log.e(TAG, "写日志失败: ${e.message}")
        }
    }

    private fun getDefaultModelPath(): String {
        val internalDir = File(context.filesDir, "models")
        return "${internalDir.absolutePath}/$DEFAULT_MODEL_FILE"
    }

    private fun getExternalModelPath(): String {
        val externalDir = context.getExternalFilesDir("models")
        return if (externalDir != null) {
            "${externalDir.absolutePath}/$DEFAULT_MODEL_FILE"
        } else ""
    }

    private suspend fun ensureModelInInternalStorage(): String = withContext(Dispatchers.IO) {
        val internalPath = getDefaultModelPath()
        val internalFile = File(internalPath)

        if (internalFile.exists() && internalFile.length() > 0) {
            logToFile("模型已在内部存储: $internalPath (${internalFile.length()} bytes)")
            return@withContext internalPath
        }

        val externalPath = getExternalModelPath()
        val externalFile = File(externalPath)

        if (!externalFile.exists()) {
            logToFile("外部存储也无模型文件: $externalPath")
            return@withContext internalPath
        }

        logToFile("模型在外部存储，开始复制到内部存储...")
        logToFile("源: $externalPath (${externalFile.length()} bytes)")
        logToFile("目标: $internalPath")

        internalFile.parentFile?.mkdirs()

        try {
            externalFile.inputStream().use { input ->
                internalFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024 * 1024)
                    var copied = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copied += bytesRead
                        if (copied % (100 * 1024 * 1024) < buffer.size) {
                            logToFile("复制进度: ${copied / 1024 / 1024}MB / ${externalFile.length() / 1024 / 1024}MB")
                        }
                    }
                    output.flush()
                }
            }
            logToFile("模型复制完成: ${internalFile.length()} bytes")
        } catch (e: Exception) {
            logToFile("模型复制失败: ${e.message}")
            if (internalFile.exists()) internalFile.delete()
        }

        internalPath
    }

    private fun uriToImageBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
                val maxSize = 1024
                val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                    val ratio = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true
                    )
                } else {
                    bitmap
                }
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                output.toByteArray()
            }
        } catch (e: Exception) {
            logToFile("图片转换失败: ${uri} - ${e.message}")
            null
        }
    }

    override suspend fun initialize(config: EngineConfig) = withContext(Dispatchers.IO) {
        if (_state.value is InferenceState.Ready && currentConfig == config) {
            return@withContext
        }

        _state.value = InferenceState.Initializing
        release()

        try {
            val modelPath = config.modelPath.ifBlank {
                ensureModelInInternalStorage()
            }
            val modelFile = File(modelPath)
            logToFile("=== 开始初始化引擎 ===")
            logToFile("模型路径: $modelPath")
            logToFile("模型文件存在: ${modelFile.exists()}")
            logToFile("模型文件大小: ${modelFile.length()} bytes")

            if (!modelFile.exists() || modelFile.length() == 0L) {
                _state.value = InferenceState.Error("模型文件不存在或为空: $modelPath")
                logToFile("模型文件不存在或为空，初始化终止")
                return@withContext
            }

            // 尝试使用请求的后端，如果失败则回退到 CPU
            val backendsToTry = if (config.backend == BackendType.GPU) {
                listOf(BackendType.GPU to "GPU", BackendType.CPU to "CPU")
            } else {
                listOf(BackendType.CPU to "CPU")
            }

            // MTP (Multi-Token Prediction) 当前模型测试后速度反而变慢，暂时禁用
            // @OptIn(ExperimentalApi::class)
            // ExperimentalFlags.enableSpeculativeDecoding = true
            // logToFile("已启用 MTP (Multi-Token Prediction)")

            var lastException: Exception? = null
            for ((backendType, backendName) in backendsToTry) {
                try {
                    logToFile("尝试使用 $backendName 后端...")
                    val backend = when (backendType) {
                        BackendType.GPU -> Backend.GPU()
                        else -> Backend.CPU()
                    }

                    logToFile("创建 EngineConfig...")
                    val litertConfig = LiteRTConfig(
                        modelPath = modelPath,
                        backend = backend,
                        visionBackend = Backend.CPU(),
                        maxNumImages = 4,
                        cacheDir = context.cacheDir.absolutePath
                    )
                    logToFile("EngineConfig 创建成功 (含 visionBackend=CPU, maxNumImages=4)")

                    logToFile("创建 Engine...")
                    val eng = Engine(litertConfig)
                    logToFile("Engine 创建成功")

                    logToFile("Engine.initialize() 开始...")
                    eng.initialize()
                    logToFile("Engine.initialize() 完成")

                    val systemPrompt = config.systemPrompt.ifBlank {
                        "你是一个友善的AI助手，请用中文回答用户的问题。"
                    }
                    logToFile("系统提示词: ${systemPrompt.take(50)}...")

                    val convConfig = createConversationConfig(systemPrompt)
                    logToFile("ConversationConfig 创建成功")

                    logToFile("创建 Conversation...")
                    val conv = eng.createConversation(convConfig)
                    logToFile("Conversation 创建成功")

                    engine = eng
                    conversation = conv
                    currentConfig = config
                    _state.value = InferenceState.Ready

                    if (backendType == BackendType.CPU && config.backend == BackendType.GPU) {
                        logToFile("⚠️ GPU 初始化失败，已自动回退到 CPU 后端")
                    } else {
                        logToFile("=== 引擎初始化完成，使用 $backendName 后端，状态: Ready ===")
                    }
                    return@withContext
                } catch (e: Exception) {
                    lastException = e
                    logToFile("$backendName 后端初始化失败: ${e.message}")
                    if (backendType == BackendType.GPU) {
                        logToFile("将尝试回退到 CPU 后端...")
                    }
                }
            }

            // 所有后端都失败
            logToFile("!!! 所有后端初始化失败 !!!")
            logToFile("异常类型: ${lastException?.javaClass?.simpleName}")
            logToFile("异常信息: ${lastException?.message}")
            logToFile("堆栈: ${lastException?.stackTraceToString()?.take(500)}")
            _state.value = InferenceState.Error("模型初始化失败: ${lastException?.message}")
        } catch (e: Exception) {
            logToFile("!!! 引擎初始化失败 !!!")
            logToFile("异常类型: ${e.javaClass.simpleName}")
            logToFile("异常信息: ${e.message}")
            logToFile("堆栈: ${e.stackTraceToString().take(500)}")
            _state.value = InferenceState.Error("模型初始化失败: ${e.message}")
        }
    }

    override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val conv = conversation
        if (conv == null) {
            logToFile("sendMessageStream: 引擎未初始化")
            close(IllegalStateException("引擎未初始化"))
            return@callbackFlow
        }

        _state.value = InferenceState.Generating()

        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
        if (lastUserMessage == null) {
            logToFile("sendMessageStream: 没有用户消息")
            close(IllegalStateException("没有用户消息"))
            return@callbackFlow
        }

        logToFile("开始推理: ${lastUserMessage.content.take(50)}...")

        val imageBytesList = lastUserMessage.images.mapNotNull { uri ->
            uriToImageBytes(uri)
        }
        if (lastUserMessage.images.isNotEmpty() && imageBytesList.size != lastUserMessage.images.size) {
            val message = "图片读取失败，请重新选择图片"
            logToFile(message)
            trySend("[$message]")
            _state.value = InferenceState.Ready
            close()
            return@callbackFlow
        }
        if (imageBytesList.isNotEmpty()) {
            logToFile("检测到 ${imageBytesList.size} 张图片，构建多模态消息")
        }

        try {
            val flow = if (imageBytesList.isNotEmpty()) {
                val contentList = mutableListOf<Content>()
                imageBytesList.forEach { bytes ->
                    contentList.add(Content.ImageBytes(bytes))
                }
                if (lastUserMessage.content.isNotBlank()) {
                    contentList.add(Content.Text(lastUserMessage.content))
                }
                val contents = Contents.of(contentList)
                logToFile("发送多模态消息 (${contentList.size} 个内容块)")
                conv.sendMessageAsync(contents)
            } else {
                logToFile("发送纯文本消息")
                conv.sendMessageAsync(lastUserMessage.content)
            }

            flow.collect { message ->
                val text = message.toString()
                if (text.isNotEmpty()) {
                    trySend(text)
                }
            }
            logToFile("推理完成")
        } catch (e: CancellationException) {
            logToFile("推理被取消")
            throw e
        } catch (e: Exception) {
            logToFile("推理出错: ${e.javaClass.simpleName}: ${e.message}")
            trySend("[推理出错: ${e.message}]")
        } finally {
            _state.value = InferenceState.Ready
            close()
        }

        awaitClose {
            cancel()
        }
    }

    override fun cancel() {
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            logToFile("取消推理出错: ${e.message}")
        }
    }

    override fun getCurrentConfig(): EngineConfig? {
        return currentConfig
    }

    override suspend fun rebuildConversation(systemPrompt: String): Boolean = withContext(Dispatchers.IO) {
        logToFile("开始重建 Conversation")
        logToFile("新 system prompt: ${systemPrompt.take(80)}")
        val success = replaceConversation(
            systemPrompt = systemPrompt,
            initialMessages = emptyList(),
            reasonLabel = "Conversation 重建"
        )
        if (success) {
            logToFile("Conversation 重建完成")
        }
        success
    }

    override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean = withContext(Dispatchers.IO) {
        logToFile("开始降级摘要注入重建")
        logToFile("降级 system prompt: ${systemPrompt.take(80)}")
        val success = replaceConversation(
            systemPrompt = systemPrompt,
            initialMessages = emptyList(),
            reasonLabel = "降级摘要注入重建"
        )
        if (success) {
            logToFile("降级摘要注入重建完成")
        }
        success
    }

    override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            logToFile("最近消息回放跳过: 无需回放")
            return@withContext true
        }

        val existingConfig = currentConfig
        if (existingConfig == null) {
            logToFile("最近消息回放失败: 引擎未初始化或配置缺失")
            return@withContext false
        }

        // 历史消息回放时不加载图片，只保留文字描述
        val initialMessages = messages.mapNotNull { toInitialMessage(it, loadImages = false) }
        if (initialMessages.isEmpty()) {
            logToFile("最近消息回放跳过: 转换后的初始消息为空")
            return@withContext true
        }

        logToFile("开始最近消息回放实验: messageCount=${messages.size}")
        val success = replaceConversation(
            systemPrompt = existingConfig.systemPrompt,
            initialMessages = initialMessages,
            reasonLabel = "最近消息回放"
        )
        if (success) {
            logToFile("最近消息回放成功: initialMessages=${initialMessages.size}")
        } else {
            logToFile("最近消息回放失败，降级为摘要注入")
        }
        success
    }

    override fun release() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            logToFile("释放引擎出错: ${e.message}")
        }
        conversation = null
        engine = null
        currentConfig = null
        _state.value = InferenceState.Idle
    }
}
