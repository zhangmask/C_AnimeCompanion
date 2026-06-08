package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage

interface SummaryGenerator {

    suspend fun summarize(
        messages: List<ChatMessage>,
        settings: ContextSettings
    ): String
}
