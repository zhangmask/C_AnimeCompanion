package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆链接表 — 6 种类型化的有向边。
 *
 * linkType 枚举值：
 * - "related_to":   一般关联（1跳即止）
 * - "belongs_to":   归属关系（可传播最多3跳）
 * - "caused_by":    因果关系（可传播最多2跳）
 * - "derived_from": 派生关系（溯源，低权重）
 * - "contradicts":  矛盾关系（强制包含）
 * - "evolved_from": 演变关系（旧→新）
 */
@Entity(
    tableName = "memory_links",
    indices = [
        Index("fromId"),
        Index("toId"),
        Index("linkType"),
        Index("fromId", "toId", "linkType", unique = true)
    ]
)
data class MemoryLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val linkType: String,
    val weight: Double = 1.0,
    val createdAt: Long,
    val updatedAt: Long
)
