package com.companion.chat.data.model

import android.net.Uri
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 引用上下文：保存被引用消息的角色与内容片段。
 * - sourceRole 用于在 UI 上标注"引用了 用户/助手 的内容"
 * - text 即被引用文本（用户可在选择模式下框选，进入输入框前作为引用快照保存）
 */
data class MessageQuote(
    val sourceRole: MessageRole,
    val text: String
)

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
    val isSuggestion: Boolean = false,
    /** 当本条用户消息携带引用时，记录被引用来源与片段；发送后此字段会随消息一起持久化，便于历史回看 */
    val quote: MessageQuote? = null,
    /** TTS 音频缓存路径；命中则直接播放，避免重复合成 */
    val audioUri: String? = null
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
