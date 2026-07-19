package com.companion.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.companion.chat.data.local.entity.MessageEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, position ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>): List<Long>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages(): Int

    @Query("SELECT audioUri FROM messages WHERE id = :id")
    suspend fun getAudioUri(id: String): String?

    @Query("UPDATE messages SET audioUri = :uri WHERE id = :id")
    suspend fun updateAudioUri(id: String, uri: String)
}
