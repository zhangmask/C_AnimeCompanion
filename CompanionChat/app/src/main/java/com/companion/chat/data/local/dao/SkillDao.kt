package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.companion.chat.data.local.entity.Skill

@Dao
interface SkillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: Skill): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<Skill>): List<Long>

    @Update
    suspend fun update(skill: Skill)

    @Delete
    suspend fun delete(skill: Skill)

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, usageCount DESC, updatedAt DESC")
    suspend fun getAll(): List<Skill>

    @Query("SELECT * FROM skills WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Skill?

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Skill?

    @Query("UPDATE skills SET isActive = 0")
    suspend fun deactivateAll(): Int

    @Query("UPDATE skills SET isActive = 1, usageCount = usageCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: Long, now: Long = System.currentTimeMillis()): Int
}
