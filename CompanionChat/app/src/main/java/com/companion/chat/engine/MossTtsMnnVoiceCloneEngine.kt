package com.companion.chat.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.companion.chat.data.voice.MossTtsMnnModelPackage
import com.companion.chat.data.voice.MossTtsMnnModelStatus
import com.companion.chat.data.voice.MossTtsNanoConfig
import com.companion.chat.data.voice.MossTtsNanoModelPackage
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

class MossTtsMnnVoiceCloneEngine(
    private val context: Context,
    private val modelDirectoryProvider: () -> String
) : VoiceCloneEngine {

    companion object {
        private const val TAG = "MossTtsMnnEngine"
        private const val WAV_HEADER_BYTES = 44
    }

    @Volatile private var cachedRuntime: MossTtsMnnRuntime? = null
    @Volatile private var cachedModelDirectory: String? = null
    @Volatile private var cachedPromptAudioCodes: Array<IntArray>? = null
    @Volatile private var cachedReferenceAudioUri: String? = null
    @Volatile private var cachedTokenizer: SentencePieceTokenizer? = null
    @Volatile private var cachedTokenizerPath: String? = null

    private fun getOrCreateTokenizer(path: String): SentencePieceTokenizer {
        cachedTokenizer?.let { if (cachedTokenizerPath == path) return it }
        synchronized(this) {
            cachedTokenizer?.let { if (cachedTokenizerPath == path) return it }
            Log.i(TAG, "创建 SentencePieceTokenizer: $path")
            val tk = SentencePieceTokenizer(path)
            cachedTokenizer = tk
            cachedTokenizerPath = path
            return tk
        }
    }

    override suspend fun synthesize(request: VoiceCloneRequest): Result<VoiceCloneResult> = withContext(Dispatchers.IO) {
        val tTotal0 = System.currentTimeMillis()
        runCatching {
            Log.i(TAG, "MNN合成开始: text=${request.text.take(40)}")

            val modelDirectory = modelDirectoryProvider().trim()
            require(modelDirectory.isNotBlank()) { "MNN模型目录未配置" }

            val status = MossTtsMnnModelPackage.inspect(modelDirectory)
            when (status) {
                MossTtsMnnModelStatus.Ready -> Unit
                MossTtsMnnModelStatus.DirectoryNotConfigured -> error("MNN模型目录未配置")
                is MossTtsMnnModelStatus.MissingFiles -> error("MNN模型文件缺失：" +
                    status.fileNames.joinToString())
            }

            val directory = File(modelDirectory)
            val config = MossTtsNanoConfig.fromDirectory(directory)

            // 必须在 MNN 加载之前创建 tokenizer，否则 libMNN.so 导出的符号会与
            // sentencepiece::filesystem::PosixReadableFile::ReadAll() 冲突导致 SIGABRT
            val tokenizerPath = File(directory, config.tokenizerModelPath).absolutePath
            val tokenizer = getOrCreateTokenizer(tokenizerPath)

            // Get or create runtime (loads libMNN.so)
            val runtime = getOrCreateRuntime(directory, config)

            // Read reference audio (with disk-persistent encoding cache)
            val promptAudioCodes: Array<IntArray> = cachedPromptAudioCodes?.takeIf { cachedReferenceAudioUri == request.referenceAudioUri }
                ?: loadEncodingCache(request.referenceAudioUri, config.numQuantizers)
                ?: run {
                    val referenceWaveform = readReferenceAudio(request.referenceAudioUri, config)
                    require(referenceWaveform.isNotEmpty()) { "参考音频为空" }

                    Log.i(TAG, "编码参考音频: ${referenceWaveform.size} samples")
                    val codes = runtime.encodeReferenceAudio(
                        referenceWaveform, referenceWaveform.size / config.channels
                    )
                    cachedPromptAudioCodes = codes
                    cachedReferenceAudioUri = request.referenceAudioUri
                    saveEncodingCache(request.referenceAudioUri, codes)
                    codes
                }
            if (cachedPromptAudioCodes == null || cachedReferenceAudioUri != request.referenceAudioUri) {
                cachedPromptAudioCodes = promptAudioCodes
                cachedReferenceAudioUri = request.referenceAudioUri
                Log.i(TAG, "编码磁盘缓存命中: codes=${promptAudioCodes.size}帧")
            } else {
                Log.i(TAG, "编码内存缓存命中: codes=${promptAudioCodes.size}帧")
            }

            // Tokenize (tokenizer 已在 MNN 加载前创建)
            val normalizedText = MossTtsTextNormalizer.normalize(request.text)
            val textTokenIds = tokenizer.encode(normalizedText)

            val chunks = if (textTokenIds.size <= 75) listOf(textTokenIds)
            else MossTtsTextNormalizer.splitByApproxTokenBudget(normalizedText, 75)
                .map { tokenizer.encode(it) }.filter { it.isNotEmpty() }

            val allFrames = mutableListOf<IntArray>()
            for ((idx, chunk) in chunks.withIndex()) {
                val requestRows = runtime.buildVoiceCloneRequestRows(chunk, promptAudioCodes)
                val frames = runtime.generateAudioFramesMnn(requestRows)
                allFrames.addAll(frames)
                if (idx < chunks.size - 1) {
                    repeat(10) { allFrames.add(IntArray(config.numQuantizers) { config.ttsConfig.audioPadTokenId }) }
                }
            }

            if (allFrames.isEmpty()) error("未生成音频帧")

            val decoded = runtime.decodeAudioMnn(allFrames)
            val pcm = channelMajorToInterleaved(decoded.data, decoded.lengthPerChannel, config.channels)
            val outputFile = writeWaveFile(pcm, config.sampleRate, config.channels)

            val elapsed = System.currentTimeMillis() - tTotal0
            Log.i(TAG, "MNN合成成功: ${outputFile.absolutePath}, 总耗时=${elapsed}ms")
            MossTtsMnnRuntime.logToFile("WAV written: ${outputFile.absolutePath} totalElapsed=${elapsed}ms")
            VoiceCloneResult(
                provider = VoiceCloneProvider.MOSS_TTS_NANO,
                audioUri = Uri.fromFile(outputFile).toString(),
                fallbackToSystemTts = false,
                message = "MNN本地合成完成"
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(TAG, "MNN合成失败: ${error.message}")
                Result.success(VoiceCloneResult(
                    provider = VoiceCloneProvider.MOSS_TTS_NANO,
                    fallbackToSystemTts = true,
                    message = error.message ?: "MNN合成失败"
                ))
            }
        )
    }

    fun preloadModels() {
        try {
            val modelDirectory = modelDirectoryProvider().trim()
            if (modelDirectory.isBlank()) return
            val status = MossTtsMnnModelPackage.inspect(modelDirectory)
            if (status !is MossTtsMnnModelStatus.Ready) return
            val directory = File(modelDirectory)
            val config = MossTtsNanoConfig.fromDirectory(directory)
            // 必须在 MNN 加载之前创建 tokenizer，避免符号冲突导致 SIGABRT
            val tokenizerPath = File(directory, config.tokenizerModelPath).absolutePath
            getOrCreateTokenizer(tokenizerPath)
            getOrCreateRuntime(directory, config)
            Log.i(TAG, "preloadModels: 模型预加载完成")
        } catch (e: Exception) {
            Log.w(TAG, "preloadModels 失败(将在首次合成时重试): ${e.message}")
        }
    }

    private fun getOrCreateRuntime(directory: File, config: MossTtsNanoConfig): MossTtsMnnRuntime {
        val existing = cachedRuntime
        if (existing != null && cachedModelDirectory == directory.absolutePath) return existing
        synchronized(this) {
            val e2 = cachedRuntime
            if (e2 != null && cachedModelDirectory == directory.absolutePath) return e2
            e2?.release()
            Log.i(TAG, "创建 MNN Runtime: ${directory.absolutePath}")
            MossTtsMnnRuntime.initDebugLog(context)
            val rt = MossTtsMnnRuntime(directory, config, context)
            cachedRuntime = rt
            cachedModelDirectory = directory.absolutePath
            cachedPromptAudioCodes = null
            cachedReferenceAudioUri = null
            rt.preloadTtsModels()
            return rt
        }
    }

    private fun getEncodingCacheFile(uriString: String): File {
        val cacheDir = File(context.filesDir, "encoding_cache").apply { mkdirs() }
        val key = uriString.hashCode().toString().replace("-", "m")
        return File(cacheDir, "enc_$key.bin")
    }

    private fun loadEncodingCache(uriString: String, expectedQuantizers: Int): Array<IntArray>? {
        val file = getEncodingCacheFile(uriString)
        if (!file.exists()) return null
        try {
            val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val frameCount = buf.int
            val quantizers = buf.int
            if (quantizers != expectedQuantizers || frameCount <= 0) return null
            val codes = Array(frameCount) { IntArray(quantizers) }
            for (f in 0 until frameCount) {
                for (q in 0 until quantizers) {
                    codes[f][q] = buf.int
                }
            }
            Log.i(TAG, "编码磁盘缓存加载: $frameCount 帧 × $quantizers")
            return codes
        } catch (e: Exception) {
            Log.w(TAG, "编码缓存加载失败: ${e.message}")
            return null
        }
    }

    private fun saveEncodingCache(uriString: String, codes: Array<IntArray>) {
        try {
            val file = getEncodingCacheFile(uriString)
            val frameCount = codes.size
            val quantizers = if (frameCount > 0) codes[0].size else 0
            val buf = ByteBuffer.allocate(8 + frameCount * quantizers * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(frameCount)
            buf.putInt(quantizers)
            for (f in 0 until frameCount) {
                for (q in 0 until quantizers) {
                    buf.putInt(codes[f][q])
                }
            }
            file.writeBytes(buf.array())
            Log.i(TAG, "编码磁盘缓存保存: $frameCount 帧 × $quantizers, ${file.length()} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "编码缓存保存失败: ${e.message}")
        }
    }

    private fun readReferenceAudio(uriString: String, config: MossTtsNanoConfig): FloatArray {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uriString))
            ?.use { it.readBytes() }
            ?: return FloatArray(0)
        if (bytes.size <= WAV_HEADER_BYTES) return FloatArray(0)
        val dataOffset = findWavDataOffset(bytes).takeIf { it >= 0 } ?: WAV_HEADER_BYTES
        val headerBuf = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.position(20)
        val audioFormat = headerBuf.short.toInt() and 0xFFFF
        val wavChannels = headerBuf.short.toInt() and 0xFFFF
        val wavSampleRate = headerBuf.int
        headerBuf.position(34)
        val bitsPerSample = headerBuf.short.toInt() and 0xFFFF


        val dataBuf = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = bitsPerSample / 8
        val totalSamples = if (bytesPerSample > 0) dataBuf.remaining() / bytesPerSample else 0
        val samplesPerChannel = totalSamples / wavChannels
        if (samplesPerChannel <= 0) return FloatArray(0)
        val channels = wavChannels.coerceIn(1, 2)
        val rawResult = FloatArray(channels * samplesPerChannel)

        when {
            audioFormat == 1 && bitsPerSample == 16 -> {
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val s = if (dataBuf.hasRemaining()) dataBuf.short else 0
                        if (ch < channels) rawResult[ch * samplesPerChannel + i] = s / Short.MAX_VALUE.toFloat()
                    }
                }
            }
            else -> {
                for (i in 0 until samplesPerChannel) {
                    for (ch in 0 until wavChannels) {
                        val s = if (dataBuf.hasRemaining()) dataBuf.short else 0
                        if (ch < channels) rawResult[ch * samplesPerChannel + i] = s / Short.MAX_VALUE.toFloat()
                    }
                }
            }
        }

        val result = if (wavSampleRate != config.sampleRate) {
            resample(rawResult, samplesPerChannel, channels, wavSampleRate, config.sampleRate)
        } else rawResult

        val rspc = if (wavSampleRate != config.sampleRate)
            (samplesPerChannel.toLong() * config.sampleRate / wavSampleRate).toInt()
        else samplesPerChannel

        if (channels == 1 && config.channels == 2) {
            val stereo = FloatArray(2 * rspc)
            System.arraycopy(result, 0, stereo, 0, rspc)
            System.arraycopy(result, 0, stereo, rspc, rspc)
            return stereo
        }
        return result
    }

    private fun resample(data: FloatArray, inputLen: Int, channels: Int, src: Int, dst: Int): FloatArray {
        if (src == dst || inputLen <= 0) return data
        val outLen = (inputLen.toLong() * dst / src).toInt()
        if (outLen <= 0) return data
        val result = FloatArray(channels * outLen)
        val ratio = src.toDouble() / dst
        for (ch in 0 until channels) {
            val srcBase = ch * inputLen
            val dstBase = ch * outLen
            for (i in 0 until outLen) {
                val pos = i * ratio
                val idx = pos.toInt()
                val frac = pos - idx
                val s0 = if (idx < inputLen) data[srcBase + idx] else 0f
                val s1 = if (idx + 1 < inputLen) data[srcBase + idx + 1] else s0
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
        val pcm = ShortArray(waveform.size) {
            (waveform[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }
        val dir = File(context.filesDir, "generated_audio/mnn_tts").apply { mkdirs() }
        val file = File(dir, "mnn_${System.currentTimeMillis()}.wav")
        val dataSize = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()); buf.putInt(16)
        buf.putShort(1); buf.putShort(channels.toShort())
        buf.putInt(sampleRate); buf.putInt(sampleRate * channels * 2)
        buf.putShort((channels * 2).toShort()); buf.putShort(16)
        buf.put("data".toByteArray()); buf.putInt(dataSize)
        pcm.forEach { buf.putShort(it) }
        file.writeBytes(buf.array())
        return file
    }

    private fun channelMajorToInterleaved(data: FloatArray, lenPerCh: Int, channels: Int): FloatArray {
        if (channels <= 1) return data.copyOf(lenPerCh)
        val r = FloatArray(lenPerCh * channels)
        for (s in 0 until lenPerCh) {
            for (ch in 0 until channels) {
                val src = ch * lenPerCh + s
                r[s * channels + ch] = if (src < data.size) data[src] else 0f
            }
        }
        return r
    }

    fun release() {
        synchronized(this) {
            cachedRuntime?.release()
            cachedRuntime = null
            cachedModelDirectory = null
            cachedTokenizer = null
            cachedTokenizerPath = null
        }
    }
}
