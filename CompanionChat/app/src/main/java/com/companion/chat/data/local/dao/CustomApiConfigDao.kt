package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.companion.chat.data.local.entity.CustomApiConfig

@Dao
interface CustomApiConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: CustomApiConfig): Long

    @Update
    suspend fun update(config: CustomApiConfig)

    @Delete
    suspend fun delete(config: CustomApiConfig)

    @Query("SELECT * FROM custom_api_configs ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CustomApiConfig>

    @Query("SELECT * FROM custom_api_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CustomApiConfig?

    @Query("SELECT * FROM custom_api_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): CustomApiConfig?

    @Query("UPDATE custom_api_configs SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE custom_api_configs SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM custom_api_configs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
