package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["roleCardId"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val roleCardId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isUserRenamed: Boolean = false
)
