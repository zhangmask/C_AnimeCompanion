package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [Index(value = ["roleCardId"])]
)
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String,
    val layer: String,
    val source: String,
    val referenceCount: Int = 0,
    val sessionId: String? = null,
    val roleCardId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null
)
