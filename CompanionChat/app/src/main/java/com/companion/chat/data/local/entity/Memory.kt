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
    val strength: Double = 0.6,         // 0.0 ~ 1.0，替代 layer
    val source: String,
    val entityName: String? = null,     // 标准化实体名（如 "user_张三"）
    val abstractionLevel: Int = 2,      // 0=L0摘要 / 1=L1概览 / 2=L2详情
    val l0Summary: String? = null,      // 一句话摘要（~50 tokens）
    val l1Overview: String? = null,     // 概览（~200 tokens）
    val sessionId: String? = null,
    val roleCardId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long = 0        // 最后被检索/提及时间
)
