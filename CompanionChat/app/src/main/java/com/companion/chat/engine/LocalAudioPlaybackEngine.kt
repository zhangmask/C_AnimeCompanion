package com.companion.chat.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
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
        Log.i(TAG, "播放开始: $audioUri")
        stop()
        val uri = Uri.parse(audioUri)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setVolume(1.0f, 1.0f)
            try {
                setDataSource(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "setDataSource 失败: ${e.message}")
                _state.value = VoiceOutputState.Error("音频数据源错误: ${e.message}")
                releasePlayer()
                return
            }
            setOnPreparedListener {
                Log.i(TAG, "MediaPlayer prepared, duration=${it.duration}ms, 开始播放")
                _state.value = VoiceOutputState.Speaking
                it.start()
            }
            setOnCompletionListener {
                Log.i(TAG, "MediaPlayer 播放完成")
                _state.value = VoiceOutputState.Idle
                releasePlayer()
            }
            setOnErrorListener { mp, what, extra ->
                val msg = "MediaPlayer 错误 what=$what extra=$extra"
                Log.e(TAG, msg)
                _state.value = VoiceOutputState.Error(msg)
                releasePlayer()
                true
            }
            setOnInfoListener { _, what, extra ->
                Log.d(TAG, "MediaPlayer info what=$what extra=$extra")
                false
            }
            prepareAsync()
        }
    }

    override fun stop() {
        Log.i(TAG, "播放停止")
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

    companion object {
        private const val TAG = "LocalAudioPlayback"
    }
}
