package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 图像工作室消息持久化实体。
 *
 * 存储用户在 ImageStudio 中生成的每一条消息（含图片 URI、引用关系），
 * 使多轮对话、图片形象、引用修改在应用重启后仍可恢复。
 */
@Entity(
    tableName = "image_studio_messages",
    foreignKeys = [
        ForeignKey(
            entity = RoleCard::class,
            parentColumns = ["id"],
            childColumns = ["roleCardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roleCardId"])]
)
data class ImageStudioMessageEntity(
    @PrimaryKey val id: String,
    val roleCardId: Long,
    /** 用户原始输入 */
    val prompt: String,
    /** 完整 prompt（含 stylePrefix / reference） */
    val fullPrompt: String,
    /** 生成图片的文件 URI；null 表示尚未生成或失败 */
    val imageUri: String?,
    val isError: Boolean,
    val errorMessage: String?,
    /** 引用的上一条消息 ID（引用修改功能持久化） */
    val referenceMessageId: String?,
    val timestamp: Long,
    /** 列表排序位置 */
    val position: Int
)
