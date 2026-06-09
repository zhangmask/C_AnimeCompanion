package com.companion.chat.data.profile

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class UserAvatarStore(private val context: Context) {

    private val avatarsDir: File
        get() = File(context.filesDir, "user_avatars").apply { mkdirs() }

    fun persistUri(sourceUri: Uri): String? {
        return try {
            val ext = resolveExtension(sourceUri)
            val fileName = "user_avatar_${UUID.randomUUID()}$ext"
            val dest = File(avatarsDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.toURI().toString()
        } catch (_: Exception) {
            null
        }
    }

    fun delete(avatarUri: String) {
        if (avatarUri.isBlank()) return
        try {
            val uri = java.net.URI(avatarUri)
            if (uri.scheme == "file") {
                val file = File(uri)
                if (file.parentFile?.name == "user_avatars") {
                    file.delete()
                }
            }
        } catch (_: Exception) { }
    }

    private fun resolveExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("png") == true -> ".png"
            mimeType?.contains("webp") == true -> ".webp"
            mimeType?.contains("gif") == true -> ".gif"
            else -> ".jpg"
        }
    }
}
