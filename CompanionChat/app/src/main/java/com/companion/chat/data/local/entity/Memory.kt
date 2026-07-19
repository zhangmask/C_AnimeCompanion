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
    val strength: Double = 0.3,          // 当前强度（衰减对象）
    val baseline: Double = 0.0,          // 基础强度（最低值，缓慢上升，衰减更慢）
    val source: String,
    val entityName: String? = null,
    val abstractionLevel: Int = 2,
    val l0Summary: String? = null,
    val l1Overview: String? = null,
    val sessionId: String? = null,
    val roleCardId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long = 0,
    val dailyStrengthenDelta: Double = 0.0,  // 今天已增加的量
    val lastStrengthenDate: Long = 0          // 上次强化的日期（epoch day）
)
