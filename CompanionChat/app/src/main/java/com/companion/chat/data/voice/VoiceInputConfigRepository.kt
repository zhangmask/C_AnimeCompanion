package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class VoiceInputConfigRepository(
    private val sharedPreferences: SharedPreferences,
    private val defaultModelDirectoryProvider: () -> String = { "" }
) {
    constructor(context: Context) : this(
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(DEFAULT_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        }
    )

    fun getConfig(): VoiceInputConfig {
        return VoiceInputConfig(
            backend = parseBackend(sharedPreferences.getString(KEY_BACKEND, VoiceInputBackend.LOCAL_SENSEVOICE.name)),
            localSenseVoiceModelDirectory = sharedPreferences.getString(
                KEY_LOCAL_SENSEVOICE_MODEL_DIRECTORY,
                null
            )?.trim().orEmpty().ifBlank { defaultModelDirectoryProvider().trim() }
        )
    }

    fun updateConfig(config: VoiceInputConfig) {
        sharedPreferences.edit()
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_LOCAL_SENSEVOICE_MODEL_DIRECTORY, config.localSenseVoiceModelDirectory.trim())
            .apply()
    }

    fun updateBackend(backend: VoiceInputBackend) {
        updateConfig(getConfig().copy(backend = backend))
    }

    fun updateLocalSenseVoiceModelDirectory(modelDirectory: String) {
        updateConfig(getConfig().copy(localSenseVoiceModelDirectory = modelDirectory))
    }

    fun getLocalSenseVoiceModelStatus(
        config: VoiceInputConfig = getConfig()
    ): LocalSenseVoiceModelStatus {
        val directory = config.localSenseVoiceModelDirectory.trim()
        if (directory.isBlank()) return LocalSenseVoiceModelStatus.DirectoryNotConfigured

        val missingFiles = REQUIRED_LOCAL_SENSEVOICE_FILES
            .filterNot { File(directory, it).isFile }
        return if (missingFiles.isEmpty()) {
            LocalSenseVoiceModelStatus.Ready
        } else {
            LocalSenseVoiceModelStatus.MissingFiles(missingFiles)
        }
    }

    private fun parseBackend(rawBackend: String?): VoiceInputBackend {
        return when (rawBackend) {
            VoiceInputBackend.CLOUD_HTTP_ASR.name -> VoiceInputBackend.CLOUD_HTTP_ASR
            VoiceInputBackend.LOCAL_SENSEVOICE.name,
            "LOCAL_MULTILINGUAL_ASR",
            "SYSTEM_SPEECH_RECOGNIZER",
            null,
            "" -> VoiceInputBackend.LOCAL_SENSEVOICE
            else -> VoiceInputBackend.LOCAL_SENSEVOICE
        }
    }

    companion object {
        const val PREFS_NAME = "voice_input_config"
        const val DEFAULT_MODEL_RELATIVE_DIRECTORY = "models/asr/sensevoice"

        val REQUIRED_LOCAL_SENSEVOICE_FILES = listOf(
            "model.int8.onnx",
            "tokens.txt",
            "silero_vad.onnx"
        )

        private const val KEY_BACKEND = "backend"
        private const val KEY_LOCAL_SENSEVOICE_MODEL_DIRECTORY = "local_sensevoice_model_directory"
    }
}
