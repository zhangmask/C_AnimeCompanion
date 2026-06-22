package com.companion.chat.data.context

data class ContextSettings(
    val retainedRounds: Int = 10,
    val compressionBuffer: Int = 6,
    val summaryMaxChars: Int = 500,
    val summaryTimeoutMillis: Long = 120_000L
) {
    val compressionThreshold: Int
        get() = retainedRounds * 2 + compressionBuffer
}
