package com.companion.chat.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.companion.chat.data.voice.MossTtsNanoConfig
import com.companion.chat.data.voice.MossTtsNanoModelPackage
import com.companion.chat.data.voice.MossTtsNanoModelStatus
import com.companion.chat.data.voice.VoiceCloneEngine
import com.companion.chat.data.voice.VoiceCloneProvider
import com.companion.chat.data.voice.VoiceCloneRequest
import com.companion.chat.data.voice.VoiceCloneResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class MossTtsNanoVoiceCloneEngine(
    private val context: Context,
    private val modelDirectoryProvider: () -> String
) : VoiceCloneEngine {

    companion object {
        private const val TAG = "MossTtsVoiceClone"
        private const val WAV_HEADER_BYTES = 44

        /** Emoji 和特殊符号的正则表达式（不包含CJK汉字） */
        private val EMOJI_REGEX = Regex(
            "[\\x{1F600}-\\x{1F64F}" +  // 情感类表情
            "\\x{1F300}-\\x{1F5FF}" +    // 符号和象形文字
            "\\x{1F680}-\\x{1F6FF}" +    // 交通和地图符号
            "\\x{1F1E0}-\\x{1F1FF}" +    // 旗帜
            "\\x{2702}-\\x{27B0}" +      // 装饰符号
            "\\x{1F900}-\\x{1F9FF}" +    // 补充符号
            "\\x{1FA00}-\\x{1FA6F}" +    // 棋子
            "\\x{1FA70}-\\x{1FAFF}" +    // 符号扩展
            "\\x{2600}-\\x{26FF}" +      // 杂项符号
            "\\x{2700}-\\x{27BF}" +      // 装饰符号
            "\\x{FE00}-\\x{FE0F}" +      // 变体选择符
            "\\x{200D}" +                // 零宽连接符
            "\\x{20E3}" +                // 组合用 enclosing keycap
            "\\x{25A0}-\\x{25FF}" +      // 几何形状
            "\\x{2B50}-\\x{2B55}" +      // 星星和圆圈
            "\\x{231A}-\\x{231B}" +      // 手表和沙漏
            "\\x{23E9}-\\x{23F3}" +      // 快进等
            "\\x{23F8}-\\x{23FA}" +      // 暂停等
            "\\x{25FB}-\\x{25FE}" +      // 方块
            "\\x{2934}-\\x{2935}" +      // 箭头
            "\\x{2B05}-\\x{2B07}" +      // 箭头
            "\\x{2B1B}-\\x{2B1C}" +      // 方块
            "\\x{3030}" +                // 波浪线
            "\\x{303D}" +                // 部分替代标记
            "\\x{3297}" +                // 祝贺
            "\\x{3299}" +                // 秘密
            "\\x{FE0F}" +                // 变体选择符
            "\\x{200B}-\\x{200D}" +      // 零宽字符
            "\\x{2066}-\\x{2069}" +      // 双向控制字符
            "\\x{FFF9}-\\x{FFFB}" +      // 属性标记
            "\\x{1F000}-\\x{1F02F}" +    // 麻将牌
            "\\x{1F0A0}-\\x{1F0FF}" +    // 扑克牌
            "\\x{1F100}-\\x{1F1FF}" +    // 封闭字母数字符号
            "\\x{1F200}-\\x{1F2FF}" +    // 封闭表意文字
            "\\x{1F700}-\\x{1F77F}" +    // 炼金术符号
            "\\x{1F780}-\\x{1F7FF}" +    // 几何形状扩展
            "\\x{1F800}-\\x{1F8FF}" +    // 补充箭头
            "\\x{1F900}-\\x{1F9FF}" +    // 补充符号和象形文字
            "\\x{1FA00}-\\x{1FA6F}" +    // 棋子符号
            "\\x{1FA70}-\\x{1FAFF}" +    // 符号和象形文字扩展
            "]"
        )

        /** 过滤表情符号和不可读字符 */
        fun filterEmojis(text: String): String {
            var result = EMOJI_REGEX.replace(text, "")
            // 连续空格合并为单个空格
            result = result.replace(Regex("\\s+"), " ")
            return result.trim()
        }
    }

    /** Lazily created runtime; reused across calls to avoid reloading ONNX models. */
    @Volatile
    private var cachedRuntime: MossTtsNanoRuntime? = null
    @Volatile
    private var cachedModelDirectory: String? = null

    /** Lazily created tokenizer. */
    @Volatile
    private var cachedTokenizer: MossTtsTokenizer? = null

    /** Guard for runtime/tokenizer creation and release. */
    private val lock = Any()

    /** 预热模型：提前加载 tokenizer 和 runtime，减少首次合成延迟 */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        try {
            val modelDirectory = modelDirectoryProvider().trim()
            if (modelDirectory.isBlank()) {
                Log.w(TAG, "预热跳过：模型目录未配置")
                return@withContext
            }

            when (val status = MossTtsNanoModelPackage.inspect(modelDirectory)) {
                MossTtsNanoModelStatus.Ready -> {
                    Log.i(TAG, "预热 MOSS TTS 模型: $modelDirectory")
                    val directory = File(modelDirectory)
                    val config = MossTtsNanoConfig.fromDirectory(directory)
                    getOrCreateTokenizer(directory, config)
                    getOrCreateRuntime(directory, config, cachedTokenizer!!)
                    Log.i(TAG, "MOSS TTS 模型预热完成")
                }
                else -> {
                    Log.w(TAG, "预热跳过：模型状态=$status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MOSS TTS 预热失败: ${e.message}", e)
        }
    }

    override suspend fun synthesize(request: VoiceCloneRequest): Result<VoiceCloneResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 过滤表情符号
            val filteredText = filterEmojis(request.text)
            require(filteredText.isNotBlank()) { "过滤表情后文本为空" }
            Log.i(TAG, "文本过滤: 原长度=${request.text.length}, 过滤后=${filteredText.length}")

            require(request.referenceAudioUri.isNotBlank()) { "角色参考音频 URI 未配置" }

            val modelDirectory = modelDirectoryProvider().trim()
            Log.i(TAG, "开始 MOSS TTS 合成: modelDir=$modelDirectory, textLength=${filteredText.length}")

            when (val status = MossTtsNanoModelPackage.inspect(modelDirectory)) {
                MossTtsNanoModelStatus.Ready -> Unit
                MossTtsNanoModelStatus.DirectoryNotConfigured -> error("moss-tts-nano 模型目录未配置")
                is MossTtsNanoModelStatus.InvalidConfig -> error("moss-tts-nano 配置无效：${status.message}")
                is MossTtsNanoModelStatus.MissingFiles -> error("moss-tts-nano 模型文件缺失：${status.fileNames.joinToString()}")
            }

            val directory = File(modelDirectory)
            val config = MossTtsNanoConfig.fromDirectory(directory)

            // Get or create tokenizer
            val tokenizer = getOrCreateTokenizer(directory, config)

            // Get or create runtime
            val runtime = getOrCreateRuntime(directory, config, tokenizer)

            // Read and encode reference audio
            Log.i(TAG, "读取参考音频: ${request.referenceAudioUri}")
            val referenceWaveform = readReferenceAudio(request.referenceAudioUri, config)
            require(referenceWaveform.isNotEmpty()) { "参考音频为空或格式不支持" }

            Log.i(TAG, "编码参考音频: ${referenceWaveform.size} samples")
            val promptAudioCodes = runtime.encodeReferenceAudio(
                referenceWaveform,
                referenceWaveform.size / config.channels
            )

            // Synthesize
            Log.i(TAG, "开始文本合成: text=${filteredText.take(40)}...")
            val result = runtime.synthesize(
                text = filteredText,
                promptAudioCodes = promptAudioCodes,
                onProgress = { msg -> Log.d(TAG, "合成进度: $msg") }
            )

            when (result) {
                is MossTtsNanoRuntime.SynthesisResult.Success -> {
                    val outputFile = writeWaveFile(
                        result.waveform,
                        result.sampleRate,
                        result.channels
                    )
                    Log.i(TAG, "MOSS TTS 合成成功: ${outputFile.absolutePath}")
                    VoiceCloneResult(
                        provider = VoiceCloneProvider.MOSS_TTS_NANO,
                        audioUri = Uri.fromFile(outputFile).toString(),
                        fallbackToSystemTts = false,
                        message = "moss-tts-nano 本地合成完成"
                    )
                }
                is MossTtsNanoRuntime.SynthesisResult.Error -> {
                    Log.e(TAG, "MOSS TTS 合成失败: ${result.message}")
                    VoiceCloneResult(
                        provider = VoiceCloneProvider.MOSS_TTS_NANO,
                        fallbackToSystemTts = true,
                        message = result.message
                    )
                }
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                // Let CancellationException propagate for coroutine cooperative cancellation
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "MOSS TTS 异常: ${error.message}", error)
                Result.success(
                    VoiceCloneResult(
                        provider = VoiceCloneProvider.MOSS_TTS_NANO,
                        fallbackToSystemTts = true,
                        message = error.message ?: "moss-tts-nano 合成失败"
                    )
                )
            }
        )
    }

    private fun getOrCreateTokenizer(directory: File, config: MossTtsNanoConfig): MossTtsTokenizer {
        cachedTokenizer?.let { return it }
        synchronized(lock) {
            cachedTokenizer?.let { return it }
            val tokenizerPath = File(directory, config.tokenizerModelPath).absolutePath
            val tokenizer = try {
                SentencePieceTokenizer(tokenizerPath)
            } catch (e: Exception) {
                Log.w(TAG, "SentencePiece JNI 不可用 (${e.message})，使用占位分词器。" +
                    " 需要在 CMake 中编译 sentencepiece C++ 库以获得完整支持。")
                PlaceholderMossTokenizer()
            }
            cachedTokenizer = tokenizer
            return tokenizer
        }
    }

    private fun getOrCreateRuntime(
        directory: File,
        config: MossTtsNanoConfig,
        tokenizer: MossTtsTokenizer
    ): MossTtsNanoRuntime {
        val existing = cachedRuntime
        if (existing != null && cachedModelDirectory == directory.absolutePath) {
            return existing
        }
        synchronized(lock) {
            val existing2 = cachedRuntime
            if (existing2 != null && cachedModelDirectory == directory.absolutePath) {
                return existing2
            }
            existing2?.release()
            Log.i(TAG, "创建 MossTtsNanoRuntime: ${directory.absolutePath}")
            val runtime = MossTtsNanoRuntime(directory, config, tokenizer)
            cachedRuntime = runtime
            cachedModelDirectory = directory.absolutePath
            return runtime
        }
    }

    /**
     * 读取参考音频 WAV 并转为 channel-major Float32 布局 [ch0_samples..., ch1_samples...]。
     * ONNX codec_encode 模型期望的输入形状是 [1, channels, waveformLength]。
     *
     * 支持:
     *   - PCM Int16 (audioFormat=1, bitsPerSample=16)
     *   - PCM Int32 (audioFormat=1, bitsPerSample=32)
     *   - IEEE Float32 (audioFormat=3, bitsPerSample=32)
     *   - 自动重采样到 config.sampleRate (48kHz)
     *   - 单声道自动上混为立体声
     */
    private fun readReferenceAudio(uriString: String, config: MossTtsNanoConfig): FloatArray {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uriString))
            ?.use { it.readBytes() }
            ?: error("无法读取参考音频")
        if (bytes.size <= WAV_HEADER_BYTES) return FloatArray(0)

        val dataOffset = findWavDataOffset(bytes).takeIf { it >= 0 } ?: WAV_HEADER_BYTES

        // Parse WAV header
        val headerBuf = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.position(20)
        val audioFormat = headerBuf.short.toInt() and 0xFFFF
        val wavChannels = headerBuf.short.toInt() and 0xFFFF
        val wavSampleRate = headerBuf.int
        headerBuf.position(34)
        val bitsPerSample = headerBuf.short.toInt() and 0xFFFF
        Log.i(TAG, "参考音频 WAV: format=$audioFormat, channels=$wavChannels, " +
            "sampleRate=$wavSampleRate, bitsPerSample=$bitsPerSample, dataOffset=$dataOffset")

        val dataBuf = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = bitsPerSample / 8
        val totalSamples = if (bytesPerSample > 0) dataBuf.remaining() / bytesPerSample else 0
        val samplesPerChannel = totalSamples / wavChannels
        if (samplesPerChannel <= 0) return FloatArray(0)

        // Read interleaved samples -> de-interleave to channel-major Float32
        val channels = wavChannels.coerceIn(1, 2)
        val rawResult = FloatArray(channels * samplesPerChannel)

        when {
            // PCM Int16
            audioFormat == 1 && bitsPerSample == 16 -> {
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val rawSample = if (dataBuf.hasRemaining()) dataBuf.short else 0
                        val floatSample = rawSample / Short.MAX_VALUE.toFloat()
                        if (ch < channels) {
                            rawResult[ch * samplesPerChannel + i] = floatSample
                        }
                    }
                }
            }
            // PCM Int32
            audioFormat == 1 && bitsPerSample == 32 -> {
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val rawSample = if (dataBuf.hasRemaining()) dataBuf.int else 0
                        val floatSample = rawSample.toFloat() / Int.MAX_VALUE.toFloat()
                        if (ch < channels) {
                            rawResult[ch * samplesPerChannel + i] = floatSample
                        }
                    }
                }
            }
            // IEEE Float32
            audioFormat == 3 && bitsPerSample == 32 -> {
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val floatSample = if (dataBuf.hasRemaining()) dataBuf.float else 0f
                        if (ch < channels) {
                            rawResult[ch * samplesPerChannel + i] = floatSample
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "不支持的 WAV 格式: audioFormat=$audioFormat, bitsPerSample=$bitsPerSample，尝试按 Int16 读取")
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val rawSample = if (dataBuf.hasRemaining()) dataBuf.short else 0
                        val floatSample = rawSample / Short.MAX_VALUE.toFloat()
                        if (ch < channels) {
                            rawResult[ch * samplesPerChannel + i] = floatSample
                        }
                    }
                }
            }
        }

        // Resample to target sample rate if necessary (e.g. 44100 → 48000)
        val targetSampleRate = config.sampleRate
        val result = if (wavSampleRate != targetSampleRate) {
            Log.i(TAG, "重采样: ${wavSampleRate}Hz → ${targetSampleRate}Hz")
            resampleChannelMajor(rawResult, samplesPerChannel, channels, wavSampleRate, targetSampleRate)
        } else {
            rawResult
        }

        val resultSamplesPerChannel = if (wavSampleRate != targetSampleRate) {
            (samplesPerChannel.toLong() * targetSampleRate / wavSampleRate).toInt()
        } else {
            samplesPerChannel
        }

        // If WAV is mono but codec expects stereo, duplicate channel
        if (channels == 1 && config.channels == 2) {
            val stereo = FloatArray(2 * resultSamplesPerChannel)
            System.arraycopy(result, 0, stereo, 0, resultSamplesPerChannel)
            System.arraycopy(result, 0, stereo, resultSamplesPerChannel, resultSamplesPerChannel)
            return stereo
        }

        return result
    }

    /**
     * 线性插值重采样 (channel-major 布局)。
     * 对于短参考音频（几秒），性能开销可忽略。
     */
    private fun resampleChannelMajor(
        data: FloatArray,
        inputLengthPerChannel: Int,
        channels: Int,
        srcRate: Int,
        dstRate: Int
    ): FloatArray {
        if (srcRate == dstRate || inputLengthPerChannel <= 0) return data
        val outputLength = (inputLengthPerChannel.toLong() * dstRate / srcRate).toInt()
        if (outputLength <= 0) return data
        val result = FloatArray(channels * outputLength)
        val ratio = srcRate.toDouble() / dstRate
        for (ch in 0 until channels) {
            val srcBase = ch * inputLengthPerChannel
            val dstBase = ch * outputLength
            for (i in 0 until outputLength) {
                val srcPos = i * ratio
                val srcIdx = srcPos.toInt()
                val frac = srcPos - srcIdx
                val s0 = if (srcIdx < inputLengthPerChannel) data[srcBase + srcIdx] else 0f
                val s1 = if (srcIdx + 1 < inputLengthPerChannel) data[srcBase + srcIdx + 1] else s0
                result[dstBase + i] = (s0 + frac * (s1 - s0)).toFloat()
            }
        }
        return result
    }

    private fun findWavDataOffset(bytes: ByteArray): Int {
        val marker = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
        for (index in 12 until bytes.size - 8) {
            if (marker.indices.all { bytes[index + it] == marker[it] }) return index + 8
        }
        return -1
    }

    private fun writeWaveFile(waveform: FloatArray, sampleRate: Int, channels: Int): File {
        require(waveform.isNotEmpty()) { "ONNX 未输出音频波形" }

        // Convert interleaved float PCM to Int16 PCM
        val pcm = ShortArray(waveform.size) { index ->
            (waveform[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }

        val outputDirectory = File(context.filesDir, "generated_audio/moss_tts_nano").apply { mkdirs() }
        val outputFile = File(outputDirectory, "moss_${System.currentTimeMillis()}.wav")

        // Write WAV file (PCM 16-bit)
        val totalSamples = pcm.size
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataSize = totalSamples * 2

        val header = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        // fmt chunk
        header.put("fmt ".toByteArray())
        header.putInt(16)            // chunk size
        header.putShort(1)           // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(16)          // bits per sample
        // data chunk
        header.put("data".toByteArray())
        header.putInt(dataSize)

        // Write PCM samples (interleaved Int16)
        for (sample in pcm) {
            header.putShort(sample)
        }

        outputFile.writeBytes(header.array())
        return outputFile
    }

    fun release() {
        synchronized(lock) {
            cachedRuntime?.release()
            cachedRuntime = null
            cachedTokenizer?.close()
            cachedTokenizer = null
            cachedModelDirectory = null
        }
    }
}

/**
 * SentencePiece 分词器（JNI 包装）。
 * 通过 moss_tts_tokenizer.cpp JNI 桥接 sentencepiece C++ 库。
 * CMakeLists.txt 已配置编译 libmoss_tts_tokenizer.so。
 *
 * 当 sentencepiece 源码不可用时，C++ 端自动编译为字符级 fallback，
 * nativeCreate 仍返回非零 handle，nativeEncode 走 UTF-32 code point 编码。
 */
class SentencePieceTokenizer(modelPath: String) : MossTtsTokenizer {

    companion object {
        init {
            try {
                System.loadLibrary("moss_tts_tokenizer")
            } catch (e: UnsatisfiedLinkError) {
                // Library not available; will fall back to placeholder behavior
            }
        }
    }

    @Volatile
    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeCreate(modelPath)
        if (nativeHandle == 0L) {
            throw RuntimeException("无法加载 SentencePiece 模型: $modelPath")
        }
    }

    override fun encode(text: String): IntArray {
        if (nativeHandle == 0L) return text.map { it.code }.toIntArray()
        return nativeEncode(nativeHandle, text)
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    private external fun nativeCreate(modelPath: String): Long
    private external fun nativeEncode(handle: Long, text: String): IntArray
    private external fun nativeDestroy(handle: Long)
}
