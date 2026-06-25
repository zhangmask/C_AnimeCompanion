package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 元记忆 — "如何使用记忆"的策略。
 *
 * 参考 MetaMem 的轻量版实现：
 * - 由 T+1 批量生成，不在实时路径中产生 LLM 开销
 * - 注入 prompt 时作为元指导
 *
 * category: "retrieval" / "reasoning" / "conflict"
 */
@Entity(tableName = "meta_memories")
data class MetaMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val category: String = "retrieval",
    val applyCount: Int = 0,
    val confidence: Double = 0.5,
    val createdAt: Long,
    val updatedAt: Long
)
