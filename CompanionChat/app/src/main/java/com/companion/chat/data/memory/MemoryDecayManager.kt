package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.MemoryDao

/**
 * 衰减调度器 — 每日按遗忘曲线衰减记忆强度。
 *
 * 遗忘曲线（近似艾宾浩斯，2 周完全遗忘）：
 *   idleDays=1:  S *= 0.70
 *   idleDays=2:  S *= 0.80
 *   idleDays=3:  S *= 0.90
 *   idleDays≥4:  S *= 0.90/天
 *
 * 从 strength=0.6 起，约 14 天跌至 0.1 以下 → 自动清理。
 */
class MemoryDecayManager(
    private val memoryDao: MemoryDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * 每日衰减调度：遍历所有 strength > 0.05 的记忆，按 idle 天数衰减。
     */
    suspend fun applyDailyDecay(): Int {
        val now = nowProvider()
        val allMemories = memoryDao.getActiveMemories(0.05)
        var count = 0
        for (memory in allMemories) {
            val idleDays = (now - memory.lastAccessedAt) / DAY_MILLIS
            if (idleDays >= 1) {
                memoryDao.applyDecayByAge(memory.id, idleDays.toInt(), now)
                count++
            }
        }
        return count
    }

    /**
     * 强化记忆：被 LLM 确认有价值或 FTS 检索命中时调用。
     */
    suspend fun strengthenMemory(memoryId: Long, delta: Double = 0.15) {
        memoryDao.strengthen(memoryId, delta, nowProvider())
    }

    /**
     * 清理弱记忆：删除强度低于阈值的记忆。
     */
    suspend fun cleanupWeakMemories(threshold: Double = 0.05): Int {
        return memoryDao.deleteByStrengthBelow(threshold)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
