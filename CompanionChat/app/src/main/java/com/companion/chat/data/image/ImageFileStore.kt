package com.companion.chat.data.image

import android.content.Context
import java.io.File

/**
 * Saves generated images to the app's internal storage directory.
 * Images persist across app restarts (only cleared if user explicitly clears app data).
 * URIs are file:// URIs that work directly with Coil AsyncImage.
 */
class ImageFileStore(
    private val context: Context
) {
    fun saveBytes(bytes: ByteArray, purpose: ImageGenerationPurpose): String {
        val file = nextImageFile(purpose)
        file.writeBytes(bytes)
        return file.toURI().toString()
    }

    /**
     * Returns the next image File to be written, without writing it yet.
     * Used when the caller needs the file path before generation (e.g. for
     * pre-allocating a latents path for img2img).
     */
    fun nextImageFile(purpose: ImageGenerationPurpose): File {
        val dir = File(context.filesDir, "generated_images/${purpose.name.lowercase()}").apply {
            mkdirs()
        }
        return File(dir, "image_${System.currentTimeMillis()}.png")
    }

    /**
     * Derives the latents file path from an image file path or URI.
     * e.g. "image_123.png" → "image_123.latents.bin"
     */
    fun latentsPathFor(imageFile: File): String =
        imageFile.absolutePath.removeSuffix(".png") + ".latents.bin"

    /**
     * Converts a file:// URI string to a raw filesystem path for native code.
     * Returns empty string if the URI is not a file URI.
     */
    fun uriToPath(uri: String): String {
        return try {
            val f = File(android.net.Uri.parse(uri).path ?: return "")
            if (f.exists()) f.absolutePath else ""
        } catch (e: Exception) { "" }
    }

    /**
     * Derives the latents file path from an image URI string.
     * e.g. "file:/.../image_123.png" → "/.../image_123.latents.bin"
     * Returns empty string if the URI is not valid.
     */
    fun latentsPathForUri(imageUri: String): String {
        val path = uriToPath(imageUri)
        if (path.isEmpty()) return ""
        return path.removeSuffix(".png") + ".latents.bin"
    }
}
