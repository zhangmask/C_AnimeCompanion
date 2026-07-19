package com.companion.chat.data.context

import com.companion.chat.data.model.ChatMessage

class NoOpSummaryGenerator : SummaryGenerator {
    override suspend fun summarize(messages: List<ChatMessage>, settings: ContextSettings): String = ""
}
