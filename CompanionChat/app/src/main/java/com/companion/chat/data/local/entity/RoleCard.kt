package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "role_cards")
data class RoleCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val avatar: String = "person",
    val persona: String,
    val speakingStyle: String = "",
    val background: String = "",
    val rules: String = "",
    val taboos: String = "",
    val openingMessage: String = "",
    val exampleDialogue: String = "",
    val avatarImageUri: String = "",
    val galleryImageUris: List<String> = emptyList(),
    val imageStylePrompt: String = "",
    val voiceProfileUri: String = "",
    val voiceMode: String = "CLONE",
    val voiceDisplayName: String = "",
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
