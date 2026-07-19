package com.companion.chat.engine

import android.util.Log
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.local.entity.CustomApiConfig
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

class CustomApiInferenceEngine : InferenceEngine {

    companion object {
        private const val TAG = "CustomApiEngine"
    }

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    override val state: StateFlow<InferenceState> = _state.asStateFlow()

    private var config: EngineConfig? = null
    private val cancelled = AtomicBoolean(false)
    private var currentConnection: HttpURLConnection? = null

    override suspend fun initialize(config: EngineConfig) {
        Log.i(TAG, "initialize: customApiConfig=${config.customApiConfig != null}")
        if (config.customApiConfig == null) {
            _state.value = InferenceState.Error("自定义 API 配置为空")
            return
        }
        this.config = config
        _state.value = InferenceState.Ready
    }

    override fun getCurrentConfig(): EngineConfig? = config

    override suspend fun rebuildConversation(systemPrompt: String): Boolean = true

    override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean = true

    override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = true

    override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        cancelled.set(false)
        val apiConfig = config?.customApiConfig
        if (apiConfig == null) {
            close(Exception("自定义 API 配置为空"))
            return@callbackFlow
        }

        _state.value = InferenceState.Generating("")

        launch(Dispatchers.IO) {
            val messagesArray = JSONArray()
            val systemPrompt = config?.systemPrompt?.trim().orEmpty()
            if (systemPrompt.isNotBlank()) {
                messagesArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            for (msg in messages) {
                val role = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
                }
                messagesArray.put(JSONObject().put("role", role).put("content", msg.content))
            }

            val body = JSONObject()
            body.put("model", apiConfig.model)
            body.put("messages", messagesArray)
            body.put("stream", true)
            body.put("temperature", config?.temperature ?: 0.7f)
            body.put("max_tokens", config?.maxTokens ?: 256)
            if (config?.jsonMode == true && apiConfig.apiFormat != "ANTHROPIC") {
                body.put("response_format", JSONObject().put("type", "json_object"))
            }

            val customParams = runCatching { JSONObject(apiConfig.customParams) }.getOrNull()
            if (customParams != null) {
                val keys = customParams.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    body.put(key, customParams.get(key))
                }
            }

            val endpoint = buildEndpoint(apiConfig.baseUrl, apiConfig.apiFormat)
            val maxRetries = 2
            var attempt = 0
            var tokensReceived = false

            while (true) {
                var connection: HttpURLConnection? = null
                try {
                    Log.i(TAG, "POST $endpoint model=${apiConfig.model} attempt=${attempt + 1}/${maxRetries + 1}")

                    val url = URL(endpoint)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30000
                        readTimeout = 120000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "text/event-stream")
                        setRequestProperty("Authorization", "Bearer ${apiConfig.apiKey}")
                    }
                    currentConnection = connection

                    val outputStream: OutputStream = connection.outputStream
                    outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        Log.e(TAG, "API 错误 $responseCode: $errorText")
                        connection.disconnect()
                        currentConnection = null

                        // 5xx 和 429 可重试
                        if (attempt < maxRetries && (responseCode >= 500 || responseCode == 429)) {
                            attempt++
                            Log.i(TAG, "服务端 $responseCode，第 $attempt 次重试...")
                            delay(1000L * attempt)
                            continue
                        }
                        _state.value = InferenceState.Error("API 错误 $responseCode: ${errorText.take(200)}")
                        close(Exception("API 错误 $responseCode"))
                        return@launch
                    }

                    val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (cancelled.get()) break
                        val data = line ?: continue
                        if (!data.startsWith("data:")) continue
                        val payload = data.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        if (payload.isEmpty()) continue

                        val delta = extractDeltaContent(payload, apiConfig.apiFormat)
                        if (delta.isNotEmpty()) {
                            tokensReceived = true
                            trySend(delta)
                        }
                    }

                    _state.value = InferenceState.Ready
                    close()
                    return@launch
                } catch (e: CancellationException) {
                    _state.value = InferenceState.Ready
                    close()
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "流式请求失败 (attempt ${attempt + 1})", e)
                    connection?.disconnect()
                    currentConnection = null

                    // 仅在未收到任何 token 且为网络错误时重试
                    if (attempt < maxRetries && !tokensReceived && isRetryableNetworkError(e)) {
                        attempt++
                        Log.i(TAG, "网络错误，第 $attempt 次重试: ${e.message}")
                        delay(1000L * attempt)
                        continue
                    }
                    _state.value = InferenceState.Error(e.message ?: "未知错误")
                    close(e)
                    return@launch
                } finally {
                    connection?.disconnect()
                    currentConnection = null
                }
            }
        }

        awaitClose {
            cancelled.set(true)
            currentConnection?.disconnect()
        }
    }

    private fun isRetryableNetworkError(e: Exception): Boolean {
        return e is UnknownHostException ||
            e is SocketTimeoutException ||
            e is ConnectException ||
            (e is java.io.IOException && e.message?.let {
                it.contains("network", ignoreCase = true) ||
                it.contains("connection", ignoreCase = true) ||
                it.contains("reset", ignoreCase = true) ||
                it.contains("broken pipe", ignoreCase = true)
            } == true)
    }

    private fun extractDeltaContent(payload: String, apiFormat: String): String {
        return try {
            val json = JSONObject(payload)
            when (apiFormat) {
                "ANTHROPIC" -> {
                    val type = json.optString("type", "")
                    if (type == "content_block_delta") {
                        val delta = json.optJSONObject("delta")
                        delta?.optString("text", "") ?: ""
                    } else ""
                }
                else -> {
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.getJSONObject(0).optJSONObject("delta")
                        val content = delta?.optString("content", "") ?: ""
                        // 后台总结等场景可开启：模型把结果放在 reasoning_content 里时回退读取
                        if (content.isBlank() && config?.fallbackToReasoningContent == true) {
                            delta?.optString("reasoning_content", "") ?: ""
                        } else {
                            content
                        }
                    } else ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * baseUrl 可能带 /v1 也可能不带，统一构造完整端点。
     * 例：https://api.openai.com → /v1/chat/completions
     *     https://api.stepfun.com/step_plan/v1 → /chat/completions
     */
    private fun buildEndpoint(baseUrl: String, apiFormat: String): String {
        val base = baseUrl.trimEnd('/')
        val suffix = when (apiFormat) {
            "ANTHROPIC" -> if (base.endsWith("/v1")) "/messages" else "/v1/messages"
            else -> if (base.endsWith("/v1")) "/chat/completions" else "/v1/chat/completions"
        }
        return "$base$suffix"
    }

    override fun cancel() {
        cancelled.set(true)
        currentConnection?.disconnect()
        if (_state.value is InferenceState.Generating) {
            _state.value = InferenceState.Ready
        }
    }

    override fun release() {
        cancel()
        _state.value = InferenceState.Idle
        config = null
    }
}
