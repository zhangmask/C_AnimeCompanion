package com.companion.chat.data.voice

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VoiceClipInfo(
    val displayName: String,
    val uri: String,
    val durationLabel: String,
    val uploadedLabel: String,
    val file: File
)

class VoiceClipScanner(private val context: Context) {

    private companion object {
        const val TAG = "VoiceClipScanner"
        const val VOICE_CLIPS_DIR = "voice_clips"
    }

    fun getVoiceClipsDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), VOICE_CLIPS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun scanClips(): List<VoiceClipInfo> {
        val dir = getVoiceClipsDirectory()
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = System.currentTimeMillis()

        return dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("wav", "mp3", "ogg", "flac", "m4a")
        }?.sortedByDescending { it.lastModified() }?.map { file ->
            val nameWithoutExt = file.nameWithoutExtension
                .replace("_", " ")
                .replaceFirstChar { it.uppercaseChar() }
            val daysAgo = ((now - file.lastModified()) / (1000L * 60 * 60 * 24)).toInt()
            val uploadedLabel = when {
                daysAgo == 0 -> "今天上传"
                daysAgo == 1 -> "昨天上传"
                daysAgo < 7 -> "$daysAgo 天前上传"
                daysAgo < 30 -> "${daysAgo / 7} 周前上传"
                else -> dateFormat.format(Date(file.lastModified()))
            }
            val uriString = Uri.fromFile(file).toString()
            VoiceClipInfo(
                displayName = nameWithoutExt,
                uri = uriString,
                durationLabel = "", // duration requires MediaMetadataRetriever, skip for now
                uploadedLabel = uploadedLabel,
                file = file
            )
        } ?: emptyList()
    }

    /**
     * Copy an audio file from a content URI (picked by user) into the voice clips directory.
     * Returns the VoiceClipInfo for the copied file, or null on failure.
     */
    fun importClipFromUri(sourceUri: Uri, displayName: String? = null): VoiceClipInfo? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return null
            val mimeType = context.contentResolver.getType(sourceUri) ?: "audio/wav"
            val ext = when {
                mimeType.contains("wav") -> "wav"
                mimeType.contains("mp3") -> "mp3"
                mimeType.contains("ogg") -> "ogg"
                mimeType.contains("flac") -> "flac"
                mimeType.contains("m4a") || mimeType.contains("mp4") -> "m4a"
                else -> "wav"
            }
            val fileName = (displayName ?: "clip_${System.currentTimeMillis()}")
                .replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fff\\-_ ]"), "")
                .trim()
                .ifBlank { "clip_${System.currentTimeMillis()}" }
            val destFile = File(getVoiceClipsDirectory(), "$fileName.$ext")
            // Avoid overwriting
            val finalFile = if (destFile.exists()) {
                File(getVoiceClipsDirectory(), "${fileName}_${System.currentTimeMillis()}.$ext")
            } else {
                destFile
            }
            FileOutputStream(finalFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            Log.i(TAG, "Imported voice clip: ${finalFile.absolutePath}")
            val uriString = Uri.fromFile(finalFile).toString()
            VoiceClipInfo(
                displayName = finalFile.nameWithoutExtension
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercaseChar() },
                uri = uriString,
                durationLabel = "",
                uploadedLabel = "今天上传",
                file = finalFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import voice clip: ${e.message}", e)
            null
        }
    }

    /**
     * Returns the URI of the first available voice clip, or empty string if none.
     * Used as default voice profile URI for MOSS clone roles.
     */
    fun getDefaultClipUri(): String {
        val clips = scanClips()
        return clips.firstOrNull()?.uri ?: ""
    }
}
