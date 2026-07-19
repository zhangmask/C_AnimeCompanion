package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_api_configs")
data class CustomApiConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val apiFormat: String = "OPENAI",
    val customParams: String = "{}",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
