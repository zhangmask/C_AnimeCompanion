package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.companion.chat.data.local.entity.RoleCard

@Dao
interface RoleCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(roleCard: RoleCard): Long

    @Update
    suspend fun update(roleCard: RoleCard)

    @Delete
    suspend fun delete(roleCard: RoleCard)

    @Query("SELECT * FROM role_cards ORDER BY isActive DESC, isBuiltIn DESC, updatedAt DESC")
    suspend fun getAll(): List<RoleCard>

    @Query("SELECT * FROM role_cards WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): RoleCard?

    @Query("SELECT * FROM role_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoleCard?

    @Query("UPDATE role_cards SET isActive = 0")
    suspend fun deactivateAll(): Int

    @Query("UPDATE role_cards SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: Long, now: Long = System.currentTimeMillis()): Int
}
