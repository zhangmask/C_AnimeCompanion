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

    @Query("SELECT * FROM memories WHERE category = :category AND content LIKE :pattern LIMIT :limit")
    suspend fun searchByContentLike(category: String, pattern: String, limit: Int = 5): List<Memory>

    @Query("SELECT * FROM memories WHERE content LIKE :pattern AND (roleCardId IS NULL OR roleCardId = :roleCardId) ORDER BY strength DESC, lastAccessedAt DESC LIMIT :limit")
    suspend fun searchByContentLikeWithRole(pattern: String, roleCardId: Long, limit: Int = 5): List<Memory>

    @Query("SELECT * FROM memories WHERE strength > :minStrength OR baseline > :minStrength ORDER BY strength DESC, lastAccessedAt DESC")
    suspend fun getActiveMemories(minStrength: Double = 0.05): List<Memory>

    // ── 强度衰减（双值模型：strength 衰减但不低于 baseline，baseline 缓慢衰减） ──
    @Query("""
        UPDATE memories SET
            baseline = baseline * 0.95,
            strength = MAX(strength * CASE
                WHEN :idleDays >= 4 THEN 0.90
                WHEN :idleDays = 3  THEN 0.90
                WHEN :idleDays = 2  THEN 0.80
                WHEN :idleDays = 1  THEN 0.70
                ELSE 1.0
            END, baseline),
            updatedAt = :now, lastAccessedAt = :now
        WHERE id = :id AND (strength > 0.05 OR baseline > 0.05)
    """)
    suspend fun applyDecayByAge(id: Long, idleDays: Int, now: Long)

    // ── 强化（每日上限 0.4，baseline 同步上升但仅 30%） ──
    @Query("""
        UPDATE memories SET
            dailyStrengthenDelta = CASE WHEN lastStrengthenDate != :today THEN 0 ELSE dailyStrengthenDelta END,
            strength = MIN(1.0, strength + MIN(:delta, MAX(0, 0.4 - CASE WHEN lastStrengthenDate != :today THEN 0 ELSE dailyStrengthenDelta END))),
            baseline = MIN(0.8, baseline + MIN(:delta * 0.3, MAX(0, 0.4 - CASE WHEN lastStrengthenDate != :today THEN 0 ELSE dailyStrengthenDelta END) * 0.3)),
            dailyStrengthenDelta = CASE WHEN lastStrengthenDate != :today THEN 0 ELSE dailyStrengthenDelta END + MIN(:delta, MAX(0, 0.4 - CASE WHEN lastStrengthenDate != :today THEN 0 ELSE dailyStrengthenDelta END)),
            lastStrengthenDate = :today,
            lastAccessedAt = :now
        WHERE id = :id
    """)
    suspend fun strengthen(id: Long, delta: Double, today: Long, now: Long)

    // ── 清理弱记忆（strength 和 baseline 都低于阈值才清理） ──
    @Query("DELETE FROM memories WHERE strength < :threshold AND baseline < :threshold")
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
