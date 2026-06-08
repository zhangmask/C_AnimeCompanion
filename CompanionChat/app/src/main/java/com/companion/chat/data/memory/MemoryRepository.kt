package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val extractor: MemoryExtractor = RuleBasedMemoryExtractor(),
    private val retriever: MemoryRetriever = MemoryRetriever(memoryDao),
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun extractAndStoreMemories(userMessage: String, sessionId: String): List<Memory> {
        val extractedMemories = extractor.extract(
            userMessage = userMessage,
            sessionId = sessionId
        )
        return storeExtractedMemories(extractedMemories, sessionId)
    }

    suspend fun extractAndStoreMemoriesFromMessages(
        userMessages: List<String>,
        sessionId: String
    ): List<Memory> {
        val extractedMemories = userMessages.flatMap { message ->
            extractor.extract(
                userMessage = message,
                sessionId = sessionId
            )
        }
        return storeExtractedMemories(extractedMemories, sessionId)
    }

    suspend fun storeModelExtractedMemories(
        extractedMemories: List<ExtractedMemory>,
        sessionId: String
    ): List<Memory> {
        return storeExtractedMemories(extractedMemories, sessionId)
    }

    suspend fun retrieveRelevantMemories(userMessage: String): List<Memory> {
        return retriever.retrieveRelevantMemories(userMessage)
    }

    suspend fun getPersistentMemories(): List<Memory> {
        return memoryDao.getPersistentMemories()
    }

    suspend fun getAllMemories(): List<Memory> {
        return memoryDao.getAll()
    }

    fun observeAllMemories(): Flow<List<Memory>> {
        return memoryDao.observeAll()
    }

    suspend fun addManualMemory(content: String, category: String): Memory {
        val now = nowProvider()
        val memory = Memory(
            content = content.trim(),
            category = category,
            layer = LONG_TERM_LAYER,
            source = MANUAL_SOURCE,
            referenceCount = 0,
            sessionId = null,
            createdAt = now,
            updatedAt = now,
            expiresAt = null
        )
        val insertedId = memoryDao.insert(memory)
        return memory.copy(id = insertedId)
    }

    suspend fun updateMemory(memory: Memory) {
        memoryDao.update(
            memory.copy(
                content = memory.content.trim(),
                updatedAt = nowProvider()
            )
        )
    }

    suspend fun deleteMemory(memory: Memory) {
        memoryDao.delete(memory)
    }

    suspend fun promoteMemory(memoryId: Long, now: Long = nowProvider()): Boolean {
        return memoryDao.promoteToLongTerm(memoryId, now) > 0
    }

    suspend fun cleanupExpiredShortTerm(now: Long = nowProvider()): Int {
        return memoryDao.cleanupExpiredShortTerm(now)
    }

    suspend fun promoteEligibleShortTerm(now: Long = nowProvider()): Int {
        val promotableMemories = memoryDao.getPromotableShortTerm()
        promotableMemories.forEach { memoryDao.promoteToLongTerm(it.id, now) }
        return promotableMemories.size
    }

    private suspend fun storeExtractedMemories(
        extractedMemories: List<ExtractedMemory>,
        sessionId: String
    ): List<Memory> {
        if (extractedMemories.isEmpty()) {
            return emptyList()
        }

        val now = nowProvider()
        val storedMemories = mutableListOf<Memory>()
        extractedMemories.forEach { extractedMemory ->
            val content = extractedMemory.content.trim()
            val category = extractedMemory.category.trim()
            if (content.isBlank() || category.isBlank()) {
                return@forEach
            }

            val existing = memoryDao.findExactMatch(category, content)
            if (existing != null) {
                storedMemories += existing
                return@forEach
            }

            val memoryToInsert = Memory(
                content = content,
                category = category,
                layer = extractedMemory.layer,
                source = extractedMemory.source,
                referenceCount = 0,
                sessionId = sessionId,
                createdAt = now,
                updatedAt = now,
                expiresAt = extractedMemory.expiresAt ?: now + SHORT_TERM_TTL_MILLIS
            )
            val insertedId = memoryDao.insert(memoryToInsert)
            storedMemories += memoryToInsert.copy(id = insertedId)
        }
        return storedMemories
    }

    companion object {
        const val SHORT_TERM_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val LONG_TERM_LAYER = "long_term"
        private const val MANUAL_SOURCE = "manual"
        const val MODEL_SOURCE = "model_extractor"
    }
}
