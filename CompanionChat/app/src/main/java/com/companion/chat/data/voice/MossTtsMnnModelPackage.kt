package com.companion.chat.data.voice

import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import java.io.File

object MossTtsMnnModelPackage {
    const val DEFAULT_MODEL_RELATIVE_DIRECTORY = "models/tts/mnn-moss-int8"

    val REQUIRED_MODEL_FILES = listOf(
        "tts/moss_tts_prefill_nocumsum.mnn",
        "tts/moss_tts_decode_step.mnn",
        "tts/moss_tts_local_cached_step.mnn",
        "tts/tokenizer.model",
        "audio_tokenizer/moss_audio_tokenizer_encode.mnn",
        "audio_tokenizer/moss_audio_tokenizer_decode_full.mnn",
        "audio_tokenizer/moss_audio_tokenizer_decode_step.mnn"
    )

    fun inspect(modelDirectory: String): MossTtsMnnModelStatus {
        val directoryPath = modelDirectory.trim()
        if (directoryPath.isBlank()) return MossTtsMnnModelStatus.DirectoryNotConfigured

        val directory = File(directoryPath)
        if (!directory.isDirectory) {
            return MossTtsMnnModelStatus.MissingFiles(REQUIRED_MODEL_FILES)
        }

        val missingFiles = REQUIRED_MODEL_FILES.filterNot { File(directory, it).isFile }
        return if (missingFiles.isEmpty()) {
            MossTtsMnnModelStatus.Ready
        } else {
            MossTtsMnnModelStatus.MissingFiles(missingFiles)
        }
    }
}

sealed class MossTtsMnnModelStatus {
    data object Ready : MossTtsMnnModelStatus()
    data object DirectoryNotConfigured : MossTtsMnnModelStatus()
    data class MissingFiles(val fileNames: List<String>) : MossTtsMnnModelStatus()

    fun displayName(lang: AppLanguage): String = when (this) {
        is Ready -> Strings.get(lang, StringsKey.voice_ready)
        is DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_mnn_not_configured)
        is MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString(", "))
    }
}
