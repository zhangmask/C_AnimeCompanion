package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.companion.chat.data.local.entity.MemoryLink

/**
 * 记忆链接 DAO — 管理 memory_links 表的 CRUD 和 PPR 邻接查询。
 */
@Dao
interface MemoryLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: MemoryLink): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<MemoryLink>)

    @Delete
    suspend fun delete(link: MemoryLink)

    @Query("DELETE FROM memory_links WHERE fromId = :memoryId OR toId = :memoryId")
    suspend fun deleteAllForMemory(memoryId: Long)

    /**
     * PPR 邻接查询：同时获取出边和入边邻居。
     */
    @Query("""
        SELECT ml.*, m.content, m.category, m.strength, m.l0Summary
        FROM memory_links ml
        JOIN memories m ON m.id = ml.toId
        WHERE ml.fromId = :memoryId AND ml.weight >= :minWeight
        UNION ALL
        SELECT ml.*, m.content, m.category, m.strength, m.l0Summary
        FROM memory_links ml
        JOIN memories m ON m.id = ml.fromId
        WHERE ml.toId = :memoryId AND ml.weight >= :minWeight
    """)
    suspend fun getNeighbors(memoryId: Long, minWeight: Double = 0.3): List<MemoryLinkWithNeighbor>

    @Query("SELECT * FROM memory_links WHERE linkType = :linkType")
    suspend fun getByType(linkType: String): List<MemoryLink>

    @Query("SELECT COUNT(*) FROM memory_links WHERE fromId = :memoryId OR toId = :memoryId")
    suspend fun getLinkCount(memoryId: Long): Int
}

/**
 * 带邻居内容的链接结果（用于 PPR 检索，不需要独立 Room Entity）。
 */
data class MemoryLinkWithNeighbor(
    val id: Long,
    val fromId: Long,
    val toId: Long,
    val linkType: String,
    val weight: Double,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val content: String,
    val category: String,
    val strength: Double,
    val l0Summary: String?
)
