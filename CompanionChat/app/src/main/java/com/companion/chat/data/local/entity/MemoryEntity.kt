package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 实体表 — 标准化实体存储。
 *
 * 参考 mem0 的 Entity Store：
 * - normalizedName 唯一索引，用于去重合并（threshold=0.95）
 * - linkedMemoryCount 影响实体提升权重（越多提升越小）
 */
@Entity(
    tableName = "memory_entities",
    indices = [Index("name", unique = true), Index("normalizedName")]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val type: String,                 // "person" / "org" / "topic" / "concept"
    val linkedMemoryCount: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)
