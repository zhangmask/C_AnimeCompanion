package com.companion.chat.data.migration

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.local.entity.ConversationEntity
import com.companion.chat.data.local.entity.MessageEntity
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.ConversationSession
import com.companion.chat.data.model.DEFAULT_SESSION_TITLE
import com.companion.chat.data.model.MessageRole
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataMigration(
    private val context: Context,
    private val database: CompanionDatabase
) {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    private val legacySessionsFile: File
        get() = File(context.filesDir, "conversations.json")

    private val legacyBackupFile: File
        get() = File(context.filesDir, "conversations.json.bak")

    fun hasLegacySessionFile(): Boolean = legacySessionsFile.exists()

    suspend fun migrateLegacySessionsIfNeeded() {
        if (conversationDao.getConversationCount() > 0) {
            log("Room 已有会话数据，跳过旧 JSON 迁移")
            Log.d(TAG, "Room 已有会话数据，跳过旧 JSON 迁移")
            return
        }
        if (legacyBackupFile.exists()) {
            log("检测到 conversations.json.bak，视为已迁移，跳过")
            Log.d(TAG, "检测到 conversations.json.bak，视为已迁移，跳过")
            return
        }
        if (!legacySessionsFile.exists()) {
            log("未检测到 conversations.json，跳过迁移")
            Log.d(TAG, "未检测到 conversations.json，跳过迁移")
            return
        }

        try {
            log("开始读取旧 JSON: ${legacySessionsFile.absolutePath}")
            val sessions = parseLegacySessions(legacySessionsFile.readText())
            if (sessions.isEmpty()) {
                log("旧 JSON 为空或无有效会话，跳过导入")
                Log.d(TAG, "旧 JSON 为空或无有效会话，跳过导入")
                return
            }

            database.withTransaction {
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
            backupLegacyFile()
            log("旧 JSON 迁移完成，会话数=${sessions.size}")
            Log.d(TAG, "旧 JSON 迁移完成，会话数=${sessions.size}")
        } catch (e: Exception) {
            log("旧 JSON 迁移失败: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "旧 JSON 迁移失败，保留原文件", e)
        }
    }

    private fun backupLegacyFile() {
        if (legacyBackupFile.exists()) {
            legacyBackupFile.delete()
        }
        if (!legacySessionsFile.renameTo(legacyBackupFile)) {
            legacyBackupFile.writeText(legacySessionsFile.readText())
            legacySessionsFile.delete()
        }
    }

    private fun parseLegacySessions(json: String): List<ConversationSession> {
        val normalizedJson = json.trim().removePrefix("\uFEFF")
        if (normalizedJson.isBlank()) return emptyList()

        val array = JSONArray(normalizedJson)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val sessionObject = array.getJSONObject(index)
                val createdAt = sessionObject.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = sessionObject.optLong("updatedAt", createdAt)
                val messagesArray = sessionObject.optJSONArray("messages") ?: JSONArray()
                val messages = buildList(messagesArray.length()) {
                    for (messageIndex in 0 until messagesArray.length()) {
                        val messageObject = messagesArray.getJSONObject(messageIndex)
                        val imageArray = messageObject.optJSONArray("images") ?: JSONArray()
                        add(
                            ChatMessage(
                                id = messageObject.getString("id"),
                                role = MessageRole.valueOf(messageObject.getString("role")),
                                content = messageObject.optString("content"),
                                images = buildList(imageArray.length()) {
                                    for (imageIndex in 0 until imageArray.length()) {
                                        add(Uri.parse(imageArray.getString(imageIndex)))
                                    }
                                },
                                timestamp = messageObject.optLong("timestamp", createdAt)
                            )
                        )
                    }
                }

                add(
                    ConversationSession(
                        id = sessionObject.getString("id"),
                        title = sessionObject.optString("title", DEFAULT_SESSION_TITLE),
                        messages = messages,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "DataMigration"
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        context.openFileOutput("app_init_log.txt", Context.MODE_APPEND).use { output ->
            output.write("[$time] $message\n".toByteArray())
        }
    }
}

private fun ConversationSession.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
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
