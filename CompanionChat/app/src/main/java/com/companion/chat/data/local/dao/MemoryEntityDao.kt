package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MemoryEntity
import com.companion.chat.data.local.entity.MemoryEntityMap

/**
 * 实体 DAO — 管理 memory_entities + memory_entity_map 表。
 */
@Dao
interface MemoryEntityDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MemoryEntity): Long

    @Query("SELECT * FROM memory_entities WHERE normalizedName = :name LIMIT 1")
    suspend fun findByNormalizedName(name: String): MemoryEntity?

    @Query("UPDATE memory_entities SET linkedMemoryCount = linkedMemoryCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementLinkedCount(id: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntityMap(mapEntry: MemoryEntityMap)

    @Query("SELECT me.* FROM memory_entities me JOIN memory_entity_map mem ON me.id = mem.entityId WHERE mem.memoryId = :memoryId")
    suspend fun getEntitiesForMemory(memoryId: Long): List<MemoryEntity>

    @Query("SELECT m.* FROM memories m JOIN memory_entity_map mem ON m.id = mem.memoryId WHERE mem.entityId = :entityId")
    suspend fun getMemoriesForEntity(entityId: Long): List<Memory>
}
