package com.companion.chat.data.memory

/**
 * 记忆系统配置常量 — 集中管理所有调优参数。
 *
 * 注意：MemoryDao.kt SQL 中的衰减率因 Room @Query 限制保持硬编码。
 */
object MemoryConfig {
    // ── 新建记忆初始强度 ──
    const val INITIAL_STRENGTH = 0.6

    // ── 衰减曲线（艾宾浩斯） ──
    const val DECAY_DAY1 = 0.70
    const val DECAY_DAY2 = 0.80
    const val DECAY_DAY3 = 0.90
    const val DECAY_DAY4_PLUS = 0.90
    const val CLEANUP_THRESHOLD = 0.05

    // ── 强化参数 ──
    const val STRENGTHEN_FTS_HIT = 0.05
    const val STRENGTHEN_LLM_CONFIRM = 0.15

    // ── PPR 检索 ──
    const val PPR_DAMPING_FACTOR = 0.85
    const val PPR_MIN_LINK_WEIGHT = 0.3
    const val PPR_MIN_SCORE = 0.05

    // ── 语义去重 ──
    const val SEMANTIC_DEDUP_THRESHOLD = 0.85f

    // ── Token 预算 ──
    const val DEFAULT_TOKEN_BUDGET = 1200
}
