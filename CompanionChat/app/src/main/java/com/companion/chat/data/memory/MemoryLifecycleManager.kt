package com.companion.chat.data.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MemoryLifecycleManager(
    private val memoryRepository: MemoryRepository,
    private val scope: CoroutineScope? = null,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var periodicJob: Job? = null

    suspend fun runStartupMaintenance() {
        val now = nowProvider()
        memoryRepository.cleanupExpiredShortTerm(now)
        memoryRepository.promoteEligibleShortTerm(now)
    }

    /**
     * 启动周期性维护：每 30 分钟清理过期短期记忆、晋升符合条件的短期记忆
     */
    fun startPeriodicMaintenance() {
        val s = scope ?: return
        periodicJob?.cancel()
        periodicJob = s.launch {
            while (true) {
                delay(MAINTENANCE_INTERVAL_MILLIS)
                runCatching {
                    runStartupMaintenance()
                }
            }
        }
    }

    fun stopPeriodicMaintenance() {
        periodicJob?.cancel()
        periodicJob = null
    }

    companion object {
        private const val MAINTENANCE_INTERVAL_MILLIS = 30L * 60 * 1000
    }
}
