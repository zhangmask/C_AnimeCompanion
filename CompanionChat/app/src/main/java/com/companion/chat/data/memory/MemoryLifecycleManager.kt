package com.companion.chat.data.memory

class MemoryLifecycleManager(
    private val memoryRepository: MemoryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun runStartupMaintenance() {
        val now = nowProvider()
        memoryRepository.cleanupExpiredShortTerm(now)
        memoryRepository.promoteEligibleShortTerm(now)
    }
}
