package com.companion.chat.data.voice

data class CloudAsrConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val requestFieldName: String = "audio",
    val responseTextFieldPath: String = "text",
    val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) {
    val isConfigured: Boolean = baseUrl.isNotBlank()

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
        const val MIN_TIMEOUT_MILLIS = 1_000
        const val MAX_TIMEOUT_MILLIS = 120_000
    }
}
