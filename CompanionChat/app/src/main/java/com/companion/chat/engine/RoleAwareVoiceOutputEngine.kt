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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.atomic.AtomicBoolean

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
    private val defaultReferenceAudioProvider: (() -> String)? = null,
    private val getCachedAudioUri: (suspend (String, String) -> String?)? = null,
    private val saveCachedAudioUri: (suspend (String, String, String) -> Unit)? = null
) : VoiceOutputEngine {
    /** 回退事件流 */
    private val _fallbackEvents = MutableSharedFlow<TtsFallbackEvent>()
    val fallbackEvents: SharedFlow<TtsFallbackEvent> = _fallbackEvents.asSharedFlow()

    /** 同一时刻仅处理一个合成/播放请求，防止用户连续点击导致并发任务 */
    private val isProcessing = AtomicBoolean(false)

    private companion object {
        const val TAG = "RoleAwareVoiceOutput"
        /** 每段最大字符数，超过则按句子分段 */
        const val MAX_SEGMENT_LENGTH = 100
        const val PLAYBACK_TIMEOUT_MILLIS = 30_000L

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
        synthesizeAndPlay(text, config, queueMode)
    }

    override suspend fun speakWithCache(
        messageId: String,
        text: String,
        config: VoiceOutputConfig,
        queueMode: TtsQueueMode
    ) {
        val cleanText = cleanTextForTts(text)
        val role = resolveRoleName()
        val cacheKey = buildCacheKey(role, cleanText)
        safeLog("speakWithCache 开始: messageId=$messageId role=$role cacheKey=$cacheKey textLength=${cleanText.length} originalLength=${text.length}")

        // 1. 先查缓存：命中且文件存在则直接播放，跳过合成
        val cachedUri = getCachedAudioUri?.invoke(role, cleanText)
        safeLog("speakWithCache 查询缓存: cacheKey=$cacheKey cachedUri=$cachedUri")
        val fileExists = if (!cachedUri.isNullOrBlank()) cachedAudioFileExists(cachedUri) else false
        safeLog("speakWithCache 文件校验: cacheKey=$cacheKey fileExists=$fileExists")
        if (!cachedUri.isNullOrBlank() && fileExists) {
            safeLog("缓存命中，直接播放: cacheKey=$cacheKey uri=$cachedUri")
            if (localAudioPlaybackEngine != null) {
                if (!isProcessing.compareAndSet(false, true)) {
                    safeLog("已有播放任务进行中，忽略缓存播放请求", warning = true)
                    return
                }
                try {
                    safeLog("缓存命中播放: uri=$cachedUri")
                    localAudioPlaybackEngine.play(cachedUri)
                    waitForPlaybackComplete()
                    safeLog("缓存命中播放完成: uri=$cachedUri")
                } finally {
                    isProcessing.set(false)
                }
                return
            } else {
                safeLog("缓存命中但 localAudioPlaybackEngine 为 null，退而合成", warning = true)
            }
        }

        // 2. 未命中缓存：合成并播放
        safeLog("缓存未命中，开始合成: cacheKey=$cacheKey cachedUri=$cachedUri fileExists=$fileExists")
        val segmentUris = synthesizeAndPlay(cleanText, config, queueMode)

        // 3. 合成成功后写入缓存（按 角色+文本 持久化到数据库）
        if (segmentUris.isNotEmpty()) {
            val mergedUri = mergeOrPickAudioUri(segmentUris)
            safeLog("speakWithCache 合并URI: cacheKey=$cacheKey mergedUri=$mergedUri")
            if (!mergedUri.isNullOrBlank()) {
                saveCachedAudioUri?.invoke(role, cleanText, mergedUri)
                safeLog("缓存已写入: cacheKey=$cacheKey uri=$mergedUri")
            } else {
                safeLog("speakWithCache 合并URI为空，跳过缓存写入: cacheKey=$cacheKey", warning = true)
            }
        } else {
            safeLog("speakWithCache 合成返回空列表，跳过缓存写入: cacheKey=$cacheKey", warning = true)
        }
        safeLog("speakWithCache 结束: cacheKey=$cacheKey")
    }

    /** 从角色卡或配置中解析当前角色名称，用于缓存 key。 */
    private suspend fun resolveRoleName(): String {
        val activeRole = roleCardRepository?.getActiveRoleCard()
        return activeRole?.name?.takeIf { it.isNotBlank() }
            ?: activeRoleConfigProvider?.invoke()?.displayName?.takeIf { it.isNotBlank() }
            ?: "default"
    }

    /** 构造缓存 key：角色名 + 文本内容 SHA-256。 */
    private fun buildCacheKey(role: String, text: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$role|$hash"
    }

    /**
     * 清理用于 TTS 的文本：
     * - 移除 emoji 和零宽字符
     * - 移除 Markdown 标记（**、*、__、_、`）
     * - 移除链接 [text](url) 只保留 text
     * - 压缩多余空白
     */
    private fun cleanTextForTts(text: String): String {
        return text
            // 移除 emoji（Unicode emoji 范围，覆盖绝大多数常用表情）
            .replace(Regex("[\uD83C\uDC00-\uD83C\uDFFF]|[\uD83D\uDC00-\uD83D\uDFFF]|[\uD83E\uDD00-\uD83E\uDEFF]|[\u2600-\u27BF]|[\uFE00-\uFE0F]"), "")
            // 移除零宽字符
            .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
            // 移除 Markdown 标记
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            .replace(Regex("`{1,3}([^`]+?)`{1,3}"), "$1")
            // 将链接 [text](url) 替换为 text
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
            // 压缩空白
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 核心合成+播放逻辑。返回成功播放的每段 audioUri 列表（用于后续缓存）。
     * 加 isProcessing 锁防止并发点击。
     */
    private suspend fun synthesizeAndPlay(
        text: String,
        config: VoiceOutputConfig,
        queueMode: TtsQueueMode
    ): List<String> {
        if (!isProcessing.compareAndSet(false, true)) {
            safeLog("已有正在进行的语音合成/播放任务，忽略本次点击请求", warning = true)
            return emptyList()
        }
        safeLog("speak() 开始: textLength=${text.length}, queueMode=$queueMode")
        val playedUris = mutableListOf<String>()
        try {
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
                return emptyList()
            }

            safeLog("尝试语音克隆: refAudio=${roleConfig.referenceAudioUri.take(60)}")

            // 角色未配置参考音频时，使用默认 MOSS 参考音频
            val effectiveRefAudioUri = roleConfig.referenceAudioUri.ifBlank {
                defaultReferenceAudioProvider?.invoke()?.also {
                    safeLog("角色未配置参考音频，使用默认 MOSS 音色: $it")
                } ?: ""
            }
            safeLog("实际使用参考音频 URI: $effectiveRefAudioUri")

            if (effectiveRefAudioUri.isBlank()) {
                safeLog("无可用参考音频（角色未配置且无默认音色），回退系统 TTS")
                _fallbackEvents.tryEmit(TtsFallbackEvent.FallbackToSystem("未配置参考音频"))
                fallbackEngine.speak(text, roleConfig.copy(mode = VoiceOutputMode.SYSTEM_TTS), queueMode)
                return emptyList()
            }

            // 分段合成：等所有段合成完成后才播放，宁愿多等也不停顿
            val segments = splitTextBySentences(text, MAX_SEGMENT_LENGTH)
            safeLog("文本分段: ${segments.size} 段, 总长度=${text.length}")

            coroutineScope {
                // 一次性启动所有段的合成
                val deferreds = segments.mapIndexed { index, segment ->
                    async {
                        safeLog("合成第 ${index + 1}/${segments.size} 段: ${segment.take(30)}...")
                        var retryCount = 0
                        while (retryCount <= 3) {
                            try {
                                val result = cloneEngine.synthesize(
                                    VoiceCloneRequest(
                                        text = segment,
                                        referenceAudioUri = effectiveRefAudioUri,
                                        displayName = roleConfig.displayName
                                    )
                                ).getOrElse { null }
                                if (result != null && !result.fallbackToSystemTts && !result.audioUri.isNullOrBlank()) {
                                    return@async result
                                }
                            } catch (e: Exception) {
                                safeLog("第 ${index + 1} 段合成异常: ${e.message}", warning = true)
                            }
                            retryCount++
                            if (retryCount <= 3) {
                                safeLog("第 ${index + 1} 段合成失败，等待 5 秒后重试 ($retryCount/3)", warning = true)
                                kotlinx.coroutines.delay(5000)
                            }
                        }
                        null
                    }
                }

                // 等待所有段合成完成
                safeLog("等待所有段合成完成...")
                val results = deferreds.map { it.await() }
                safeLog("所有段合成完成，开始顺序播放")

                // 按顺序播放
                for ((index, cloneResult) in results.withIndex()) {
                    if (cloneResult != null && !cloneResult.fallbackToSystemTts && !cloneResult.audioUri.isNullOrBlank()) {
                        safeLog("播放第 ${index + 1} 段: ${cloneResult.message}")
                        localAudioPlaybackEngine.play(cloneResult.audioUri)
                        playedUris.add(cloneResult.audioUri)
                        waitForPlaybackComplete()
                    } else {
                        safeLog("第 ${index + 1} 段最终失败，跳过", warning = true)
                    }
                }
            }
        } finally {
            isProcessing.set(false)
            safeLog("speak() 结束，释放合成锁")
        }
        return playedUris
    }

    /** 缓存命中时校验音频文件仍存在，避免引用已删除文件 */
    private fun cachedAudioFileExists(uri: String): Boolean {
        return runCatching {
            val path = if (uri.startsWith("file://")) android.net.Uri.parse(uri).path else uri
            !path.isNullOrBlank() && java.io.File(path).exists()
        }.getOrDefault(false)
    }

    /**
     * 单段直接复用原 wav 路径；多段读取每个 wav 的 interleaved PCM 合并写新 wav。
     * 合并文件存到与原文件相同的 generated_audio/mnn_tts 目录。
     */
    private fun mergeOrPickAudioUri(uris: List<String>): String? {
        if (uris.isEmpty()) return null
        if (uris.size == 1) return uris[0]
        return runCatching {
            val chunks = uris.mapNotNull { readInterleavedWavPcm(it) }
            if (chunks.isEmpty()) return@runCatching uris.firstOrNull()
            val sampleRate = chunks[0].sampleRate
            val channels = chunks[0].channels
            val totalSamples = chunks.sumOf { it.pcm.size }
            val merged = FloatArray(totalSamples)
            var offset = 0
            for (c in chunks) {
                System.arraycopy(c.pcm, 0, merged, offset, c.pcm.size)
                offset += c.pcm.size
            }
            val outFile = writeInterleavedWav(merged, sampleRate, channels, uris.first())
            outFile?.let { android.net.Uri.fromFile(it).toString() }
        }.onFailure { safeLog("合并多段 wav 失败: ${it.message}", warning = true) }.getOrNull()
    }

    private fun readInterleavedWavPcm(uri: String): WavPcmChunk? = runCatching {
        val path = if (uri.startsWith("file://")) android.net.Uri.parse(uri).path else uri
        val file = java.io.File(path ?: return@runCatching null)
        if (!file.exists()) return@runCatching null
        val bytes = file.readBytes()
        if (bytes.size <= 44) return@runCatching null
        val dataOffset = findWavDataOffset(bytes).takeIf { it >= 0 } ?: 44
        val header = java.nio.ByteBuffer.wrap(bytes, 0, 44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.position(22); val channels = header.short.toInt() and 0xFFFF
        header.position(24); val sampleRate = header.int
        header.position(34); val bitsPerSample = header.short.toInt() and 0xFFFF
        if (channels <= 0 || bitsPerSample != 16) return@runCatching null
        val dataBuf = java.nio.ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val totalSamples = dataBuf.remaining() / 2
        val pcm = FloatArray(totalSamples) { (dataBuf.short.toInt() / Short.MAX_VALUE.toFloat()) }
        WavPcmChunk(pcm, sampleRate, channels)
    }.getOrNull()

    private fun findWavDataOffset(bytes: ByteArray): Int {
        val marker = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
        for (index in 12 until bytes.size - 8) {
            if (marker.indices.all { bytes[index + it] == marker[it] }) return index + 8
        }
        return -1
    }

    private fun writeInterleavedWav(pcm: FloatArray, sampleRate: Int, channels: Int, firstUri: String): java.io.File? {
        val firstPath = if (firstUri.startsWith("file://")) android.net.Uri.parse(firstUri).path else firstUri
        val firstFile = firstPath?.let { java.io.File(it) }
        val dir = firstFile?.parentFile ?: return null
        val outFile = java.io.File(dir, "merged_${System.currentTimeMillis()}.wav")
        val shortPcm = ShortArray(pcm.size) { (pcm[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val dataSize = shortPcm.size * 2
        val buf = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()); buf.putInt(36 + dataSize); buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()); buf.putInt(16)
        buf.putShort(1); buf.putShort(channels.toShort())
        buf.putInt(sampleRate); buf.putInt(sampleRate * channels * 2)
        buf.putShort((channels * 2).toShort()); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(dataSize)
        shortPcm.forEach { buf.putShort(it) }
        outFile.writeBytes(buf.array())
        return outFile
    }

    private data class WavPcmChunk(val pcm: FloatArray, val sampleRate: Int, val channels: Int)

    /** 等待当前音频播放完成，带超时保护防止永久挂起 */
    private suspend fun waitForPlaybackComplete() {
        val playback = localAudioPlaybackEngine ?: return
        try {
            // 先等待播放开始（play() 是异步的，状态可能还没变）
            withTimeout(5000) {
                playback.state.first { it is VoiceOutputState.Speaking }
            }
            // 等待播放完成
            withTimeout(PLAYBACK_TIMEOUT_MILLIS) {
                playback.state.first { it !is VoiceOutputState.Speaking }
            }
        } catch (_: TimeoutCancellationException) {
            // 超时后强制停止，防止永久挂起
            playback.stop()
        }
    }

    override fun stop() {
        isProcessing.set(false)
        localAudioPlaybackEngine?.stop()
        fallbackEngine.stop()
    }

    override fun release() {
        localAudioPlaybackEngine?.release()
        fallbackEngine.release()
    }
}
