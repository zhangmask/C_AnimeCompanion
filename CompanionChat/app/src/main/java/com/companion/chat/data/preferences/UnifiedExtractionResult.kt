package com.companion.chat.data.preferences

import com.companion.chat.data.memory.ExtractedMemory

data class UnifiedExtractionResult(
    val memories: List<ExtractedMemory> = emptyList(),
    val userPreferences: List<ExtractedPreference> = emptyList()
)
