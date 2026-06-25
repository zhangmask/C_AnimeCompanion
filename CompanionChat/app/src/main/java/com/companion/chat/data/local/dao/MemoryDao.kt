package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
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

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<Memory>

    @Query("SELECT * FROM memories WHERE category = :category AND content = :content LIMIT 1")
    suspend fun findExactMatch(category: String, content: String): Memory?

    @Query("SELECT * FROM memories WHERE strength > :minStrength ORDER BY strength DESC, lastAccessedAt DESC")
    suspend fun getActiveMemories(minStrength: Double = 0.05): List<Memory>

    // ── 强度衰减（按 idle 天数分段） ──
    @Query("""
        UPDATE memories SET strength = CASE
            WHEN :idleDays >= 4 THEN ROUND(strength * 0.90, 4)
            WHEN :idleDays = 3  THEN ROUND(strength * 0.90, 4)
            WHEN :idleDays = 2  THEN ROUND(strength * 0.80, 4)
            WHEN :idleDays = 1  THEN ROUND(strength * 0.70, 4)
            ELSE strength
        END, updatedAt = :now, lastAccessedAt = :now
        WHERE id = :id AND strength > 0.05
    """)
    suspend fun applyDecayByAge(id: Long, idleDays: Int, now: Long)

    // ── 强化（只更新 strength 和 lastAccessedAt，不误改 updatedAt） ──
    @Query("""
        UPDATE memories
        SET strength = MIN(1.0, strength + :delta), lastAccessedAt = :now
        WHERE id = :id
    """)
    suspend fun strengthen(id: Long, delta: Double, now: Long)

    // ── 清理弱记忆 ──
    @Query("DELETE FROM memories WHERE strength < :threshold")
    suspend fun deleteByStrengthBelow(threshold: Double): Int

    // ── FTS 检索（加 LIMIT） ──
    // 注意：memories_fts 是运行时创建的 FTS4 虚拟表，Room 无法编译期验证，故使用 @RawQuery
    @RawQuery(observedEntities = [Memory::class])
    suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory>

    // ── FTS 检索 + 角色过滤 ──
    @RawQuery(observedEntities = [Memory::class])
    suspend fun searchByFTSWithRole(query: SupportSQLiteQuery): List<Memory>
}

/**
 * FTS 查询构建器 — 隔离 @RawQuery 调用方的构造逻辑。
 */
object FtsQueryHelper {
    /** 构建 FTS 查询（按 strength 排序 + LIMIT）。 */
    fun buildFtsQuery(expression: String, limit: Int = 5): SupportSQLiteQuery {
        val sql = """
            SELECT memories.* FROM memories
            JOIN memories_fts ON memories.id = memories_fts.docid
            WHERE memories_fts MATCH ?
            ORDER BY memories.strength DESC, memories.lastAccessedAt DESC
            LIMIT ?
        """.trimIndent()
        return SimpleSQLiteQuery(sql, arrayOf<Any>(expression, limit))
    }

    /** 构建 FTS 查询（按 strength 排序 + LIMIT + 角色过滤）。 */
    fun buildFtsQueryWithRole(expression: String, roleCardId: Long, limit: Int = 5): SupportSQLiteQuery {
        val sql = """
            SELECT memories.* FROM memories
            JOIN memories_fts ON memories.id = memories_fts.docid
            WHERE memories_fts MATCH ?
              AND (memories.roleCardId IS NULL OR memories.roleCardId = ?)
            ORDER BY memories.strength DESC, memories.lastAccessedAt DESC
            LIMIT ?
        """.trimIndent()
        return SimpleSQLiteQuery(sql, arrayOf<Any>(expression, roleCardId, limit))
    }
}
