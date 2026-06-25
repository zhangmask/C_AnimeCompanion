package com.companion.chat.data.memory

import com.companion.chat.data.local.dao.MemoryEntityDao
import com.companion.chat.data.local.dao.MemoryLinkDao
import com.companion.chat.data.local.dao.MemoryLinkWithNeighbor
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MemoryEntity
import com.companion.chat.data.local.entity.MemoryEntityMap
import com.companion.chat.data.local.entity.MemoryLink

/**
 * 图仓库 — 封装 memory_links + memory_entities + memory_entity_map 的批量操作。
 *
 * 参考 mem0 的 Entity Store（0.95 合并阈值）+ OpenViking 的链接分发。
 */
class MemoryGraphRepository(
    private val memoryLinkDao: MemoryLinkDao,
    private val memoryEntityDao: MemoryEntityDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    // ── 实体操作 ──

    /**
     * 查找或创建实体（标准化名去重）。
     * 参考 mem0：threshold=0.95，标准化名完全匹配视为同一实体。
     */
    suspend fun findOrCreateEntity(name: String, type: String): MemoryEntity {
        val normalizedName = name.trim().lowercase()
        val existing = memoryEntityDao.findByNormalizedName(normalizedName)
        if (existing != null) {
            memoryEntityDao.incrementLinkedCount(existing.id, nowProvider())
            return existing
        }
        val id = memoryEntityDao.insert(
            MemoryEntity(
                name = name.trim(),
                normalizedName = normalizedName,
                type = type,
                linkedMemoryCount = 1,
                createdAt = nowProvider(),
                updatedAt = nowProvider()
            )
        )
        return memoryEntityDao.findByNormalizedName(normalizedName)!!
    }

    /**
     * 链接实体到记忆。
     */
    suspend fun linkEntityToMemory(entityId: Long, memoryId: Long) {
        memoryEntityDao.insertEntityMap(
            MemoryEntityMap(
                entityId = entityId,
                memoryId = memoryId
            )
        )
    }

    suspend fun getEntitiesForMemory(memoryId: Long): List<MemoryEntity> {
        return memoryEntityDao.getEntitiesForMemory(memoryId)
    }

    suspend fun getMemoriesForEntity(entityId: Long): List<Memory> {
        return memoryEntityDao.getMemoriesForEntity(entityId)
    }

    // ── 链接操作 ──

    /**
     * 添加链接（去重：同 from-to-type 取 weight max）。
     */
    suspend fun addLink(fromId: Long, toId: Long, linkType: String, weight: Double) {
        memoryLinkDao.insert(
            MemoryLink(
                fromId = fromId,
                toId = toId,
                linkType = linkType,
                weight = weight.coerceIn(0.0, 1.0),
                createdAt = nowProvider(),
                updatedAt = nowProvider()
            )
        )
    }

    /**
     * 删除记忆相关的所有链接（级联清理）。
     */
    suspend fun removeLinksForMemory(memoryId: Long) {
        memoryLinkDao.deleteAllForMemory(memoryId)
    }

    /**
     * 获取邻接记忆。
     */
    suspend fun getNeighbors(memoryId: Long, minWeight: Double = 0.3): List<MemoryLinkWithNeighbor> {
        return memoryLinkDao.getNeighbors(memoryId, minWeight)
    }
}
