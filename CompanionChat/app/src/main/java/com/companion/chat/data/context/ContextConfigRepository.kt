package com.companion.chat.data.context

import android.content.Context
import android.content.SharedPreferences

class ContextConfigRepository(
    private val sharedPreferences: SharedPreferences
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    )

    fun getSettings(): ContextSettings {
        return ContextSettings(
            retainedRounds = sharedPreferences.getInt(KEY_RETAINED_ROUNDS, DEFAULT_SETTINGS.retainedRounds),
            compressionBuffer = sharedPreferences.getInt(
                KEY_COMPRESSION_BUFFER,
                DEFAULT_SETTINGS.compressionBuffer
            ),
            summaryMaxChars = sharedPreferences.getInt(
                KEY_SUMMARY_MAX_CHARS,
                DEFAULT_SETTINGS.summaryMaxChars
            ),
            summaryTimeoutMillis = sharedPreferences.getLong(
                KEY_SUMMARY_TIMEOUT_MILLIS,
                DEFAULT_SETTINGS.summaryTimeoutMillis
            )
        )
    }

    fun updateSettings(settings: ContextSettings) {
        sharedPreferences.edit()
            .putInt(KEY_RETAINED_ROUNDS, settings.retainedRounds)
            .putInt(KEY_COMPRESSION_BUFFER, settings.compressionBuffer)
            .putInt(KEY_SUMMARY_MAX_CHARS, settings.summaryMaxChars)
            .putLong(KEY_SUMMARY_TIMEOUT_MILLIS, settings.summaryTimeoutMillis)
            .apply()
    }

    fun updateRetainedRounds(retainedRounds: Int) {
        val currentSettings = getSettings()
        updateSettings(
            currentSettings.copy(retainedRounds = retainedRounds.coerceIn(MIN_RETAINED_ROUNDS, MAX_RETAINED_ROUNDS))
        )
    }

    fun getAutoPreferenceLearningEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            KEY_AUTO_PREFERENCE_LEARNING_ENABLED,
            DEFAULT_AUTO_PREFERENCE_LEARNING_ENABLED
        )
    }

    fun updateAutoPreferenceLearningEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTO_PREFERENCE_LEARNING_ENABLED, enabled)
            .apply()
    }

    companion object {
        const val MIN_RETAINED_ROUNDS = 3
        const val MAX_RETAINED_ROUNDS = 20
        private const val PREFS_NAME = "context_settings"
        private const val KEY_RETAINED_ROUNDS = "retained_rounds"
        private const val KEY_COMPRESSION_BUFFER = "compression_buffer"
        private const val KEY_SUMMARY_MAX_CHARS = "summary_max_chars"
        private const val KEY_SUMMARY_TIMEOUT_MILLIS = "summary_timeout_millis"
        private const val KEY_AUTO_PREFERENCE_LEARNING_ENABLED = "auto_preference_learning_enabled"
        private const val DEFAULT_AUTO_PREFERENCE_LEARNING_ENABLED = true

        val DEFAULT_SETTINGS = ContextSettings()
    }
}
