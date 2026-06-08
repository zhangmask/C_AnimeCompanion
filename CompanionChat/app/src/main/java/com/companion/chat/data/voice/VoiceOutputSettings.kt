package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences

data class VoiceOutputSettings(
    val autoPlayTts: Boolean = true
)

class VoiceOutputSettingsRepository(
    private val sharedPreferences: SharedPreferences
) {
    constructor(context: Context) : this(
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    fun getSettings(): VoiceOutputSettings {
        return VoiceOutputSettings(
            autoPlayTts = sharedPreferences.getBoolean(KEY_AUTO_PLAY_TTS, true)
        )
    }

    fun updateSettings(settings: VoiceOutputSettings) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTO_PLAY_TTS, settings.autoPlayTts)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "voice_output_settings"
        private const val KEY_AUTO_PLAY_TTS = "auto_play_tts"
    }
}
