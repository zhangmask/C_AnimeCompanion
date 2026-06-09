package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.model.ConversationWithMessages

@Dao
interface ConversationDao {

    @Transaction
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    suspend fun getAllConversationsWithMessages(): List<ConversationWithMessages>

    @Query("SELECT * FROM conversations WHERE roleCardId = :roleCardId LIMIT 1")
    suspend fun getConversationByRoleCardId(roleCardId: Long): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getConversationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(conversations: List<ConversationEntity>): List<Long>

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversationById(conversationId: String): Int

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations(): Int
}
