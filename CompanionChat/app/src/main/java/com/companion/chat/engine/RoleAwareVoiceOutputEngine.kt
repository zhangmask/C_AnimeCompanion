package com.companion.chat.engine

import android.util.Log
import com.companion.chat.data.engine.VoiceOutputConfig
import com.companion.chat.data.engine.VoiceOutputEngine
import com.companion.chat.data.engine.VoiceOutputMode
import com.companion.chat.data.engine.VoiceOutputState
import com.companion.chat.data.engine.TtsQueueMode
import com.companion.chat.data.role.RoleCardRepository
import com.companion.chat.data.voice.VoiceCloneEngine
import com.companion.chat.data.voice.VoiceCloneRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine

/** TTS 回退事件 */
sealed class TtsFallbackEvent {
    /** 回退到系统 TTS */
    data class FallbackToSystem(val reason: String) : TtsFallbackEvent()
}

class RoleAwareVoiceOutputEngine(
    private val fallbackEngine: VoiceOutputEngine,
    private val roleCardRepository: RoleCardRepository?,
    private val cloneEngine: VoiceCloneEngine? = null,
    private val localAudioPlaybackEngine: GeneratedAudioPlayer? = null,
    private val activeRoleConfigProvider: (suspend () -> VoiceOutputConfig?)? = null,
    private val defaultReferenceAudioProvider: (() -> String)? = null
) : VoiceOutputEngine {
    /** 回退事件流 */
    private val _fallbackEvents = MutableSharedFlow<TtsFallbackEvent>()
    val fallbackEvents: SharedFlow<TtsFallbackEvent> = _fallbackEvents.asSharedFlow()

    private companion object {
        const val TAG = "RoleAwareVoiceOutput"
        /** 每段最大字符数，超过则按句子分段 */
        const val MAX_SEGMENT_LENGTH = 100

        fun safeLog(message: String, warning: Boolean = false) {
            runCatching {
                if (warning) {
                    Log.w(TAG, message)
                } else {
                    Log.i(TAG, message)
                }
            }
        }

        /** 按句子边界分段文本 */
        fun splitTextBySentences(text: String, maxLength: Int): List<String> {
            if (text.length <= maxLength) return listOf(text)

            val segments = mutableListOf<String>()
            val sentenceEnders = setOf('。', '！', '？', '!', '?', '.', '\n')
            var start = 0

            while (start < text.length) {
                val remaining = text.length - start
                if (remaining <= maxLength) {
                    segments.add(text.substring(start))
                    break
                }

                // 在 maxLength 范围内找最后一个句子结束符
                var lastSentenceEnd = -1
                for (i in start until minOf(start + maxLength, text.length)) {
                    if (text[i] in sentenceEnders) {
                        lastSentenceEnd = i + 1
                    }
                }

                if (lastSentenceEnd > start) {
                    segments.add(text.substring(start, lastSentenceEnd))
                    start = lastSentenceEnd
                } else {
                    // 没找到句子结束符，强制截断
                    segments.add(text.substring(start, start + maxLength))
                    start += maxLength
                }
            }

            return segments.filter { it.isNotBlank() }
        }
    }

    override val state: Flow<VoiceOutputState> = if (localAudioPlaybackEngine == null) {
        fallbackEngine.state
    } else {
        fallbackEngine.state.combine(localAudioPlaybackEngine.state) { fallbackState, playbackState ->
            when {
                playbackState is VoiceOutputState.Error -> playbackState
                fallbackState is VoiceOutputState.Error -> fallbackState
                playbackState is VoiceOutputState.Speaking -> playbackState
                fallbackState is VoiceOutputState.Speaking -> fallbackState
                else -> VoiceOutputState.Idle
            }
        }
    }

    override suspend fun speak(text: String, config: VoiceOutputConfig, queueMode: TtsQueueMode) {
        safeLog("speak() 开始: textLength=${text.length}, queueMode=$queueMode")

        val roleConfig = activeRoleConfigProvider?.invoke()
            ?: roleCardRepository?.getActiveRoleCard()?.let {
                safeLog("读取角色卡语音配置: voiceMode=${it.voiceMode}, hasProfileUri=${it.voiceProfileUri.isNotBlank()}")
                VoiceOutputConfig(
                    mode = runCatching { VoiceOutputMode.valueOf(it.voiceMode) }
                        .getOrDefault(VoiceOutputMode.CLONE),
                    referenceAudioUri = it.voiceProfileUri,
                    displayName = it.voiceDisplayName
                )
            }
            ?: config.also { safeLog("未找到角色卡配置，使用默认 config: mode=${it.mode}") }

        safeLog("语音配置: mode=${roleConfig.mode}, hasRefAudio=${roleConfig.referenceAudioUri.isNotBlank()}")

        if (roleConfig.mode != VoiceOutputMode.CLONE || cloneEngine == null || localAudioPlaybackEngine == null) {
            val reason = when {
                roleConfig.mode != VoiceOutputMode.CLONE -> "mode=${roleConfig.mode} (非 CLONE)"
                cloneEngine == null -> "cloneEngine 为 null"
                localAudioPlaybackEngine == null -> "localAudioPlaybackEngine 为 null"
                else -> "unknown"
            }
            safeLog("使用系统 TTS ($reason): text=${text.take(40)}...")
            _fallbackEvents.tryEmit(TtsFallbackEvent.FallbackToSystem("语音克隆未启用"))
            fallbackEngine.speak(text, roleConfig.copy(mode = VoiceOutputMode.SYSTEM_TTS), queueMode)
            return
        }

        safeLog("尝试语音克隆: refAudio=${roleConfig.referenceAudioUri.take(60)}")

        // 角色未配置参考音频时，使用默认 MOSS 参考音频
        val effectiveRefAudioUri = roleConfig.referenceAudioUri.ifBlank {
            defaultReferenceAudioProvider?.invoke()?.also {
                safeLog("角色未配置参考音频，使用默认 MOSS 音色: $it")
            } ?: ""
        }

        if (effectiveRefAudioUri.isBlank()) {
            safeLog("无可用参考音频（角色未配置且无默认音色），回退系统 TTS")
            _fallbackEvents.tryEmit(TtsFallbackEvent.FallbackToSystem("未配置参考音频"))
            fallbackEngine.speak(text, roleConfig.copy(mode = VoiceOutputMode.SYSTEM_TTS), queueMode)
            return
        }

        // 分段合成：长文本按句子分段，每段合成完立即播放
        val segments = splitTextBySentences(text, MAX_SEGMENT_LENGTH)
        safeLog("文本分段: ${segments.size} 段, 总长度=${text.length}")

        for ((index, segment) in segments.withIndex()) {
            safeLog("合成第 ${index + 1}/${segments.size} 段: ${segment.take(30)}...")

            val cloneResult = cloneEngine.synthesize(
                VoiceCloneRequest(
                    text = segment,
                    referenceAudioUri = effectiveRefAudioUri,
                    displayName = roleConfig.displayName
                )
            ).getOrElse {
                safeLog("语音克隆异常，回退系统 TTS: ${it.message}", warning = true)
                null
            }

            if (cloneResult?.fallbackToSystemTts == false && !cloneResult.audioUri.isNullOrBlank()) {
                safeLog("第 ${index + 1} 段合成成功，播放: ${cloneResult.message}")
                localAudioPlaybackEngine.play(cloneResult.audioUri)
                // 等待当前段播放完成再合成下一段
                waitForPlaybackComplete()
            } else {
                safeLog("第 ${index + 1} 段合成失败，回退系统 TTS")
                _fallbackEvents.tryEmit(TtsFallbackEvent.FallbackToSystem("MOSS TTS 合成失败"))
                fallbackEngine.speak(segment, roleConfig.copy(mode = VoiceOutputMode.SYSTEM_TTS), TtsQueueMode.FLUSH)
                break
            }
        }
    }

    /** 等待当前音频播放完成 */
    private suspend fun waitForPlaybackComplete() {
        // 简单实现：轮询状态直到 Idle
        // 更优雅的实现可以使用 callback 或 Flow
        while (localAudioPlaybackEngine?.state?.value is VoiceOutputState.Speaking) {
            kotlinx.coroutines.delay(100)
        }
    }

    override fun stop() {
        localAudioPlaybackEngine?.stop()
        fallbackEngine.stop()
    }

    override fun release() {
        localAudioPlaybackEngine?.release()
        fallbackEngine.release()
    }
}
