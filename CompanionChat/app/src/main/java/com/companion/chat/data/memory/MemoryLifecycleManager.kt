package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.MemoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 记忆生命周期管理器 — 每天一次衰减调度 + 清理弱记忆 + T+1 批处理。
 *
 * 改造后：
 * - 废除旧晋升机制（short_term→long_term）
 * - 改由 MemoryDecayManager 做每日分段衰减
 * - 维护间隔从 30 分钟改为 24 小时
 * - 新增 T+1 批处理触发点
 */
class MemoryLifecycleManager(
    private val memoryRepository: MemoryRepository,
    private val decayManager: MemoryDecayManager? = null,
    private val t1BatchProcessor: T1BatchProcessor? = null,
    private val scope: CoroutineScope? = null,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var periodicJob: Job? = null

    /**
     * 每日衰减调度（应在每日首次启动时调用）。
     */
    suspend fun runDailyDecay() {
        val manager = decayManager ?: return
        val decayed = manager.applyDailyDecay()
        val cleaned = manager.cleanupWeakMemories(MemoryConfig.CLEANUP_THRESHOLD)
        android.util.Log.d(LOG_TAG, "每日衰减: decayed=$decayed, cleaned=$cleaned")

        // T+1 批处理
        t1BatchProcessor?.processT1Batch()
    }

    /**
     * 启动周期性维护：每 24 小时衰减 + 清理。
     */
    fun startPeriodicMaintenance() {
        val s = scope ?: return
        periodicJob?.cancel()
        periodicJob = s.launch {
            while (true) {
                delay(MAINTENANCE_INTERVAL_MILLIS)
                try {
                    runDailyDecay()
                } catch (e: Exception) {
                    android.util.Log.e("MemoryLifecycle", "每日衰减执行失败", e)
                }
            }
        }
    }

    fun stopPeriodicMaintenance() {
        periodicJob?.cancel()
        periodicJob = null
    }

    companion object {
        private const val LOG_TAG = "MemoryLifecycle"
        private const val MAINTENANCE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    }
}
