package com.companion.chat.data.memory

interface MemoryExtractor {
    fun extract(userMessage: String, sessionId: String): List<ExtractedMemory>
}
