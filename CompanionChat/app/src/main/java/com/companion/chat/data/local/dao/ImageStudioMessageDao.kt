package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.companion.chat.data.local.entity.ImageStudioMessageEntity

@Dao
interface ImageStudioMessageDao {

    @Query("SELECT * FROM image_studio_messages WHERE roleCardId = :roleCardId ORDER BY position ASC")
    suspend fun getByRoleCardId(roleCardId: Long): List<ImageStudioMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ImageStudioMessageEntity)

    @Query("DELETE FROM image_studio_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT MAX(position) FROM image_studio_messages WHERE roleCardId = :roleCardId")
    suspend fun maxPosition(roleCardId: Long): Int?

    @Query("UPDATE image_studio_messages SET imageUri = :imageUri, isError = :isError, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateResult(id: String, imageUri: String?, isError: Boolean, errorMessage: String?)
}
