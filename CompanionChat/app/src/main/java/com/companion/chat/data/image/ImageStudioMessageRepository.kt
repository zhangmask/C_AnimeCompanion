package com.companion.chat.data.image

import com.companion.chat.data.local.dao.ImageStudioMessageDao
import com.companion.chat.data.local.entity.ImageStudioMessageEntity

/**
 * 图像工作室消息持久化仓库。
 *
 * 负责加载/保存/删除 ImageStudio 的多轮对话消息（含图片 URI 与引用关系），
 * 使生成历史在应用重启后仍可恢复。
 */
class ImageStudioMessageRepository(
    private val dao: ImageStudioMessageDao
) {

    suspend fun loadMessages(roleCardId: Long): List<ImageStudioMessageEntity> {
        return dao.getByRoleCardId(roleCardId)
    }

    suspend fun nextPosition(roleCardId: Long): Int {
        return (dao.maxPosition(roleCardId) ?: -1) + 1
    }

    suspend fun saveMessage(message: ImageStudioMessageEntity) {
        dao.upsert(message)
    }

    suspend fun deleteMessage(id: String) {
        dao.deleteById(id)
    }

    suspend fun updateResult(id: String, imageUri: String?, isError: Boolean, errorMessage: String?) {
        dao.updateResult(id, imageUri, isError, errorMessage)
    }
}
