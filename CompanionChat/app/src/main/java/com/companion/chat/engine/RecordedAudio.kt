package com.companion.chat.engine

data class RecordedAudio(
    val pcm16: ShortArray,
    val sampleRate: Int
) {
    val isEmpty: Boolean = pcm16.isEmpty()
}
