package com.companion.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 经验记忆 — 从多轮对话归纳的可复用执行经验。
 *
 * 参考 OpenViking 的 Trajectory→Experience 管道：
 * - T+1 批量生成，不从对话实时提取
 * - Situation / Approach / Reflect 三段式
 */
@Entity(tableName = "agent_experiences")
data class AgentExperience(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val situation: String,
    val approach: String,
    val reflect: String,
    val outcome: String = "success",
    val applyCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
