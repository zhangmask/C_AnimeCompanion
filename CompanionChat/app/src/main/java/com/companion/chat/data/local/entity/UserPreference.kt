package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_preferences",
    indices = [Index(value = ["roleCardId"])]
)
data class UserPreference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val confidence: Int = 1,
    val roleCardId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
