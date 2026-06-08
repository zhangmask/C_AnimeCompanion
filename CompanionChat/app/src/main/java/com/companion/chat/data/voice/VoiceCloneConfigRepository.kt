package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File

class VoiceCloneConfigRepository(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val defaultMossModelDirectoryProvider: () -> String = { "" }
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultMossModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(MossTtsNanoModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        }
    )

    fun getConfig(): VoiceCloneConfig {
        return VoiceCloneConfig(
            mossModelDirectory = sharedPreferences.getString(KEY_MOSS_MODEL_DIRECTORY, null)
                ?.trim()
                .orEmpty()
                .ifBlank { defaultMossModelDirectoryProvider().trim() }
        )
    }

    fun updateConfig(config: VoiceCloneConfig) {
        sharedPreferences.edit()
            .putString(KEY_MOSS_MODEL_DIRECTORY, config.mossModelDirectory.trim())
            .apply()
    }

    fun getMossModelStatus(config: VoiceCloneConfig = getConfig()): MossTtsNanoModelStatus {
        return MossTtsNanoModelPackage.inspect(config.mossModelDirectory)
    }

    /**
     * 获取默认 MOSS 参考音频 URI（从 assets 懒加载复制到内部存储）。
     * 用于 CLONE 模式角色未配置参考音频时的回退。
     *
     * @return file:// URI 字符串，失败时返回空字符串
     */
    fun getDefaultReferenceAudioUri(): String {
        return try {
            val voiceDir = File(context.filesDir, "voice_defaults")
            if (!voiceDir.exists()) voiceDir.mkdirs()
            val targetFile = File(voiceDir, VoiceCloneConfig.DEFAULT_REFERENCE_AUDIO_FILE_NAME)
            if (!targetFile.exists()) {
                context.assets.open("voice/${VoiceCloneConfig.DEFAULT_REFERENCE_AUDIO_FILE_NAME}").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "已复制默认 MOSS 参考音频到: ${targetFile.absolutePath}")
            }
            Uri.fromFile(targetFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取默认参考音频路径失败: ${e.message}")
            ""
        }
    }

    companion object {
        private const val TAG = "VoiceCloneConfig"
        const val PREFS_NAME = "voice_clone_config"
        private const val KEY_MOSS_MODEL_DIRECTORY = "moss_model_directory"
    }
}

data class VoiceCloneConfig(
    val mossModelDirectory: String = ""
) {
    companion object {
        /** assets/voice/ 目录下的默认 MOSS 参考音频文件名 */
        const val DEFAULT_REFERENCE_AUDIO_FILE_NAME = "moss_default_voice.wav"
    }
}
