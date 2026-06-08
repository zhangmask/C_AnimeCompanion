package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences

class CloudAsrConfigRepository(
    private val sharedPreferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun getConfig(): CloudAsrConfig {
        return CloudAsrConfig(
            baseUrl = sharedPreferences.getString(KEY_BASE_URL, "").orEmpty().trim(),
            apiKey = sharedPreferences.getString(KEY_API_KEY, "").orEmpty().trim(),
            requestFieldName = sharedPreferences.getString(KEY_REQUEST_FIELD_NAME, "audio")
                .orEmpty()
                .trim()
                .ifBlank { "audio" },
            responseTextFieldPath = sharedPreferences.getString(KEY_RESPONSE_TEXT_FIELD_PATH, "text")
                .orEmpty()
                .trim()
                .ifBlank { "text" },
            timeoutMillis = sanitizeTimeout(
                sharedPreferences.getInt(KEY_TIMEOUT_MILLIS, CloudAsrConfig.DEFAULT_TIMEOUT_MILLIS)
            )
        )
    }

    fun updateConfig(config: CloudAsrConfig) {
        sharedPreferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_REQUEST_FIELD_NAME, config.requestFieldName.trim().ifBlank { "audio" })
            .putString(KEY_RESPONSE_TEXT_FIELD_PATH, config.responseTextFieldPath.trim().ifBlank { "text" })
            .putInt(KEY_TIMEOUT_MILLIS, sanitizeTimeout(config.timeoutMillis))
            .apply()
    }

    private fun sanitizeTimeout(timeoutMillis: Int): Int {
        return timeoutMillis.coerceIn(
            CloudAsrConfig.MIN_TIMEOUT_MILLIS,
            CloudAsrConfig.MAX_TIMEOUT_MILLIS
        )
    }

    private companion object {
        const val PREFS_NAME = "cloud_asr_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_REQUEST_FIELD_NAME = "request_field_name"
        const val KEY_RESPONSE_TEXT_FIELD_PATH = "response_text_field_path"
        const val KEY_TIMEOUT_MILLIS = "timeout_millis"
    }
}
