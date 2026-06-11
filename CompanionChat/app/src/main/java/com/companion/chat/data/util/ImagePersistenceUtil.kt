package com.companion.chat.data.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 图片持久化工具类
 * 将图片复制到应用私有目录，确保应用重启后图片仍然可用
 */
object ImagePersistenceUtil {

    private const val IMAGE_DIR = "chat_images"

    /**
     * 获取图片存储目录
     */
    private fun getImageDir(context: Context): File {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 将图片复制到应用私有目录
     * @param context 上下文
     * @param sourceUri 源图片 URI
     * @return 私有目录的 URI，失败返回 null
     */
    fun persistImage(context: Context, sourceUri: Uri): Uri? {
        return try {
            val imageDir = getImageDir(context)
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val destFile = File(imageDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Uri.fromFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 批量持久化图片
     * @param context 上下文
     * @param uris 源图片 URI 列表
     * @return 持久化后的 URI 列表
     */
    fun persistImages(context: Context, uris: List<Uri>): List<Uri> {
        return uris.mapNotNull { persistImage(context, it) }
    }

    /**
     * 检查图片是否在私有目录中
     */
    fun isPersisted(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return path.contains(IMAGE_DIR)
    }

    /**
     * 清理过期的图片文件
     * @param context 上下文
     * @param maxAgeMillis 最大保留时间（毫秒）
     */
    fun cleanupOldImages(context: Context, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L) {
        try {
            val imageDir = getImageDir(context)
            val now = System.currentTimeMillis()
            imageDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAgeMillis) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
