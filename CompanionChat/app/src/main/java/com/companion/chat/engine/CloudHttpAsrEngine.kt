package com.companion.chat.engine

import com.companion.chat.data.voice.CloudAsrConfigRepository
import com.companion.chat.data.voice.CloudAsrResponseParser
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class CloudHttpAsrEngine(
    private val configRepository: CloudAsrConfigRepository,
    private val responseParser: CloudAsrResponseParser = CloudAsrResponseParser()
) {
    fun transcribe(audio: RecordedAudio): String {
        val config = configRepository.getConfig()
        if (!config.isConfigured) {
            throw IllegalStateException("云 ASR 未配置")
        }

        val boundary = "CompanionChatAsr${System.currentTimeMillis()}"
        val connection = (URL(config.baseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutMillis
            readTimeout = config.timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
        }

        val wavBytes = WavEncoder.encodePcm16Mono(audio)
        connection.outputStream.use { output ->
            output.writeMultipartFile(boundary, config.requestFieldName, "speech.wav", "audio/wav", wavBytes)
            output.writeAscii("--$boundary--\r\n")
        }

        val statusCode = connection.responseCode
        val body = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("云 ASR 请求失败 ($statusCode): $errorBody")
        }

        return responseParser.extractText(body, config.responseTextFieldPath)
    }

    private fun OutputStream.writeMultipartFile(
        boundary: String,
        fieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ) {
        writeAscii("--$boundary\r\n")
        writeAscii("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
        writeAscii("Content-Type: $contentType\r\n\r\n")
        write(bytes)
        writeAscii("\r\n")
    }

    private fun OutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }
}
