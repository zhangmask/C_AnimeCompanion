package com.companion.chat.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.MessageEntity
import com.companion.chat.data.local.model.ConversationWithMessages
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.ConversationSession
import com.companion.chat.data.migration.DataMigration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatSessionRepository(
    private val context: Context,
    private val database: CompanionDatabase = CompanionDatabase.getInstance(context)
) {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val dataMigration = DataMigration(context, database)

    suspend fun ensureInitialized() {
        if (isInitialized) return

        initializationMutex.withLock {
            if (isInitialized) return
            dataMigration.migrateLegacySessionsIfNeeded()
            isInitialized = true
        }
    }

    suspend fun getAllSessions(): List<ConversationSession> {
        return conversationDao.getAllConversationsWithMessages().map { it.toDomainModel() }
    }

    suspend fun getSessionByRoleCardId(roleCardId: Long): ConversationSession? {
        val entity = conversationDao.getConversationByRoleCardId(roleCardId) ?: return null
        val messages = messageDao.getMessagesForConversation(entity.id)
        return ConversationWithMessages(entity, messages).toDomainModel()
    }

    suspend fun replaceAllSessions(sessions: List<ConversationSession>) {
        database.withTransaction {
            messageDao.deleteAllMessages()
            conversationDao.deleteAllConversations()
            insertSessions(sessions)
        }
    }

    suspend fun replaceSession(session: ConversationSession) {
        database.withTransaction {
            conversationDao.upsertConversations(listOf(session.toEntity()))
            messageDao.deleteMessagesForConversation(session.id)
            messageDao.upsertMessages(session.messages.mapIndexed { index, message ->
                message.toEntity(session.id, index)
            })
        }
    }

    suspend fun deleteSession(sessionId: String): Boolean {
        return database.withTransaction {
            conversationDao.deleteConversationById(sessionId) > 0
        }
    }

    private suspend fun insertSessions(sessions: List<ConversationSession>) {
        if (sessions.isEmpty()) return

        conversationDao.upsertConversations(sessions.map { it.toEntity() })
        val messages = sessions.flatMap { session ->
            session.messages.mapIndexed { index, message ->
                message.toEntity(session.id, index)
            }
        }
        if (messages.isNotEmpty()) {
            messageDao.upsertMessages(messages)
        }
    }

    companion object {
        private val initializationMutex = Mutex()

        @Volatile
        private var isInitialized = false
    }
}

private fun ConversationWithMessages.toDomainModel(): ConversationSession {
    return ConversationSession(
        id = conversation.id,
        title = conversation.title,
        roleCardId = conversation.roleCardId,
        messages = messages
            .sortedBy { it.position }
            .map { it.toDomainModel() },
        createdAt = conversation.createdAt,
        updatedAt = conversation.updatedAt
    )
}

private fun ConversationEntity.toDomainModel(): ConversationSession {
    return ConversationSession(
        id = id,
        title = title,
        roleCardId = roleCardId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun ConversationSession.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        title = title,
        roleCardId = roleCardId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

private fun MessageEntity.toDomainModel(): ChatMessage {
    return ChatMessage(
        id = id,
        role = role,
        content = content,
        images = imageUris.map(Uri::parse),
        timestamp = timestamp
    )
}

private fun ChatMessage.toEntity(conversationId: String, position: Int): MessageEntity {
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        imageUris = images.map(Uri::toString),
        timestamp = timestamp,
        position = position
    )
}
