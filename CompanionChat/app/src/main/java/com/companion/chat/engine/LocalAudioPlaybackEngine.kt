package com.companion.chat.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.companion.chat.data.engine.VoiceOutputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GeneratedAudioPlayer {
    val state: StateFlow<VoiceOutputState>
    fun play(audioUri: String)
    fun stop()
    fun release()
}

class LocalAudioPlaybackEngine(
    private val context: Context
) : GeneratedAudioPlayer {
    private val _state = MutableStateFlow<VoiceOutputState>(VoiceOutputState.Idle)
    override val state: StateFlow<VoiceOutputState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    override fun play(audioUri: String) {
        stop()
        val uri = Uri.parse(audioUri)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setOnPreparedListener {
                _state.value = VoiceOutputState.Speaking
                it.start()
            }
            setOnCompletionListener {
                _state.value = VoiceOutputState.Idle
                releasePlayer()
            }
            setOnErrorListener { _, _, _ ->
                _state.value = VoiceOutputState.Error("本地音频播放失败")
                releasePlayer()
                true
            }
            prepareAsync()
        }
    }

    override fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
        }
        releasePlayer()
        _state.value = VoiceOutputState.Idle
    }

    override fun release() {
        stop()
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
    }
}
