package com.companion.chat.data.image

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HttpImageGenerationEngine(
    context: Context
) : ImageGenerationEngine {

    private val imageFileStore = ImageFileStore(context)
    private val _state = MutableStateFlow<ImageGenerationState>(ImageGenerationState.Idle)
    override val state: StateFlow<ImageGenerationState> = _state.asStateFlow()

    override suspend fun generate(
        prompt: String,
        config: ImageGenerationConfig,
        purpose: ImageGenerationPurpose
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            val error = "图片生成 Base URL 未配置"
            _state.value = ImageGenerationState.Error(error)
            return@withContext Result.failure(IllegalStateException(error))
        }
        if (prompt.isBlank()) {
            val error = "图片生成提示词不能为空"
            _state.value = ImageGenerationState.Error(error)
            return@withContext Result.failure(IllegalArgumentException(error))
        }

        _state.value = ImageGenerationState.Generating
        runCatching {
            val response = postJson(
                url = config.baseUrl,
                apiKey = config.apiKey,
                body = renderTemplate(config.requestTemplate, config.model, prompt),
                timeoutMillis = config.timeoutMillis
            )
            val imageValue = readFieldPath(JSONObject(response), config.responseImageFieldPath)
                ?: error("响应中未找到图片字段: ${config.responseImageFieldPath}")
            val uri = when {
                imageValue.startsWith("http://") || imageValue.startsWith("https://") ->
                    downloadImage(imageValue, purpose, config.timeoutMillis)
                imageValue.startsWith("data:image") ->
                    saveBase64Image(imageValue.substringAfter(","), purpose)
                else -> saveBase64Image(imageValue, purpose)
            }
            _state.value = ImageGenerationState.Success(uri)
            uri
        }.onFailure { error ->
            _state.value = ImageGenerationState.Error(error.message ?: "图片生成失败")
        }
    }

    private fun postJson(
        url: String,
        apiKey: String,
        body: String,
        timeoutMillis: Int
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                error("图片生成 HTTP ${connection.responseCode}: ${response.take(160)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun renderTemplate(template: String, model: String, prompt: String): String {
        return template
            .replace("{{model}}", escapeJson(model))
            .replace("{{prompt}}", escapeJson(prompt))
    }

    private fun escapeJson(value: String): String =
        JSONObject.quote(value).removePrefix("\"").removeSuffix("\"")

    private fun readFieldPath(root: JSONObject, path: String): String? {
        var current: Any = root
        path.split(".").filter { it.isNotBlank() }.forEach { part ->
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return null
                is JSONArray -> current.opt(part.toIntOrNull() ?: return null)
                    ?: return null
                else -> return null
            }
        }
        return current.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun downloadImage(url: String, purpose: ImageGenerationPurpose, timeoutMillis: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("图片下载 HTTP ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            return saveBytes(bytes, purpose)
        } finally {
            connection.disconnect()
        }
    }

    private fun saveBase64Image(base64: String, purpose: ImageGenerationPurpose): String =
        imageFileStore.saveBytes(Base64.decode(base64, Base64.DEFAULT), purpose)

    private fun saveBytes(bytes: ByteArray, purpose: ImageGenerationPurpose): String =
        imageFileStore.saveBytes(bytes, purpose)
}
