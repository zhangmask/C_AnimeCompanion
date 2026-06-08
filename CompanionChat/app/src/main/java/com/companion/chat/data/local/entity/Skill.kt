package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skills")
data class Skill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val icon: String = "default",
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val usageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
