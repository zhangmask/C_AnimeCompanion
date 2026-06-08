package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage

interface ContextManager {

    fun shouldCompress(messages: List<ChatMessage>, settings: ContextSettings): Boolean

    suspend fun buildContext(
        messages: List<ChatMessage>,
        systemPrompt: String,
        userPreferences: String,
        persistentMemoryPrompt: String = "",
        memoryPrompt: String = "",
        settings: ContextSettings
    ): ContextWindow

    suspend fun compressHistory(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String
}
