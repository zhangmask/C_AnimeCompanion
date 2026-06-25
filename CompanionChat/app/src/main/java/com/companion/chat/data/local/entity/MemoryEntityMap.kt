package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 实体 ↔ 记忆 多对多关联表。
 */
@Entity(
    tableName = "memory_entity_map",
    indices = [Index("entityId"), Index("memoryId")]
)
data class MemoryEntityMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: Long,
    val memoryId: Long
)
