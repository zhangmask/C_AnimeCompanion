package com.companion.chat.data.memory

object MemoryConfig {
    // ── 新建记忆初始强度 ──
    // strength=0.3 短期起点；baseline=0.0 需多次提及逐步上升
    const val INITIAL_STRENGTH = 0.3
    const val INITIAL_BASELINE = 0.0

    // ── 衰减曲线（艾宾浩斯，双值模型） ──
    // strength 每天衰减，但不低于 baseline
    // baseline 每天乘 0.95 缓慢衰减（比 strength 慢得多）
    const val DECAY_DAY1 = 0.70
    const val DECAY_DAY2 = 0.80
    const val DECAY_DAY3 = 0.90
    const val DECAY_DAY4_PLUS = 0.90
    const val BASELINE_DECAY_RATE = 0.95
    const val CLEANUP_THRESHOLD = 0.05

    // ── 强化参数 ──
    // FTS 检索命中 +0.1：用户提到相关话题时回升
    // LLM 确认 +0.2：提取时发现已有相似记忆，确认仍有效
    // 每日上限 0.4：同一条记忆一天最多增加 0.4
    const val STRENGTHEN_FTS_HIT = 0.1
    const val STRENGTHEN_LLM_CONFIRM = 0.2
    const val DAILY_STRENGTHEN_LIMIT = 0.4
    // baseline 每次强化只增加 delta 的 30%
    const val BASELINE_GROWTH_RATIO = 0.3
    const val BASELINE_MAX = 0.8

    // ── PPR 检索 ──
    const val PPR_DAMPING_FACTOR = 0.85
    const val PPR_MIN_LINK_WEIGHT = 0.3
    const val PPR_MIN_SCORE = 0.05

    // ── 语义去重 ──
    const val SEMANTIC_DEDUP_THRESHOLD = 0.85f

    // ── Token 预算 ──
    const val DEFAULT_TOKEN_BUDGET = 1200
}
