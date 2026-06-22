package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.local.entity.Memory
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<Memory>): List<Long>

    @Update
    suspend fun update(memory: Memory)

    @Delete
    suspend fun delete(memory: Memory)

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Memory>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE layer = :layer ORDER BY updatedAt DESC")
    suspend fun getByLayer(layer: String): List<Memory>

    @Query("SELECT * FROM memories WHERE layer = 'long_term' ORDER BY updatedAt DESC")
    suspend fun getPersistentMemories(): List<Memory>

    @Query("SELECT * FROM memories WHERE (roleCardId = :roleCardId OR roleCardId IS NULL) AND layer = 'long_term' ORDER BY updatedAt DESC")
    suspend fun getPersistentMemoriesForRole(roleCardId: Long): List<Memory>

    @Query("SELECT * FROM memories WHERE roleCardId IS NULL AND layer = 'long_term' ORDER BY updatedAt DESC")
    suspend fun getGlobalPersistentMemories(): List<Memory>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<Memory>

    @Query(
        """
        SELECT * FROM memories
        WHERE category = :category AND content = :content
        LIMIT 1
        """
    )
    suspend fun findExactMatch(category: String, content: String): Memory?

    @RawQuery(observedEntities = [Memory::class])
    suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory>

    @Query("UPDATE memories SET referenceCount = referenceCount + 1 WHERE id = :id")
    suspend fun incrementReference(id: Long): Int

    @Query("UPDATE memories SET layer = 'long_term', updatedAt = :now WHERE id = :id")
    suspend fun promoteToLongTerm(id: Long, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE memories SET roleCardId = NULL, updatedAt = :now WHERE id = :id")
    suspend fun promoteToGlobal(id: Long, now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM memories WHERE layer = 'short_term' AND expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun cleanupExpiredShortTerm(now: Long): Int

    @Query("SELECT * FROM memories WHERE layer = 'short_term' AND referenceCount >= 5")
    suspend fun getPromotableShortTerm(): List<Memory>
}
