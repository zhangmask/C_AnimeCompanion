package com.companion.chat.data.role

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Persists role avatar images to the app's internal storage so they survive
 * content:// URI permission revocation and app restarts.
 *
 * Stored files live under filesDir/role_avatars/ and are referenced via file:// URIs
 * that Coil AsyncImage can load directly.
 */
class RoleAvatarStore(private val context: Context) {

    private val avatarsDir: File
        get() = File(context.filesDir, "role_avatars").apply { mkdirs() }

    /**
     * Copies the image at [sourceUri] into persistent internal storage.
     * Returns a file:// URI string, or null if the copy fails.
     */
    fun persistUri(sourceUri: Uri): String? {
        return try {
            val ext = resolveExtension(sourceUri)
            val fileName = "avatar_${UUID.randomUUID()}$ext"
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

    /**
     * Deletes the persisted file for the given avatarUri (if it lives in our dir).
     */
    fun delete(avatarUri: String) {
        if (avatarUri.isBlank()) return
        try {
            val uri = java.net.URI(avatarUri)
            if (uri.scheme == "file") {
                val file = File(uri)
                if (file.parentFile?.name == "role_avatars") {
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
