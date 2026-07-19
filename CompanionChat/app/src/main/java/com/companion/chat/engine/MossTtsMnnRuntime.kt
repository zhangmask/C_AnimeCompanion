package com.companion.chat.engine

import android.content.Context
import android.util.Log
import com.companion.chat.data.voice.MossTtsNanoConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MossTtsMnnRuntime(
    private val modelDirectory: File,
    private val config: MossTtsNanoConfig,
    private val context: Context? = null
) {
    init {
        MossMnnJni.ensureLoaded()
        if (!MossMnnJni.isLoaded()) {
            Log.e(TAG, "MNN JNI native library not loaded — TTS will fail")
        } else {
            Log.i(TAG, "MNN JNI loaded successfully")
        }
    }

    companion object {
        private const val TAG = "MossTtsMnnRuntime"
        private const val VOICE_CLONE_MAX_TEXT_TOKENS = 75
        private const val NUM_THREADS = 4

        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        private var debugLogFile: File? = null
        @Synchronized
        private fun fileLog(msg: String) {
            try {
                val file = debugLogFile ?: return
                val ts = dateFormat.format(Date())
                PrintWriter(FileWriter(file, true)).use { it.println("[$ts] $msg") }
            } catch (_: Exception) {}
        }
        fun initDebugLog(context: Context) {
            try {
                val dir = File(context.filesDir, "tts_debug")
                if (!dir.exists()) dir.mkdirs()
                debugLogFile = File(dir, "tts_run.log")
                // Truncate at start of each app session to keep file small
                debugLogFile?.writeText("")
                fileLog("=== TTS debug log initialized ===")
            } catch (_: Exception) {}
        }
        fun logToFile(msg: String) = fileLog(msg)
        // 4线程全用大核(1×X4+3×A720), 暖机状态下最优(避免A520小核瓶颈+减少热降频)

        private fun stats(a: FloatArray, name: String): String {
            if (a.isEmpty()) return "$name: empty"
            var maxV = Float.NEGATIVE_INFINITY
            var minV = Float.POSITIVE_INFINITY
            var sum = 0.0
            for (v in a) { if (v > maxV) maxV = v; if (v < minV) minV = v; sum += v }
            return "$name size=${a.size} max=$maxV min=$minV mean=${sum/a.size}"
        }

        private fun hasNaN(a: FloatArray): Boolean {
            for (v in a) { if (v.isNaN() || v.isInfinite()) return true }
            return false
        }
    }

    private var prefillHandle = 0L
    private var decodeStepHandle = 0L
    private var localCachedStepHandle = 0L
    private var codecEncodeHandle = 0L
    private var codecDecodeFullHandle = 0L
    private var codecDecodeStepHandle = 0L
    private var verbose = true

    private val mnnPathMap = mapOf(
        // nocumsum prefill：移除了 CumSum(While loop) 子图，需外部传入 position_ids。
        // 原始 prefill (moss_tts_prefill.mnn) 含 CumSum 子图，Interpreter API 不支持导致 SIGSEGV。
        config.ttsPrefillModelPath to "tts/moss_tts_prefill_nocumsum.mnn",
        // 原始 decode_step 模型：内部含 Cos/Sin 节点计算 RoPE，CPU 后端完整支持。
        config.ttsDecodeStepModelPath to "tts/moss_tts_decode_step.mnn",
        config.ttsLocalCachedStepModelPath to "tts/moss_tts_local_cached_step.mnn",
        config.audioTokenizerEncodeModelPath to "audio_tokenizer/moss_audio_tokenizer_encode.mnn",
        config.audioTokenizerDecodeFullModelPath to "audio_tokenizer/moss_audio_tokenizer_decode_full.mnn",
        config.audioTokenizerDecodeStepModelPath to "audio_tokenizer/moss_audio_tokenizer_decode_step.mnn"
    )

    // Backend type: 7=Vulkan, 3=OpenCL, 0=CPU
    // OpenCL FP16 是天玑 9500 目标设备上的主推理后端；CPU 作为 fallback 保留。
    private val VULKAN_BACKEND = 7  // Vulkan (not used, NaN issues)
    private val OPENCL_BACKEND = 3  // OpenCL FP16 for all TTS/codec models
    private val CPU_BACKEND = 0     // CPU fallback

    private fun getOrCreateHandle(configPath: String, backendType: Int): Long {
        val mnnRelativePath = mnnPathMap[configPath] ?: configPath.replace(".onnx", ".mnn")
        val fullPath = File(modelDirectory, mnnRelativePath).absolutePath
        Log.i(TAG, "Loading MNN: $fullPath backend=$backendType")
        return MossMnnJni.nativeCreateSession2(fullPath, NUM_THREADS, backendType)
    }

    // precision: 1=High(FP32/INT8 CPUBackend), 2=Low(FP16 Arm82Backend)
    private fun getOrCreateHandleWithPrecision(configPath: String, backendType: Int, precision: Int): Long {
        val mnnRelativePath = mnnPathMap[configPath] ?: configPath.replace(".onnx", ".mnn")
        val fullPath = File(modelDirectory, mnnRelativePath).absolutePath
        Log.i(TAG, "Loading MNN: $fullPath backend=$backendType precision=$precision")
        return MossMnnJni.nativeCreateSession3(fullPath, NUM_THREADS, backendType, precision)
    }

    private fun getOrCreateHandleWithThreads(configPath: String, backendType: Int, precision: Int, threads: Int): Long {
        val mnnRelativePath = mnnPathMap[configPath] ?: configPath.replace(".onnx", ".mnn")
        val fullPath = File(modelDirectory, mnnRelativePath).absolutePath
        Log.i(TAG, "Loading MNN: $fullPath backend=$backendType precision=$precision threads=$threads")
        return MossMnnJni.nativeCreateSession3(fullPath, threads, backendType, precision)
    }

    // ============================================================
    // 全 CPU INT8 配置（Streamline 验证后的最优配置）
    //   OpenCL 在天玑 9500 上不适合 TTS（小矩阵+动态 KV cache）
    //   prefill: 882ms (CPU INT8) vs 7070ms (OpenCL FP16) → CPU 8x 更快
    //   帧生成: 145ms/帧 (CPU INT8) vs 827ms/帧 (OpenCL FP16) → CPU 5.7x 更快
    //   详见 performance_analysis.md
    // ============================================================
    private val PREFILL_BACKEND   = CPU_BACKEND  // INT8
    private val PREFILL_PRECISION = 1
    private val DECODE_STEP_BACKEND   = CPU_BACKEND  // INT8
    private val DECODE_STEP_PRECISION = 1
    private val LOCAL_CACHED_STEP_BACKEND   = CPU_BACKEND  // INT8 (145ms/帧)
    private val LOCAL_CACHED_STEP_PRECISION = 1
    private val CODEC_ENCODE_BACKEND   = CPU_BACKEND
    private val CODEC_ENCODE_PRECISION = 1
    private val CODEC_DECODE_BACKEND   = CPU_BACKEND
    private val CODEC_DECODE_PRECISION = 1

    fun preloadTtsModels() {
        val t0 = System.currentTimeMillis()
        if (prefillHandle == 0L) {
            prefillHandle = getOrCreateHandleWithPrecision(config.ttsPrefillModelPath, PREFILL_BACKEND, PREFILL_PRECISION)
            Log.i(TAG, "preloadTtsModels: prefill backend=$PREFILL_BACKEND precision=$PREFILL_PRECISION")
        }
        if (decodeStepHandle == 0L) {
            decodeStepHandle = getOrCreateHandleWithPrecision(config.ttsDecodeStepModelPath, DECODE_STEP_BACKEND, DECODE_STEP_PRECISION)
        }
        if (localCachedStepHandle == 0L) {
            localCachedStepHandle = getOrCreateHandleWithPrecision(config.ttsLocalCachedStepModelPath, LOCAL_CACHED_STEP_BACKEND, LOCAL_CACHED_STEP_PRECISION)
            Log.i(TAG, "preloadTtsModels: local_cached_step backend=$LOCAL_CACHED_STEP_BACKEND precision=$LOCAL_CACHED_STEP_PRECISION (隔离测试)")
        }
        Log.i(TAG, "preloadTtsModels done in ${System.currentTimeMillis()-t0}ms")
    }

    fun encodeReferenceAudio(waveform: FloatArray, waveformLength: Int): Array<IntArray> {
        val t0 = System.currentTimeMillis()
        Log.i(TAG, "MNN编码: len=$waveformLength")
        if (codecEncodeHandle == 0L) {
            codecEncodeHandle = getOrCreateHandleWithPrecision(config.audioTokenizerEncodeModelPath, CODEC_ENCODE_BACKEND, CODEC_ENCODE_PRECISION)
            Log.i(TAG, "MNN encode句柄 backend=$CODEC_ENCODE_BACKEND precision=$CODEC_ENCODE_PRECISION: $codecEncodeHandle")
        }
        val outputs = MossMnnJni.nativeRun(codecEncodeHandle,
            arrayOf("waveform", "input_lengths"), arrayOf(waveform, floatArrayOf(waveformLength.toFloat())),
            longArrayOf(1, config.channels.toLong(), waveformLength.toLong(), 1),
            intArrayOf(0, 3, 3, 1), arrayOf("audio_codes", "audio_code_lengths"))
        if (outputs == null || outputs.size < 2) { Log.e(TAG, "encode failed: outputs=${outputs?.size}"); return emptyArray() }
        val codeLength = outputs[1][0].toInt()
        Log.i(TAG, "encode outputs: codes=${outputs[0].size}, codeLength=$codeLength, codesDump=${outputs[0].take(10).joinToString()}")
        if (codeLength <= 0) { Log.e(TAG, "encode: 0 frames"); return emptyArray() }
        val codes = Array(codeLength) { f -> IntArray(config.numQuantizers) { q -> outputs[0][f * config.numQuantizers + q].toInt() } }
        Log.i(TAG, "MNN编码完成: ${codes.size}帧 × ${config.numQuantizers}, ${System.currentTimeMillis()-t0}ms")
        return codes
    }

    fun buildVoiceCloneRequestRows(textTokenIds: IntArray, promptAudioCodes: Array<IntArray>): MossTtsNanoRuntime.RequestRows {
        val rowWidth = config.ttsConfig.nVq + 1; val pad = config.ttsConfig.audioPadTokenId
        val rows = mutableListOf<IntArray>()
        for (id in config.promptTemplates.userPromptPrefixTokenIds + intArrayOf(config.ttsConfig.audioStartTokenId))
            rows.add(intArrayOf(id) + IntArray(rowWidth - 1) { pad })
        for (codeRow in promptAudioCodes) {
            val row = IntArray(rowWidth) { pad }; row[0] = config.ttsConfig.audioUserSlotTokenId
            for (q in 0 until minOf(codeRow.size, config.ttsConfig.nVq)) row[q + 1] = codeRow[q]
            rows.add(row)
        }
        for (id in intArrayOf(config.ttsConfig.audioEndTokenId) + config.promptTemplates.userPromptAfterReferenceTokenIds +
                textTokenIds + config.promptTemplates.assistantPromptPrefixTokenIds + intArrayOf(config.ttsConfig.audioStartTokenId))
            rows.add(intArrayOf(id) + IntArray(rowWidth - 1) { pad })
        val seqLen = rows.size; val inputIds = IntArray(seqLen * rowWidth); var off = 0
        for (row in rows) { System.arraycopy(row, 0, inputIds, off, rowWidth); off += rowWidth }
        return MossTtsNanoRuntime.RequestRows(inputIds, IntArray(seqLen) { 1 }, seqLen, rowWidth)
    }

    private fun runPrefillMnn(req: MossTtsNanoRuntime.RequestRows): PrefillResult {
        val t0 = System.currentTimeMillis()
        // nocumsum prefill：移除了 CumSum 子图，需外部传入 position_ids
        if (prefillHandle == 0L) {
            prefillHandle = getOrCreateHandleWithPrecision(config.ttsPrefillModelPath, PREFILL_BACKEND, PREFILL_PRECISION)
            Log.i(TAG, "prefill backend=$PREFILL_BACKEND precision=$PREFILL_PRECISION (nocumsum)")
        }
        if (decodeStepHandle == 0L) decodeStepHandle = getOrCreateHandleWithPrecision(config.ttsDecodeStepModelPath, DECODE_STEP_BACKEND, DECODE_STEP_PRECISION)
        val data = FloatArray(req.inputIds.size) { req.inputIds[it].toFloat() }
        val mask = FloatArray(req.attentionMask.size) { req.attentionMask[it].toFloat() }
        // position_ids: [0, 1, 2, ..., seqLen-1]，shape=[1, seqLen]
        val positionIds = FloatArray(req.seqLen) { it.toFloat() }
        val heads = config.ttsMetaOnnx.localHeads
        val headDim = config.ttsMetaOnnx.localHeadDim
        val outNames = mutableListOf("global_hidden")
        for (i in 0 until heads) { outNames.add("present_key_$i"); outNames.add("present_value_$i") }
        Log.i(TAG, "prefill input ids=${stats(data, "input_ids")} mask=${stats(mask, "attention_mask")} posIds=${stats(positionIds, "position_ids")} shape=[1,${req.seqLen},${req.rowWidth}]/[1,${req.seqLen}] heads=$heads headDim=$headDim backend=OpenCL")
        val out = MossMnnJni.nativeRun(prefillHandle,
            arrayOf("input_ids", "attention_mask", "position_ids"), arrayOf(data, mask, positionIds),
            longArrayOf(1, req.seqLen.toLong(), req.rowWidth.toLong(), 1, req.seqLen.toLong(), 1, req.seqLen.toLong()),
            intArrayOf(0, 3, 3, 2, 5, 2), outNames.toTypedArray())
            ?: error("prefill nativeRun returned null")
        val hs = heads * headDim
        val sl = out[0].size / hs
        require(sl > 0) { "prefill output seqLen=$sl invalid, global_hidden size=${out[0].size} hs=$hs" }
        val lh = out[0].copyOfRange((sl - 1) * hs, sl * hs)
        val kv = Array(heads * 2) { out[it + 1] }
        Log.i(TAG, "prefill output ${stats(out[0], "global_hidden")} seqLen=$sl hiddenSize=$hs kvShape=[1,$sl,$heads,$headDim]=${kv[0].size} ${System.currentTimeMillis()-t0}ms")
        // 逐层输出 K/V 统计
        for (i in 0 until heads) {
            Log.i(TAG, "prefill layer$i key=${stats(kv[i*2], "key_$i")} value=${stats(kv[i*2+1], "val_$i")}")
        }
        Log.i(TAG, "prefill lastHidden=${stats(lh, "last_hidden")}")
        // 检测 NaN 并标记具体层
        if (hasNaN(out[0])) {
            Log.e(TAG, "prefill NaN DETECTED in global_hidden!")
            for (i in 0 until heads) {
                if (hasNaN(kv[i*2]) || hasNaN(kv[i*2+1])) {
                    Log.e(TAG, "prefill NaN in layer$i K/V")
                }
            }
        }
        return PrefillResult(lh, hs, req.attentionMask.sum(), kv)
    }
    private data class PrefillResult(val h: FloatArray, val hs: Int, val pv: Int, val kv: Array<FloatArray>)
    private data class DecodeStepResult(val h: FloatArray, val kv: Array<FloatArray>)

    private var decodeStepCallCount = 0
    private fun runDecodeStepMnn(fr: IntArray, pv: Int, kv: Array<FloatArray>): DecodeStepResult {
        val t0 = System.currentTimeMillis()
        val rw = config.ttsConfig.nVq + 1
        val rd = FloatArray(rw) { config.ttsConfig.audioPadTokenId.toFloat() }
        rd[0] = config.ttsConfig.audioAssistantSlotTokenId.toFloat()
        for (i in fr.indices) rd[i + 1] = fr[i].toFloat()
        val ad = mutableListOf<Long>(); val of = mutableListOf<Int>()
        of.add(ad.size); ad.addAll(listOf(1L, 1L, rw.toLong())); of.add(3)
        of.add(ad.size); ad.add(1L); of.add(1)
        val nm = mutableListOf("input_ids", "past_valid_lengths")
        val ts = mutableListOf(rd, floatArrayOf(pv.toFloat()))
        val heads = config.ttsMetaOnnx.localHeads; val headDim = config.ttsMetaOnnx.localHeadDim
        val kvSeqLen = kv.firstOrNull()?.size?.div(heads * headDim)?.coerceAtLeast(1)?.toLong() ?: 1L
        for (i in 0 until heads) {
            nm.add("past_key_$i"); nm.add("past_value_$i")
            ts.add(kv[i * 2]); ts.add(kv[i * 2 + 1])
            of.add(ad.size); ad.addAll(listOf(1L, kvSeqLen, heads.toLong(), headDim.toLong())); of.add(4)
            of.add(ad.size); ad.addAll(listOf(1L, kvSeqLen, heads.toLong(), headDim.toLong())); of.add(4)
        }
        val on = mutableListOf("global_hidden")
        for (i in 0 until heads) { on.add("present_key_$i"); on.add("present_value_$i") }
        // Enable verbose for first 5 decode_step calls to log input types
        val isEarlyDecodeCall = decodeStepCallCount < 5
        if (isEarlyDecodeCall) { MossMnnJni.nativeSetVerbose(true) }
        if (verbose || isEarlyDecodeCall) Log.i(TAG, "decodeStep call=$decodeStepCallCount input frame=[${fr.joinToString()}] pv=$pv kvSeqLen=$kvSeqLen heads=$heads headDim=$headDim input_ids_vals=[${rd.joinToString()}]")
        val o = MossMnnJni.nativeRun(decodeStepHandle, nm.toTypedArray(), ts.toTypedArray(), ad.toLongArray(), of.toIntArray(), on.toTypedArray())
            ?: error("decodeStep nativeRun returned null")
        val hs = heads * headDim
        require(o[0].size >= hs) { "decodeStep global_hidden size=${o[0].size} < hs=$hs" }
        if (hasNaN(o[0])) {
            Log.e(TAG, "decodeStep NaN detected! global_hidden has NaN/Inf. Output stats: ${stats(o[0], "global_hidden")}")
        }
        val lh = o[0].copyOfRange(o[0].size - hs, o[0].size)
        val nkv = Array(heads * 2) { o[it + 1] }
        val newKvSeqLen = nkv[0].size / (heads * headDim)
        if (verbose || isEarlyDecodeCall) {
            Log.i(TAG, "decodeStep call=$decodeStepCallCount output ${stats(o[0], "global_hidden")} lastHidden=${stats(lh, "last_hidden")} newKvSeqLen=$newKvSeqLen hs=$hs ${System.currentTimeMillis()-t0}ms")
            // Log layer 0 KV stats for comparison
            Log.i(TAG, "decodeStep call=$decodeStepCallCount layer0 ${stats(nkv[0], "new_key_0")} ${stats(nkv[1], "new_val_0")}")
        }
        // Always fileLog decodeStep lastHidden stats for first 5 calls to compare with PC
        if (decodeStepCallCount < 5) {
            fileLog("decodeStep call=$decodeStepCallCount last_hidden ${stats(lh, "h")} frame=[${fr.joinToString()}] pv=$pv")
        }
        if (isEarlyDecodeCall) { MossMnnJni.nativeSetVerbose(false) }
        decodeStepCallCount++
        return DecodeStepResult(lh, nkv)
    }

    private data class LocalKvCache(val key: FloatArray, val value: FloatArray)

    private var localCachedStepCallCount = 0
    private fun runLocalCachedStepMnn(h: FloatArray, t: Int, a: Int, ch: Int, st: Int, pvl: Int, lkv: LocalKvCache?): LocalResult {
        if (localCachedStepHandle == 0L) {
            localCachedStepHandle = getOrCreateHandleWithPrecision(config.ttsLocalCachedStepModelPath, LOCAL_CACHED_STEP_BACKEND, LOCAL_CACHED_STEP_PRECISION)
            Log.i(TAG, "localCachedStep backend=$LOCAL_CACHED_STEP_BACKEND precision=$LOCAL_CACHED_STEP_PRECISION")
        }
        val isEarlyCall = localCachedStepCallCount < 5
        if (isEarlyCall) { verbose = true; MossMnnJni.nativeSetVerbose(true) }
        val ad = mutableListOf<Long>(); val of = mutableListOf<Int>()
        of.add(ad.size); ad.addAll(listOf(1L, h.size.toLong())); of.add(2)
        of.add(ad.size); ad.add(1L); of.add(1)
        of.add(ad.size); ad.add(1L); of.add(1)
        of.add(ad.size); ad.add(1L); of.add(1)
        of.add(ad.size); ad.add(1L); of.add(1)
        of.add(ad.size); ad.add(1L); of.add(1)
        val nm = mutableListOf("global_hidden","text_token_id","audio_token_id","channel_index","step_type","past_valid_lengths")
        val ts = mutableListOf(h, floatArrayOf(t.toFloat()), floatArrayOf(a.toFloat()), floatArrayOf(ch.toFloat()), floatArrayOf(st.toFloat()), floatArrayOf(pvl.toFloat()))

        // 动态 KV cache 长度（与 PC ONNX 一致），避免 padding 填零导致 attention 偏差。
        // ONNX 验证：固定 shape (1,75,...) padding 填零 vs 动态 shape (1,ksl,...)，
        // text_logits asid diff=1.82, audio_logits max diff=10.09。
        // 根因：rotary embedding 对 padding key(=0) 的旋转产生非零值，污染 attention scores，
        // 即使 past_valid_lengths mask 也无法完全消除偏差（mask 在 MatMul 之后应用，key 已被旋转）。
        val heads = config.ttsMetaOnnx.localHeads
        val headDim = config.ttsMetaOnnx.localHeadDim
        val unit = heads * headDim
        val actualKsl = if (lkv != null) lkv.key.size / unit else 0
        // 第一次调用（actualKsl=0）传空 tensor [1,0,12,64]，与 PC ONNX 完全一致。
        // 之前用 zeros[1,1,12,64] 代替空 tensor，导致模型把零填充位置当成有效历史
        // （模型用 tensor shape 的 Range(0,seqLen) 计算 attention positions，不看 past_valid_lengths），
        // text_logits max_diff=2.64，audio_logits max_diff=2.14，最终生成噪声。
        // MNN CPU backend 支持空 tensor（PC 验证 max_diff < 0.00001）。
        val pastKey = if (lkv != null && actualKsl > 0) lkv.key else FloatArray(0)
        val pastValue = if (lkv != null && actualKsl > 0) lkv.value else FloatArray(0)
        nm.add("local_past_key_0"); nm.add("local_past_value_0")
        ts.add(pastKey); ts.add(pastValue)
        of.add(ad.size); ad.addAll(listOf(1L, actualKsl.toLong(), heads.toLong(), headDim.toLong())); of.add(4)
        of.add(ad.size); ad.addAll(listOf(1L, actualKsl.toLong(), heads.toLong(), headDim.toLong())); of.add(4)

        // 根据 step_type 只请求需要的输出，减少 JNI 数据拷贝
        // step_type=0: 只需要 text_logits（audio_logits 262144 floats 不需要）
        // step_type=1/2: 只需要 audio_logits（text_logits 16384 floats 不需要）
        val needTextLogits = st == 0
        val needAudioLogits = st != 0
        val outNames = mutableListOf("local_present_key_0", "local_present_value_0")
        if (needTextLogits) outNames.add(0, "text_logits")
        if (needAudioLogits) outNames.add(if (needTextLogits) 1 else 0, "audio_logits")
        val o = MossMnnJni.nativeRun(localCachedStepHandle, nm.toTypedArray(), ts.toTypedArray(), ad.toLongArray(), of.toIntArray(),
            outNames.toTypedArray())!!

        // 解析输出：text_logits 和 audio_logits 的位置取决于是否请求了它们
        var idx = 0
        val tl = if (needTextLogits) o[idx++] else FloatArray(0)
        val al = if (needAudioLogits) o[idx++] else FloatArray(0)
        val outKeyRaw = o[idx]
        val outValueRaw = o[idx + 1]

        // 保留完整的 present_key/value（与 PC ONNX 一致），不截取。
        // 之前截取最后 unit 个元素会导致 KV cache 比 PC 少 1 position（全零 past key），
        // 即使 past_valid_lengths=1，模型仍使用 past_key 的所有 positions 计算 attention，
        // 导致 audio_logits 完全不同（argmax 857 vs PC 954），帧内容错误，最终生成噪声。
        val outKey = outKeyRaw
        val outValue = outValueRaw

        if (isEarlyCall) {
            fun stats(a: FloatArray, name: String) {
                if (a.isEmpty()) { Log.i(TAG, "localCached call=$localCachedStepCallCount st=$st $name: empty"); return }
                var maxV = Float.NEGATIVE_INFINITY; var minV = Float.POSITIVE_INFINITY; var sum = 0.0
                for (v in a) { if (v > maxV) maxV = v; if (v < minV) minV = v; sum += v }
                Log.i(TAG, "localCached call=$localCachedStepCallCount st=$st $name size=${a.size} max=$maxV min=$minV mean=${sum/a.size}")
            }
            stats(tl, "text_logits")
            stats(al, "audio_logits")
            stats(outKey, "present_key")
            stats(outValue, "present_value")
        }
        // Always log text_logits stats for st=0 (text step) to track asid_logit drop across all steps
        if (st == 0 && tl.isNotEmpty()) {
            var maxV = Float.NEGATIVE_INFINITY; var minV = Float.POSITIVE_INFINITY; var sum = 0.0
            for (v in tl) { if (v > maxV) maxV = v; if (v < minV) minV = v; sum += v }
            Log.i(TAG, "localCachedText call=$localCachedStepCallCount text_logits size=${tl.size} max=$maxV min=$minV mean=${sum/tl.size}")
            fileLog("localCachedText call=$localCachedStepCallCount text_logits size=${tl.size} max=$maxV min=$minV mean=${sum/tl.size}")
        }
        // NaN 检测
        if (hasNaN(tl) || hasNaN(al) || hasNaN(outKey) || hasNaN(outValue)) {
            Log.e(TAG, "localCachedStep NaN DETECTED! call=$localCachedStepCallCount st=$st ch=$ch pvl=$pvl tl_nan=${hasNaN(tl)} al_nan=${hasNaN(al)} key_nan=${hasNaN(outKey)} val_nan=${hasNaN(outValue)}")
        }
        localCachedStepCallCount++
        if (isEarlyCall) { verbose = false; MossMnnJni.nativeSetVerbose(false) }
        return LocalResult(tl, al, LocalKvCache(outKey, outValue))
    }
    private data class LocalResult(val tl: FloatArray, val al: FloatArray, val pkv: LocalKvCache)

    @Synchronized
    fun generateAudioFramesMnn(req: MossTtsNanoRuntime.RequestRows, ccl: () -> Boolean = { false }, op: ((Int)->Unit)? = null): List<IntArray> {
        val t0 = System.currentTimeMillis(); val g = config.generationDefaults; val nq = config.ttsConfig.nVq; val asid = config.ttsConfig.audioAssistantSlotTokenId
        fileLog(">>> generateAudioFramesMnn START maxFrames=${g.maxNewFrames} doSample=${g.doSample}")
        // 采样模式：贪心解码会导致帧重复（项目记忆：frame 9+ repetition），
        // 必须用采样模式 (temp=0.8, top_p=0.95, rep=1.2) 生成有效音频
        val doSample = g.doSample
        // Release and recreate localCachedStep + decodeStep sessions to prevent
        // MNN internal state leakage between TTS runs (first run correct, subsequent runs corrupted)
        try {
            if (localCachedStepHandle != 0L) {
                MossMnnJni.nativeReleaseSession(localCachedStepHandle)
                localCachedStepHandle = 0L
                fileLog("Released localCachedStepHandle (session state reset)")
            }
            if (decodeStepHandle != 0L) {
                MossMnnJni.nativeReleaseSession(decodeStepHandle)
                decodeStepHandle = 0L
                fileLog("Released decodeStepHandle (session state reset)")
            }
        } catch (e: Throwable) {
            fileLog("Session release error (ignored): ${e.message}")
            localCachedStepHandle = 0L
            decodeStepHandle = 0L
        }
        decodeStepCallCount = 0  // Reset for verbose logging on first 5 calls
        localCachedStepCallCount = 0
        val pf = runPrefillMnn(req)
        var h = pf.h; var hs = pf.hs; var pvl = pf.pv; var kv = pf.kv
        if (h.isNotEmpty()) {
            var maxV = Float.NEGATIVE_INFINITY; var minV = Float.POSITIVE_INFINITY; var sum = 0.0
            for (v in h) { if (v > maxV) maxV = v; if (v < minV) minV = v; sum += v }
            Log.i(TAG, "prefill hidden size=${h.size} max=$maxV min=$minV mean=${sum/h.size}")
            fileLog("prefill hidden size=${h.size} max=$maxV min=$minV mean=${sum/h.size}")
        }
        val maxFrames = g.maxNewFrames.coerceAtMost(75).coerceAtLeast(1)
        Log.i(TAG, "MNN帧生成(JNI一体化): max=$maxFrames")
        fileLog("MNN帧生成(JNI一体化): max=$maxFrames")
        // 确保 localCachedStep 和 decodeStep session 已创建
        if (localCachedStepHandle == 0L) {
            localCachedStepHandle = getOrCreateHandleWithPrecision(config.ttsLocalCachedStepModelPath, LOCAL_CACHED_STEP_BACKEND, LOCAL_CACHED_STEP_PRECISION)
        }
        if (decodeStepHandle == 0L) {
            decodeStepHandle = getOrCreateHandleWithPrecision(config.ttsDecodeStepModelPath, DECODE_STEP_BACKEND, DECODE_STEP_PRECISION)
        }
        // Quiet mode for tight loop
        verbose = false
        MossMnnJni.nativeSetVerbose(false)
        // 调用 JNI 层一体化帧生成：18 次 localCachedStep + 采样 + decodeStep 全在 C++ 完成
        val jniFrames = MossMnnJni.nativeGenerateAudioFrames(
            localCachedStepHandle,
            decodeStepHandle,
            h,
            kv,
            pvl,
            maxFrames,
            nq,
            asid,
            config.ttsConfig.audioEndTokenId,
            config.ttsConfig.audioPadTokenId,
            config.ttsConfig.audioAssistantSlotTokenId,
            config.ttsMetaOnnx.localHeads,
            config.ttsMetaOnnx.localHeadDim,
            config.ttsMetaOnnx.globalHeads,
            config.ttsMetaOnnx.globalHeadDim,
            doSample,
            g.textTemperature,
            g.textTopK,
            g.textTopP,
            g.audioTemperature,
            g.audioTopK,
            g.audioTopP,
            g.audioRepetitionPenalty
        )
        verbose = true
        MossMnnJni.nativeSetVerbose(true)
        val frames = (jniFrames ?: arrayOf<IntArray>()).toMutableList()
        op?.invoke(frames.size)
        Log.i(TAG, "帧生成(JNI一体化): ${frames.size}帧, ${System.currentTimeMillis()-t0}ms")
        fileLog("<<< generateAudioFramesMnn(JNI一体化) DONE frames=${frames.size} time=${System.currentTimeMillis()-t0}ms")
        if (localCachedStepHandle != 0L) MossMnnJni.nativeLogTiming(localCachedStepHandle, "localCachedStep")
        if (decodeStepHandle != 0L) MossMnnJni.nativeLogTiming(decodeStepHandle, "decodeStep")
        return frames
    }

    fun decodeAudioMnn(frames: List<IntArray>): MossTtsNanoRuntime.DecodedAudio {
        val t0 = System.currentTimeMillis()
        if (frames.isEmpty()) return MossTtsNanoRuntime.DecodedAudio(FloatArray(0), 0)
        Log.i(TAG, "MNN解码: ${frames.size}帧")

        if (codecDecodeStepHandle == 0L) {
            val stepPath = File(modelDirectory, config.audioTokenizerDecodeStepModelPath.replace(".onnx", ".mnn")).absolutePath
            Log.i(TAG, "MNN Express加载codec_decode_step: $stepPath")
            // decode_step 有 subgraphs，必须指定所有输入/输出名称才能加载
            val stepInputNames = mutableListOf("audio_codes", "audio_code_lengths")
            val stepOutputNames = mutableListOf("audio", "audio_lengths")
            for (spec in config.codecMetaStreaming.transformerOffsets) {
                stepInputNames.add(spec.inputName)
                stepOutputNames.add(spec.outputName)
            }
            for (spec in config.codecMetaStreaming.attentionCaches) {
                stepInputNames.add(spec.offsetInputName)
                stepInputNames.add(spec.cachedKeysInputName)
                stepInputNames.add(spec.cachedValuesInputName)
                stepInputNames.add(spec.cachedPositionsInputName)
                stepOutputNames.add(spec.offsetOutputName)
                stepOutputNames.add(spec.cachedKeysOutputName)
                stepOutputNames.add(spec.cachedValuesOutputName)
                stepOutputNames.add(spec.cachedPositionsOutputName)
            }
            codecDecodeStepHandle = MossMnnJni.nativeCreateSessionExpress3(
                stepPath,
                stepInputNames.toTypedArray(),
                stepOutputNames.toTypedArray(),
                CODEC_DECODE_BACKEND,
                true, // dynamic model
                CODEC_DECODE_PRECISION
            )
            Log.i(TAG, "MNN codec decode_step句柄: $codecDecodeStepHandle backend=$CODEC_DECODE_BACKEND precision=$CODEC_DECODE_PRECISION")
            if (codecDecodeStepHandle == 0L) {
                Log.w(TAG, "decode_step加载失败，回退到decode_full")
            }
        }

        if (codecDecodeStepHandle == 0L) {
            return decodeAudioFullMnn(frames)
        }

        val nVq = config.numQuantizers
        val channels = config.channels
        require(nVq > 0 && channels > 0) { "numQuantizers=$nVq channels=$channels invalid" }

        val transformerSpecs = config.codecMetaStreaming.transformerOffsets
        val attentionSpecs = config.codecMetaStreaming.attentionCaches
        require(transformerSpecs.isNotEmpty() && attentionSpecs.isNotEmpty()) {
            "codecMetaStreaming empty: transformer=${transformerSpecs.size} attention=${attentionSpecs.size}"
        }

        for ((i, fr) in frames.withIndex()) {
            require(fr.size == nVq) { "frame $i size=${fr.size} != nVq=$nVq" }
        }

        // Streaming states: offsets and attention caches.
        // attnPositions 必须初始化为 -1（表示未使用/padding），全零会导致模型把 position 0 当成有效历史，
        // 用全零 KV cache 计算 attention，使 frame 0 输出突变波形（开头"咔哒"噪声）。
        // PC ONNX 验证：positions=-1 时流式 decode_step 与 decode_full 输出完全一致（diff max=1e-6）。
        val transformerOffsets = Array(transformerSpecs.size) { IntArray(1) { 0 } }
        val attnOffsets = Array(attentionSpecs.size) { IntArray(1) { 0 } }
        val attnKeys = Array(attentionSpecs.size) { i -> FloatArray(attentionSpecs[i].cacheShape.reduce { a: Int, b: Int -> a * b }) { 0f } }
        val attnValues = Array(attentionSpecs.size) { i -> FloatArray(attentionSpecs[i].cacheShape.reduce { a: Int, b: Int -> a * b }) { 0f } }
        val attnPositions = Array(attentionSpecs.size) { i -> IntArray(attentionSpecs[i].positionsShape.reduce { a: Int, b: Int -> a * b }) { -1 } }

        val chBuffers = Array(channels) { mutableListOf<Float>() }

        for ((frameIdx, frame) in frames.withIndex()) {
            val inputNames = mutableListOf<String>()
            val inputTensors = mutableListOf<FloatArray>()
            val inputDimList = mutableListOf<List<Long>>()
            val inputTypes = mutableListOf<Int>()

            // audio_codes [1, 1, nVq] int32
            inputNames.add("audio_codes")
            inputTensors.add(FloatArray(nVq) { frame[it].toFloat() })
            inputDimList.add(listOf(1L, 1L, nVq.toLong()))
            inputTypes.add(1)

            // audio_code_lengths [1] int32
            inputNames.add("audio_code_lengths")
            inputTensors.add(floatArrayOf(1f))
            inputDimList.add(listOf(1L))
            inputTypes.add(1)

            // transformer offsets [1] int32
            for ((i, spec) in transformerSpecs.withIndex()) {
                inputNames.add(spec.inputName)
                inputTensors.add(FloatArray(1) { transformerOffsets[i][0].toFloat() })
                inputDimList.add(spec.shape.map { it.toLong() })
                inputTypes.add(1)
            }

            // attention caches
            for ((i, spec) in attentionSpecs.withIndex()) {
                inputNames.add(spec.offsetInputName)
                inputTensors.add(FloatArray(1) { attnOffsets[i][0].toFloat() })
                inputDimList.add(spec.offsetShape.map { it.toLong() })
                inputTypes.add(1)

                inputNames.add(spec.cachedKeysInputName)
                inputTensors.add(attnKeys[i])
                inputDimList.add(spec.cacheShape.map { it.toLong() })
                inputTypes.add(0)

                inputNames.add(spec.cachedValuesInputName)
                inputTensors.add(attnValues[i])
                inputDimList.add(spec.cacheShape.map { it.toLong() })
                inputTypes.add(0)

                inputNames.add(spec.cachedPositionsInputName)
                inputTensors.add(FloatArray(attnPositions[i].size) { attnPositions[i][it].toFloat() })
                inputDimList.add(spec.positionsShape.map { it.toLong() })
                inputTypes.add(1)
            }

            // Output names in model order (nativeRunExpressTyped returns outputs by index).
            val outputNames = mutableListOf("audio", "audio_lengths")
            for (spec in transformerSpecs) outputNames.add(spec.outputName)
            for (spec in attentionSpecs) {
                outputNames.add(spec.offsetOutputName)
                outputNames.add(spec.cachedKeysOutputName)
                outputNames.add(spec.cachedValuesOutputName)
                outputNames.add(spec.cachedPositionsOutputName)
            }

            val inputDims = inputDimList.flatten().toLongArray()
            val dimOffsets = mutableListOf<Int>()
            var dimIdx = 0
            for (dims in inputDimList) {
                dimOffsets.add(dimIdx)
                dimOffsets.add(dims.size)
                dimIdx += dims.size
            }

            val o = MossMnnJni.nativeRunExpressTyped(
                codecDecodeStepHandle,
                inputNames.toTypedArray(),
                inputTensors.toTypedArray(),
                inputDims,
                dimOffsets.toIntArray(),
                inputTypes.toIntArray(),
                outputNames.toTypedArray()
            )
            require(o != null) { "MNN codec decode_step failed at frame $frameIdx" }

            val expectedOutputs = 2 + transformerSpecs.size + attentionSpecs.size * 4
            require(o.size == expectedOutputs) {
                "decode_step output count mismatch: got=${o.size} expected=$expectedOutputs"
            }

            // audio output per frame: channel-major [ch0_chunk..., ch1_chunk...]
            val audioData = o[0]
            val audioLengthPerChannel = if (o.size > 1 && o[1].isNotEmpty()) o[1][0].toInt() else audioData.size / channels
            val validFloats = audioLengthPerChannel * channels
            if (validFloats > 0 && validFloats <= audioData.size) {
                for (ch in 0 until channels) {
                    val base = ch * audioLengthPerChannel
                    for (i in 0 until audioLengthPerChannel) chBuffers[ch].add(audioData[base + i])
                }
            }
            if (frameIdx == 0) {
                Log.i(TAG, "decode_step frame=0 first8=[${audioData.take(8).joinToString()}] lenPerCh=$audioLengthPerChannel ch=$channels")
            }
            if (frameIdx == 0 || frameIdx == frames.size - 1) {
                Log.d(TAG, "decode_step frame=$frameIdx audioSize=${audioData.size} lenPerCh=$audioLengthPerChannel validFloats=$validFloats")
            }

            // Update streaming states from outputs.
            var outIdx = 2
            for (i in transformerSpecs.indices) {
                transformerOffsets[i][0] = o[outIdx++][0].toInt()
            }
            for (i in attentionSpecs.indices) {
                attnOffsets[i][0] = o[outIdx++][0].toInt()
                attnKeys[i] = o[outIdx++]
                attnValues[i] = o[outIdx++]
                val posOut = o[outIdx++]
                attnPositions[i] = IntArray(posOut.size) { posOut[it].toInt() }
            }

            if (frameIdx % 50 == 0) Log.d(TAG, "流式解码进度: $frameIdx/${frames.size}")
        }

        val lengthPerChannel = chBuffers.firstOrNull()?.size ?: 0
        val result = if (lengthPerChannel > 0) FloatArray(lengthPerChannel * channels) else FloatArray(0)
        for (ch in 0 until channels) {
            for (i in 0 until lengthPerChannel) {
                result[ch * lengthPerChannel + i] = chBuffers[ch][i]
            }
        }
        if (result.isNotEmpty()) {
            var maxV = Float.NEGATIVE_INFINITY; var minV = Float.POSITIVE_INFINITY; var sum = 0.0
            for (v in result) { if (v > maxV) maxV = v; if (v < minV) minV = v; sum += v }
            val rms = kotlin.math.sqrt((result.map { it * it }.sum() / result.size).toFloat())
            Log.i(TAG, "MNN流式解码完成: ${result.size} floats, lengthPerChannel=$lengthPerChannel, max=$maxV min=$minV mean=${sum/result.size} rms=$rms, ${System.currentTimeMillis()-t0}ms")
        } else {
            Log.i(TAG, "MNN流式解码完成: ${result.size} floats, lengthPerChannel=$lengthPerChannel, ${System.currentTimeMillis()-t0}ms")
        }
        return MossTtsNanoRuntime.DecodedAudio(result, lengthPerChannel)
    }

    private fun decodeAudioFullMnn(frames: List<IntArray>): MossTtsNanoRuntime.DecodedAudio {
        val t0 = System.currentTimeMillis()
        if (codecDecodeFullHandle == 0L) {
            val fullPath = File(modelDirectory, config.audioTokenizerDecodeFullModelPath.replace(".onnx", ".mnn")).absolutePath
            Log.i(TAG, "MNN Express加载codec_decode_full: $fullPath")
            codecDecodeFullHandle = MossMnnJni.nativeCreateSessionExpress3(
                fullPath,
                arrayOf("audio_codes", "audio_code_lengths"),
                arrayOf("audio", "audio_lengths"),
                CODEC_DECODE_BACKEND,
                true, // dynamic model
                CODEC_DECODE_PRECISION
            )
            Log.i(TAG, "MNN codec decode_full句柄: $codecDecodeFullHandle backend=$CODEC_DECODE_BACKEND precision=$CODEC_DECODE_PRECISION")
            if (codecDecodeFullHandle == 0L) error("MNN codec decode_full create session failed")
        }

        val nVq = config.numQuantizers
        val channels = config.channels
        val numFrames = frames.size
        val codesData = FloatArray(numFrames * nVq)
        var idx = 0
        for (frame in frames) {
            for (q in 0 until nVq) codesData[idx++] = frame[q].toFloat()
        }

        val o = MossMnnJni.nativeRunExpressTyped(
            codecDecodeFullHandle,
            arrayOf("audio_codes", "audio_code_lengths"),
            arrayOf(codesData, floatArrayOf(numFrames.toFloat())),
            longArrayOf(1, numFrames.toLong(), nVq.toLong(), 1),
            intArrayOf(0, 3, 3, 1),
            intArrayOf(1, 1),
            arrayOf("audio", "audio_lengths")
        ) ?: error("MNN codec decode_full inference failed")
        require(o.size >= 2) { "decode_full output count=${o.size}" }

        val audioData = o[0]
        val audioLength = if (o[1].isNotEmpty()) o[1][0].toInt() else audioData.size / channels
        val validFloats = audioLength * channels
        val result = if (validFloats > 0 && validFloats <= audioData.size) audioData.copyOf(validFloats) else audioData
        Log.i(TAG, "MNN decode_full完成: audioSize=${audioData.size} lenPerCh=$audioLength valid=$validFloats, ${System.currentTimeMillis()-t0}ms")
        return MossTtsNanoRuntime.DecodedAudio(result, audioLength)
    }

    fun release() {
        Log.i(TAG, "释放 MNN")
        fun r(h: Long) { if (h != 0L) MossMnnJni.nativeReleaseSession(h) }
        r(prefillHandle); r(decodeStepHandle); r(localCachedStepHandle); r(codecEncodeHandle)
        if (codecDecodeFullHandle != 0L) MossMnnJni.nativeReleaseSessionExpress(codecDecodeFullHandle)
        if (codecDecodeStepHandle != 0L) MossMnnJni.nativeReleaseSessionExpress(codecDecodeStepHandle)
    }
}
