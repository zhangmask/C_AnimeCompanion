package com.companion.chat.data.model

import android.net.Uri
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

const val DEFAULT_SESSION_TITLE = "新对话"
const val DEFAULT_WELCOME_MESSAGE =
    "你好！我是你的 AI 伙伴。点击下方麦克风按钮开始语音对话，或直接输入文字。"

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val images: List<Uri> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isSuggestion: Boolean = false
)

data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = DEFAULT_SESSION_TITLE,
    val roleCardId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isUserRenamed: Boolean = false
)

fun createWelcomeMessage() = ChatMessage(
    role = MessageRole.ASSISTANT,
    content = DEFAULT_WELCOME_MESSAGE
)

fun createDefaultSession() = ConversationSession(
    title = DEFAULT_SESSION_TITLE,
    messages = listOf(createWelcomeMessage())
)
