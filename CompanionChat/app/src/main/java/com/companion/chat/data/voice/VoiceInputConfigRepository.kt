package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class VoiceInputConfigRepository(
    private val sharedPreferences: SharedPreferences,
    private val defaultModelDirectoryProvider: () -> String = { "" },
    private val defaultMnnModelDirectoryProvider: () -> String = { "" }
) {
    constructor(context: Context) : this(
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(DEFAULT_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        },
        defaultMnnModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(DEFAULT_MNN_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        }
    )

    fun getConfig(): VoiceInputConfig {
        val backend = parseBackend(sharedPreferences.getString(KEY_BACKEND, VoiceInputBackend.LOCAL_SENSEVOICE.name))
        val savedDir = sharedPreferences.getString(KEY_LOCAL_SENSEVOICE_MODEL_DIRECTORY, null)?.trim().orEmpty()
        val defaultDir = if (backend == VoiceInputBackend.LOCAL_MNN_SENSEVOICE) {
            defaultMnnModelDirectoryProvider().trim()
        } else {
            defaultModelDirectoryProvider().trim()
        }
        return VoiceInputConfig(
            backend = backend,
            localSenseVoiceModelDirectory = savedDir.ifBlank { defaultDir }
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

        val requiredFiles = when (config.backend) {
            VoiceInputBackend.LOCAL_MNN_SENSEVOICE -> REQUIRED_MNN_SENSEVOICE_FILES
            else -> REQUIRED_LOCAL_SENSEVOICE_FILES
        }
        val missingFiles = requiredFiles
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
            VoiceInputBackend.LOCAL_MNN_SENSEVOICE.name -> VoiceInputBackend.LOCAL_MNN_SENSEVOICE
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
        const val DEFAULT_MNN_MODEL_RELATIVE_DIRECTORY = "models/asr/sensevoice-mnn"

        val REQUIRED_LOCAL_SENSEVOICE_FILES = listOf(
            "model.int8.onnx",
            "tokens.txt",
            "silero_vad.onnx"
        )

        val REQUIRED_MNN_SENSEVOICE_FILES = listOf(
            "sensevoice_wq8.mnn",
            "tokens.txt"
        )

        private const val KEY_BACKEND = "backend"
        private const val KEY_LOCAL_SENSEVOICE_MODEL_DIRECTORY = "local_sensevoice_model_directory"
    }
}
