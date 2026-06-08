package com.companion.chat.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.companion.chat.data.voice.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * MOSS-TTS-Nano ONNX 自回归解码运行时。
 * 移植自 OpenMOSS/MOSS-TTS-Nano-Reader/extension/browser_onnx_runtime.js。
 *
 * 管线流程:
 * 1. 文本规范化 → SentencePiece 分词
 * 2. 参考音频编码 (codec_encode ONNX)
 * 3. 构建输入张量 [1, seqLen, n_vq+1]
 * 4. Prefill (moss_tts_prefill ONNX) → global_hidden
 * 5. 逐帧自回归解码 (local_cached_step / local_fixed_sampled_frame ONNX)
 * 6. 每帧反馈到 decode_step ONNX 更新全局隐藏状态
 * 7. 音频解码 (codec_decode_full ONNX) → PCM waveform
 * 8. 输出 WAV
 */
class MossTtsNanoRuntime(
    private val modelDirectory: File,
    private val config: MossTtsNanoConfig,
    private val tokenizer: MossTtsTokenizer
) {
    companion object {
        private const val TAG = "MossTtsNanoRuntime"
        private const val VOICE_CLONE_MAX_TEXT_TOKENS = 75
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(4)
    }

    // ── Lazy-loaded ONNX sessions ──
    private val prefillSession: OrtSession by lazy { createSession(config.ttsPrefillModelPath) }
    private val decodeStepSession: OrtSession by lazy { createSession(config.ttsDecodeStepModelPath) }
    private val localCachedStepSession: OrtSession by lazy { createSession(config.ttsLocalCachedStepModelPath) }
    // localFixedSampledFrameSession: 保留供未来 non-KV-cache 解码路径使用。
    // 当前所有模型均使用 localCachedStepSession (KV cache 模式)，此模型不加载。
    // private val localFixedSampledFrameSession: OrtSession by lazy { createSession(config.ttsLocalFixedSampledFrameModelPath) }
    private val codecEncodeSession: OrtSession by lazy { createSession(config.audioTokenizerEncodeModelPath) }
    private val codecDecodeFullSession: OrtSession by lazy { createSession(config.audioTokenizerDecodeFullModelPath) }

    // ── KV cache name mapping ──
    private val decodePastInputNames: List<String> = config.ttsMetaOnnx.decodeInputNames.drop(2)
    private val decodePresentOutputNames: List<String> = config.ttsMetaOnnx.decodeOutputNames.drop(1)

    private val localCachedPastInputNames: List<String> =
        if (config.ttsMetaOnnx.localCachedInputNames.size > 6) {
            config.ttsMetaOnnx.localCachedInputNames.drop(6)
        } else if (config.ttsMetaOnnx.localCachedOutputNames.size > 2) {
            config.ttsMetaOnnx.localCachedOutputNames.drop(2).map { name ->
                name.replace("local_present_", "local_past_")
            }
        } else {
            emptyList()
        }
    private val localCachedPresentOutputNames: List<String> =
        if (config.ttsMetaOnnx.localCachedOutputNames.size > 2) {
            config.ttsMetaOnnx.localCachedOutputNames.drop(2)
        } else {
            emptyList()
        }

    // ── Mutable decode step feed state ──
    private var decodePastFeeds: MutableMap<String, OnnxTensor> = mutableMapOf()
    private var localCachedPastFeeds: MutableMap<String, OnnxTensor> = mutableMapOf()

    private fun createSession(relativePath: String): OrtSession {
        val file = File(modelDirectory, relativePath)
        Log.i(TAG, "加载 ONNX 模型: ${file.absolutePath}")
        return env.createSession(file.absolutePath, sessionOptions)
    }

    /**
     * 深拷贝 OrtSession.Result 中的张量。
     * ONNX Runtime Java API 中，Result.close() 可能释放底层原生内存，
     * 导致已提取的 OnnxTensor 引用失效。KV cache 需要跨 step 保存张量，
     * 因此必须复制数据到独立缓冲区。
     */
    private fun deepCopyTensor(tensor: OnnxTensor): OnnxTensor {
        val shape = tensor.info.shape
        val data = FloatArray(tensor.floatBuffer.remaining())
        tensor.floatBuffer.get(data)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    // ═══════════════════════════════════════════════════════
    //  1. 参考音频编码
    // ═══════════════════════════════════════════════════════

    /**
     * 将参考音频 WAV 编码为离散 audio codes。
     * @param waveform Float32 PCM 数据 (已归一化到 [-1, 1])
     * @param waveformLength 每个通道的采样点数
     * @return [numFrames, numQuantizers] 的 audio code 数组
     */
    fun encodeReferenceAudio(waveform: FloatArray, waveformLength: Int): Array<IntArray> {
        Log.i(TAG, "编码参考音频: waveformLength=$waveformLength, channels=${config.channels}")

        val feeds = mutableMapOf<String, OnnxTensor>()
        val waveformTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(waveform),
            longArrayOf(1, config.channels.toLong(), waveformLength.toLong())
        )
        feeds["waveform"] = waveformTensor

        // Optional input_lengths
        val inputNames = codecEncodeSession.inputNames
        if (inputNames.contains("input_lengths")) {
            feeds["input_lengths"] = OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(waveformLength)), longArrayOf(1)
            )
        }

        val result = codecEncodeSession.run(feeds)
        val audioCodesTensor = result.get("audio_codes").get() as OnnxTensor
        val codeLengthsTensor = result.get("audio_code_lengths").get() as OnnxTensor

        val codeLength = codeLengthsTensor.intBuffer.get(0)
        val codesData = audioCodesTensor.intBuffer
        val codes = Array(codeLength) { frameIdx ->
            IntArray(config.numQuantizers) { qIdx ->
                codesData.get(frameIdx * config.numQuantizers + qIdx)
            }
        }

        // Cleanup
        feeds.values.forEach { it.close() }
        result.close()

        Log.i(TAG, "参考音频编码完成: $codeLength frames × ${config.numQuantizers} quantizers")
        return codes
    }

    // ═══════════════════════════════════════════════════════
    //  2. 输入张量构建
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 voice clone 请求的输入行。
     * 结构:
     *   [prefix text rows] + [audio prefix rows (ref audio)] + [suffix text rows]
     */
    fun buildVoiceCloneRequestRows(
        textTokenIds: IntArray,
        promptAudioCodes: Array<IntArray>
    ): RequestRows {
        val rowWidth = config.ttsConfig.nVq + 1
        val pad = config.ttsConfig.audioPadTokenId
        val rows = mutableListOf<IntArray>()

        // 1. Prefix text rows: user_prompt_prefix + audio_start
        val prefixIds = config.promptTemplates.userPromptPrefixTokenIds +
                intArrayOf(config.ttsConfig.audioStartTokenId)
        for (id in prefixIds) {
            rows.add(buildTextRow(id, rowWidth, pad))
        }

        // 2. Audio prefix rows: reference audio codes
        for (codeRow in promptAudioCodes) {
            val row = IntArray(rowWidth) { pad }
            row[0] = config.ttsConfig.audioUserSlotTokenId
            for (q in 0 until minOf(codeRow.size, config.ttsConfig.nVq)) {
                row[q + 1] = codeRow[q]
            }
            rows.add(row)
        }

        // 3. Suffix text rows: audio_end + after_ref + text + assistant_prefix + audio_start
        val suffixIds = intArrayOf(config.ttsConfig.audioEndTokenId) +
                config.promptTemplates.userPromptAfterReferenceTokenIds +
                textTokenIds +
                config.promptTemplates.assistantPromptPrefixTokenIds +
                intArrayOf(config.ttsConfig.audioStartTokenId)
        for (id in suffixIds) {
            rows.add(buildTextRow(id, rowWidth, pad))
        }

        val seqLen = rows.size
        val inputIds = IntArray(seqLen * rowWidth)
        var offset = 0
        for (row in rows) {
            System.arraycopy(row, 0, inputIds, offset, rowWidth)
            offset += rowWidth
        }
        val attentionMask = IntArray(seqLen) { 1 }

        return RequestRows(inputIds, attentionMask, seqLen, rowWidth)
    }

    private fun buildTextRow(tokenId: Int, rowWidth: Int, pad: Int): IntArray {
        val row = IntArray(rowWidth) { pad }
        row[0] = tokenId
        return row
    }

    data class RequestRows(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val seqLen: Int,
        val rowWidth: Int
    )

    // ═══════════════════════════════════════════════════════
    //  3. Prefill (全局编码器)
    // ═══════════════════════════════════════════════════════

    private data class PrefillResult(
        val globalHidden: FloatArray,
        val hiddenSize: Int,
        val pastValidLength: Int,
        val outputs: OrtSession.Result
    )

    private fun runPrefill(requestRows: RequestRows): PrefillResult {
        Log.i(TAG, "Prefill: seqLen=${requestRows.seqLen}, rowWidth=${requestRows.rowWidth}")

        val inputIdsTensor = OnnxTensor.createTensor(
            env, IntBuffer.wrap(requestRows.inputIds),
            longArrayOf(1, requestRows.seqLen.toLong(), requestRows.rowWidth.toLong())
        )
        val maskTensor = OnnxTensor.createTensor(
            env, IntBuffer.wrap(requestRows.attentionMask),
            longArrayOf(1, requestRows.seqLen.toLong())
        )

        val outputs = prefillSession.run(mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to maskTensor
        ))

        val globalHiddenTensor = outputs.get("global_hidden").get() as OnnxTensor
        val dims = globalHiddenTensor.info.shape
        val hiddenSize = dims.last().toInt()
        val seqLen = dims[dims.size - 2].toInt()

        // Extract last hidden state: [1, hiddenSize]
        val allData = globalHiddenTensor.floatBuffer
        val lastHidden = FloatArray(hiddenSize)
        val startOffset = (seqLen - 1) * hiddenSize
        allData.position(startOffset)
        allData.get(lastHidden)

        val pastValidLength = requestRows.attentionMask.sum()

        // Update decode past feeds from prefill outputs
        updateDecodePastFeeds(outputs)

        inputIdsTensor.close()
        maskTensor.close()
        // Note: globalHiddenTensor is kept alive via outputs; don't close outputs yet

        return PrefillResult(lastHidden, hiddenSize, pastValidLength, outputs)
    }

    // ═══════════════════════════════════════════════════════
    //  4. Decode Step (全局解码器步进)
    // ═══════════════════════════════════════════════════════

    private fun runDecodeStep(
        frameTokens: IntArray,
        pastValidLength: Int,
        previousOutputs: OrtSession.Result
    ): DecodeStepResult {
        val rowWidth = config.ttsConfig.nVq + 1
        val rowData = IntArray(rowWidth) { config.ttsConfig.audioPadTokenId }
        rowData[0] = config.ttsConfig.audioAssistantSlotTokenId
        for (i in frameTokens.indices) {
            rowData[i + 1] = frameTokens[i]
        }

        val feeds = mutableMapOf<String, OnnxTensor>()
        feeds["input_ids"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(rowData),
            longArrayOf(1, 1, rowWidth.toLong())
        )
        feeds["past_valid_lengths"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(pastValidLength)), longArrayOf(1)
        )
        // Add past KV feeds from previous step
        feeds.putAll(decodePastFeeds)

        val outputs = decodeStepSession.run(feeds)

        val globalHiddenTensor = outputs.get("global_hidden").get() as OnnxTensor
        val dims = globalHiddenTensor.info.shape
        val hiddenSize = dims.last().toInt()
        val seqLen = dims[dims.size - 2].toInt()
        val allData = globalHiddenTensor.floatBuffer
        val lastHidden = FloatArray(hiddenSize)
        allData.position((seqLen - 1) * hiddenSize)
        allData.get(lastHidden)

        // 保存旧的 KV cache 引用，以便正确关闭
        val oldDecodePastFeeds = decodePastFeeds
        updateDecodePastFeeds(outputs)

        // Cleanup old feeds (关闭旧的 KV cache，而不是新更新的)
        oldDecodePastFeeds.values.forEach { it.close() }
        feeds.values.forEach { it.close() }
        previousOutputs.close()

        return DecodeStepResult(lastHidden, hiddenSize, outputs)
    }

    private data class DecodeStepResult(
        val globalHidden: FloatArray,
        val hiddenSize: Int,
        val outputs: OrtSession.Result
    )

    private fun updateDecodePastFeeds(outputs: OrtSession.Result) {
        val newFeeds = mutableMapOf<String, OnnxTensor>()
        for (i in decodePastInputNames.indices) {
            val presentName = decodePresentOutputNames[i]
            val pastName = decodePastInputNames[i]
            val tensor = outputs.get(presentName).get() as OnnxTensor
            newFeeds[pastName] = deepCopyTensor(tensor)
        }
        decodePastFeeds = newFeeds
    }

    // ═══════════════════════════════════════════════════════
    //  5. Local Cached Step (本地自回归解码器 - KV cache 版)
    // ═══════════════════════════════════════════════════════

    private fun resetLocalCachedState() {
        localCachedPastFeeds.values.forEach { it.close() }
        localCachedPastFeeds = mutableMapOf()
        for (name in localCachedPastInputNames) {
            val tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(FloatArray(0)),
                longArrayOf(1, 0, config.ttsMetaOnnx.localHeads.toLong(), config.ttsMetaOnnx.localHeadDim.toLong())
            )
            localCachedPastFeeds[name] = tensor
        }
    }

    private data class LocalCachedStepResult(
        val textLogits: FloatArray,
        val audioLogits: OnnxTensor,
        val outputs: OrtSession.Result
    )

    private fun runLocalCachedStep(
        globalHidden: FloatArray,
        hiddenSize: Int,
        textTokenId: Int,
        audioTokenId: Int,
        channelIndex: Int,
        stepType: Int,
        pastValidLength: Int
    ): LocalCachedStepResult {
        val feeds = mutableMapOf<String, OnnxTensor>()
        feeds["global_hidden"] = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(globalHidden),
            longArrayOf(1, hiddenSize.toLong())
        )
        feeds["text_token_id"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(textTokenId)), longArrayOf(1)
        )
        feeds["audio_token_id"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(audioTokenId)), longArrayOf(1)
        )
        feeds["channel_index"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(channelIndex)), longArrayOf(1)
        )
        feeds["step_type"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(stepType)), longArrayOf(1)
        )
        feeds["past_valid_lengths"] = OnnxTensor.createTensor(
            env, IntBuffer.wrap(intArrayOf(pastValidLength)), longArrayOf(1)
        )
        feeds.putAll(localCachedPastFeeds)

        val outputs = localCachedStepSession.run(feeds)

        val textLogitsTensor = outputs.get("text_logits").get() as OnnxTensor
        val textLogits = FloatArray(textLogitsTensor.floatBuffer.remaining())
        textLogitsTensor.floatBuffer.get(textLogits)

        val audioLogitsTensor = outputs.get("audio_logits").get() as OnnxTensor

        // Update local cached past feeds (deep copy to survive result.close())
        val newFeeds = mutableMapOf<String, OnnxTensor>()
        for (i in localCachedPastInputNames.indices) {
            val presentName = localCachedPresentOutputNames[i]
            val pastName = localCachedPastInputNames[i]
            val tensor = outputs.get(presentName).get() as OnnxTensor
            newFeeds[pastName] = deepCopyTensor(tensor)
        }
        // Cleanup old feeds
        localCachedPastFeeds.values.forEach { it.close() }
        feeds.values.forEach { it.close() }
        localCachedPastFeeds = newFeeds

        return LocalCachedStepResult(textLogits, audioLogitsTensor, outputs)
    }

    private fun sliceAudioChannelLogits(audioLogitsTensor: OnnxTensor, channelIndex: Int): FloatArray {
        val dims = audioLogitsTensor.info.shape
        val perChannel = dims.last().toInt()
        val allData = audioLogitsTensor.floatBuffer
        val start = channelIndex * perChannel
        val slice = FloatArray(perChannel)
        allData.position(start)
        allData.get(slice)
        return slice
    }

    // ═══════════════════════════════════════════════════════
    //  6. 音频帧生成 (自回归解码主循环)
    // ═══════════════════════════════════════════════════════

    /**
     * 生成音频帧序列。
     * @param requestRows 输入张量
     * @param isCancelled 取消检查回调
     * @param onFrameProgress 每帧进度回调 (帧数)
     * @return [numFrames, n_vq] 的音频 token 数组
     */
    @Synchronized
    fun generateAudioFrames(
        requestRows: RequestRows,
        isCancelled: () -> Boolean = { false },
        onFrameProgress: ((Int) -> Unit)? = null
    ): List<IntArray> {
        val gen = config.generationDefaults
        val nVq = config.ttsConfig.nVq
        val assistantSlotId = config.ttsConfig.audioAssistantSlotTokenId

        // Phase 1: Prefill
        val prefillResult = runPrefill(requestRows)
        var globalHidden = prefillResult.globalHidden
        var hiddenSize = prefillResult.hiddenSize
        var pastValidLength = prefillResult.pastValidLength
        var currentOutputs = prefillResult.outputs

        val generatedFrames = mutableListOf<IntArray>()
        val previousTokensByChannel = Array(nVq) { mutableListOf<Int>() }
        val previousTokenSetsByChannel = Array(nVq) { mutableSetOf<Int>() }

        Log.i(TAG, "开始帧生成: maxNewFrames=${gen.maxNewFrames}, nVq=$nVq")

        // Phase 2: Autoregressive frame generation
        for (stepIndex in 0 until gen.maxNewFrames) {
            if (isCancelled()) {
                Log.i(TAG, "帧生成被取消: step=$stepIndex")
                break
            }

            val frame = IntArray(nVq)

            if (localCachedPastInputNames.isNotEmpty()) {
                // ── Cached step decoding ──
                resetLocalCachedState()
                var localPastValidLength = 0

                // Step 0: sample assistant text token
                val textResult = runLocalCachedStep(
                    globalHidden, hiddenSize,
                    textTokenId = 0, audioTokenId = 0,
                    channelIndex = 0, stepType = 0,
                    pastValidLength = localPastValidLength
                )
                localPastValidLength++

                val nextTextToken = MossTtsSampling.sampleAssistantTextToken(
                    textLogits = textResult.textLogits,
                    assistantSlotTokenId = assistantSlotId,
                    audioEndTokenId = config.ttsConfig.audioEndTokenId,
                    doSample = gen.doSample,
                    textTemperature = gen.textTemperature,
                    textTopK = gen.textTopK,
                    textTopP = gen.textTopP
                )
                textResult.outputs.close()

                if (nextTextToken != assistantSlotId) {
                    Log.i(TAG, "生成结束: step=$stepIndex, token=$nextTextToken")
                    break
                }

                // Step 1: first audio channel
                var stepResult = runLocalCachedStep(
                    globalHidden, hiddenSize,
                    textTokenId = nextTextToken, audioTokenId = 0,
                    channelIndex = 0, stepType = 1,
                    pastValidLength = localPastValidLength
                )
                localPastValidLength++

                var audioLogits = sliceAudioChannelLogits(stepResult.audioLogits, 0)
                var sampledToken = MossTtsSampling.sampleAudioToken(
                    audioLogits = audioLogits,
                    previousTokenIds = previousTokensByChannel[0],
                    previousTokenSet = previousTokenSetsByChannel[0],
                    doSample = gen.doSample,
                    audioRepetitionPenalty = gen.audioRepetitionPenalty,
                    audioTemperature = gen.audioTemperature,
                    audioTopK = gen.audioTopK,
                    audioTopP = gen.audioTopP
                )
                frame[0] = sampledToken
                previousTokensByChannel[0].add(sampledToken)
                previousTokenSetsByChannel[0].add(sampledToken)
                var previousToken = sampledToken
                stepResult.outputs.close()

                // Step 2..n_vq-1: remaining audio channels
                for (ch in 1 until nVq) {
                    if (isCancelled()) break
                    stepResult = runLocalCachedStep(
                        globalHidden, hiddenSize,
                        textTokenId = 0, audioTokenId = previousToken,
                        channelIndex = ch - 1, stepType = 2,
                        pastValidLength = localPastValidLength
                    )
                    localPastValidLength++

                    audioLogits = sliceAudioChannelLogits(stepResult.audioLogits, ch)
                    sampledToken = MossTtsSampling.sampleAudioToken(
                        audioLogits = audioLogits,
                        previousTokenIds = previousTokensByChannel[ch],
                        previousTokenSet = previousTokenSetsByChannel[ch],
                        doSample = gen.doSample,
                        audioRepetitionPenalty = gen.audioRepetitionPenalty,
                        audioTemperature = gen.audioTemperature,
                        audioTopK = gen.audioTopK,
                        audioTopP = gen.audioTopP
                    )
                    frame[ch] = sampledToken
                    previousTokensByChannel[ch].add(sampledToken)
                    previousTokenSetsByChannel[ch].add(sampledToken)
                    previousToken = sampledToken
                    stepResult.outputs.close()
                }
            } else {
                Log.e(TAG, "localCachedStep 不可用，无法生成帧")
                break
            }

            generatedFrames.add(frame)
            onFrameProgress?.invoke(generatedFrames.size)

            // Phase 3: Decode step (update global hidden state)
            val decodeResult = runDecodeStep(frame, pastValidLength, currentOutputs)
            globalHidden = decodeResult.globalHidden
            hiddenSize = decodeResult.hiddenSize
            pastValidLength++
            currentOutputs = decodeResult.outputs

            if (generatedFrames.size % 50 == 0) {
                Log.d(TAG, "帧生成进度: ${generatedFrames.size}/${gen.maxNewFrames}")
            }
        }

        // Cleanup
        currentOutputs.close()
        decodePastFeeds.values.forEach { it.close() }
        decodePastFeeds.clear()
        localCachedPastFeeds.values.forEach { it.close() }
        localCachedPastFeeds.clear()

        Log.i(TAG, "帧生成完成: ${generatedFrames.size} frames")
        return generatedFrames
    }

    // ═══════════════════════════════════════════════════════
    //  7. 音频解码
    // ═══════════════════════════════════════════════════════

    /**
     * 将生成的音频帧解码为 PCM 波形。
     * @param frames [numFrames, nVq] 的音频 token 数组
     * @return 解码后的 Float32 PCM 数据 (channel-major 布局) + 有效长度
     */
    fun decodeAudio(frames: List<IntArray>): DecodedAudio {
        if (frames.isEmpty()) return DecodedAudio(FloatArray(0), 0)

        Log.i(TAG, "解码音频: ${frames.size} frames")

        val numFrames = frames.size
        val nVq = config.numQuantizers
        val codesData = IntArray(numFrames * nVq)
        var offset = 0
        for (frame in frames) {
            for (q in 0 until nVq) {
                codesData[offset++] = frame[q]
            }
        }

        val feeds = mapOf(
            "audio_codes" to OnnxTensor.createTensor(
                env, IntBuffer.wrap(codesData),
                longArrayOf(1, numFrames.toLong(), nVq.toLong())
            ),
            "audio_code_lengths" to OnnxTensor.createTensor(
                env, IntBuffer.wrap(intArrayOf(numFrames)), longArrayOf(1)
            )
        )

        val result = codecDecodeFullSession.run(feeds)
        val audioTensor = result.get("audio").get() as OnnxTensor
        val lengthsTensor = result.get("audio_lengths").get() as OnnxTensor

        val audioLength = lengthsTensor.intBuffer.get(0)
        val audioData = FloatArray(audioTensor.floatBuffer.remaining())
        audioTensor.floatBuffer.get(audioData)

        feeds.values.forEach { it.close() }
        result.close()

        Log.i(TAG, "音频解码完成: ${audioData.size} samples, validLength=$audioLength")
        return DecodedAudio(audioData, audioLength)
    }

    data class DecodedAudio(
        /** PCM 数据，channel-major 布局: [ch0_sample0..N, ch1_sample0..N, ...] */
        val data: FloatArray,
        /** 每个通道的有效采样点数 */
        val lengthPerChannel: Int
    )

    // ═══════════════════════════════════════════════════════
    //  8. 完整合成管线
    // ═══════════════════════════════════════════════════════

    /**
     * 完整的语音克隆合成。
     * @param text 要合成的文本
     * @param promptAudioCodes 参考音频的编码 codes
     * @param isCancelled 取消检查
     * @param onProgress 进度回调
     * @return 合成结果 (PCM waveform + sample rate)
     */
    @Synchronized
    fun synthesize(
        text: String,
        promptAudioCodes: Array<IntArray>,
        isCancelled: () -> Boolean = { false },
        onProgress: ((String) -> Unit)? = null
    ): SynthesisResult {
        onProgress?.invoke("文本规范化")
        val normalizedText = MossTtsTextNormalizer.normalize(text)
        if (normalizedText.isBlank()) {
            return SynthesisResult.Error("规范化后文本为空")
        }

        onProgress?.invoke("文本分词")
        val textTokenIds = tokenizer.encode(normalizedText)
        if (textTokenIds.isEmpty()) {
            return SynthesisResult.Error("分词结果为空")
        }
        Log.i(TAG, "文本分词完成: ${textTokenIds.size} tokens")

        // Split long text by token budget
        val chunks = if (textTokenIds.size <= VOICE_CLONE_MAX_TEXT_TOKENS) {
            listOf(textTokenIds)
        } else {
            MossTtsTextNormalizer.splitByApproxTokenBudget(normalizedText, VOICE_CLONE_MAX_TEXT_TOKENS)
                .map { tokenizer.encode(it) }
                .filter { it.isNotEmpty() }
        }

        Log.i(TAG, "文本拆分为 ${chunks.size} 段")

        val allFrames = mutableListOf<IntArray>()

        for (chunkIdx in chunks.indices) {
            if (isCancelled()) break

            onProgress?.invoke("构建输入 (${chunkIdx + 1}/${chunks.size})")
            val requestRows = buildVoiceCloneRequestRows(chunks[chunkIdx], promptAudioCodes)

            onProgress?.invoke("自回归解码 (${chunkIdx + 1}/${chunks.size})")
            val frames = generateAudioFrames(
                requestRows,
                isCancelled = isCancelled,
                onFrameProgress = { frameCount ->
                    if (frameCount % 100 == 0) {
                        onProgress?.invoke("帧 $frameCount (${chunkIdx + 1}/${chunks.size})")
                    }
                }
            )
            allFrames.addAll(frames)

            // Insert pause between chunks (except after last)
            if (chunkIdx < chunks.size - 1) {
                val pauseFrames = estimatePauseFrames()
                // Pad frames (silent)
                for (i in 0 until pauseFrames) {
                    allFrames.add(IntArray(config.numQuantizers) { config.ttsConfig.audioPadTokenId })
                }
            }
        }

        if (allFrames.isEmpty()) {
            return SynthesisResult.Error("未生成任何音频帧")
        }

        onProgress?.invoke("音频解码")
        val decoded = decodeAudio(allFrames)

        // Convert channel-major to interleaved for WAV output
        val pcm = channelMajorToInterleaved(decoded.data, decoded.lengthPerChannel, config.channels)

        onProgress?.invoke("合成完成")
        Log.i(TAG, "合成完成: ${pcm.size} samples, ${config.sampleRate}Hz, ${config.channels}ch")
        return SynthesisResult.Success(
            waveform = pcm,
            sampleRate = config.sampleRate,
            channels = config.channels
        )
    }

    private fun estimatePauseFrames(): Int {
        // ~0.3 seconds of silence between chunks
        val pauseSeconds = 0.3
        val framesPerSecond = config.sampleRate.toDouble() / config.downsampleRate
        return (pauseSeconds * framesPerSecond).toInt().coerceAtLeast(1)
    }

    private fun channelMajorToInterleaved(data: FloatArray, lengthPerChannel: Int, channels: Int): FloatArray {
        if (channels <= 1) return data.copyOf(lengthPerChannel)
        val result = FloatArray(lengthPerChannel * channels)
        for (sample in 0 until lengthPerChannel) {
            for (ch in 0 until channels) {
                val srcIdx = ch * lengthPerChannel + sample
                val dstIdx = sample * channels + ch
                result[dstIdx] = if (srcIdx < data.size) data[srcIdx] else 0f
            }
        }
        return result
    }

    sealed class SynthesisResult {
        data class Success(
            val waveform: FloatArray,
            val sampleRate: Int,
            val channels: Int
        ) : SynthesisResult()

        data class Error(val message: String) : SynthesisResult()
    }

    // ═══════════════════════════════════════════════════════
    //  资源释放
    // ═══════════════════════════════════════════════════════

    fun release() {
        Log.i(TAG, "释放 MossTtsNanoRuntime")
        decodePastFeeds.values.forEach { it.close() }
        decodePastFeeds.clear()
        localCachedPastFeeds.values.forEach { it.close() }
        localCachedPastFeeds.clear()
        runCatching<Unit> { prefillSession.close() }
        runCatching<Unit> { decodeStepSession.close() }
        runCatching<Unit> { localCachedStepSession.close() }
        // localFixedSampledFrameSession 未加载，无需关闭
        runCatching<Unit> { codecEncodeSession.close() }
        runCatching<Unit> { codecDecodeFullSession.close() }
    }
}

/**
 * SentencePiece 分词器接口。
 * 实现需要通过 JNI 调用 C++ sentencepiece 库，或使用纯 Java 实现。
 */
interface MossTtsTokenizer {
    fun encode(text: String): IntArray
    fun countTokens(text: String): Int = encode(text).size
    fun close()
}

/**
 * 占位分词器：按字符编码（用于测试/回退）。
 * 生产环境需要替换为真正的 SentencePiece 实现。
 */
class PlaceholderMossTokenizer : MossTtsTokenizer {
    override fun encode(text: String): IntArray {
        // 简单的字符级编码，每个 UTF-8 字符映射到其 code point
        // 这不是真正的 SentencePiece，只是占位实现
        return text.map { it.code }.toIntArray()
    }

    override fun close() {}
}
