package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 按角色+文本内容缓存 TTS 合成的 WAV 文件。
 * key 格式：role|contentHash，保证同角色同一段文本只合成一次。
 */
@Entity(
    tableName = "tts_audio_cache",
    indices = [Index(value = ["cacheKey"], unique = true)]
)
data class TtsAudioCacheEntity(
    @PrimaryKey val cacheKey: String,
    val role: String,
    val contentHash: String,
    val audioUri: String,
    val textPreview: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
