package com.companion.chat.data.voice

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File

class VoiceCloneConfigRepository(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val defaultMossModelDirectoryProvider: () -> String = { "" },
    private val defaultMnnModelDirectoryProvider: () -> String = { "" }
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        defaultMossModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(MossTtsNanoModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        },
        defaultMnnModelDirectoryProvider = {
            context.applicationContext.getExternalFilesDir(MossTtsMnnModelPackage.DEFAULT_MODEL_RELATIVE_DIRECTORY)
                ?.absolutePath
                .orEmpty()
        }
    )

    fun getConfig(): VoiceCloneConfig {
        return VoiceCloneConfig(
            mossModelDirectory = sharedPreferences.getString(KEY_MOSS_MODEL_DIRECTORY, null)
                ?.trim()
                .orEmpty()
                .ifBlank { defaultMossModelDirectoryProvider().trim() },
            mnnModelDirectory = sharedPreferences.getString(KEY_MNN_MODEL_DIRECTORY, null)
                ?.trim()
                .orEmpty()
                .ifBlank { defaultMnnModelDirectoryProvider().trim() }
        )
    }

    fun updateConfig(config: VoiceCloneConfig) {
        sharedPreferences.edit()
            .putString(KEY_MOSS_MODEL_DIRECTORY, config.mossModelDirectory.trim())
            .putString(KEY_MNN_MODEL_DIRECTORY, config.mnnModelDirectory.trim())
            .apply()
    }

    fun getMossModelStatus(config: VoiceCloneConfig = getConfig()): MossTtsNanoModelStatus {
        return MossTtsNanoModelPackage.inspect(config.mossModelDirectory)
    }

    fun getMnnModelStatus(config: VoiceCloneConfig = getConfig()): MossTtsMnnModelStatus {
        return MossTtsMnnModelPackage.inspect(config.mnnModelDirectory)
    }

    /**
     * 获取默认 MOSS 参考音频 URI。
     * 优先使用用户/外部目录中的 moss_voice_clone_ref.wav（真正的参考音色），
     * 否则回退到 assets 中的 moss_default_voice.wav（测试音色）。
     *
     * @return file:// URI 字符串，失败时返回空字符串
     */
    fun getDefaultReferenceAudioUri(): String {
        return try {
            // 1) 优先使用外部 voice_clips 目录下的真实参考音频
            val externalClipsDir = File(context.getExternalFilesDir(null), "voice_clips")
            val externalRef = File(externalClipsDir, "moss_voice_clone_ref.wav")
            if (externalRef.exists()) {
                Log.i(TAG, "使用外部真实参考音频: ${externalRef.absolutePath}")
                return Uri.fromFile(externalRef).toString()
            }

            // 2) 其次使用内部 voice_defaults 目录下的真实参考音频
            val voiceDir = File(context.filesDir, "voice_defaults")
            if (!voiceDir.exists()) voiceDir.mkdirs()
            val internalRef = File(voiceDir, "moss_voice_clone_ref.wav")
            if (internalRef.exists()) {
                Log.i(TAG, "使用内部真实参考音频: ${internalRef.absolutePath}")
                return Uri.fromFile(internalRef).toString()
            }

            // 3) 回退到 assets 中的默认测试音频
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
        private const val KEY_MNN_MODEL_DIRECTORY = "mnn_model_directory"
    }
}

data class VoiceCloneConfig(
    val mossModelDirectory: String = "",
    val mnnModelDirectory: String = ""
) {
    companion object {
        /** assets/voice/ 目录下的默认 MOSS 参考音频文件名 */
        const val DEFAULT_REFERENCE_AUDIO_FILE_NAME = "moss_default_voice.wav"
    }
}
