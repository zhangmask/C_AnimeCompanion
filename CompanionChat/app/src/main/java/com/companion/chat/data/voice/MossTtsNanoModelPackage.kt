package com.companion.chat.data.voice

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

object MossTtsNanoModelPackage {
    const val DEFAULT_MODEL_RELATIVE_DIRECTORY = "models/tts/moss-tts-nano"
    const val TTS_META_FILE_NAME = "tts/tts_browser_onnx_meta.json"
    const val CODEC_META_FILE_NAME = "audio_tokenizer/codec_browser_onnx_meta.json"
    const val MANIFEST_FILE_NAME = "tts/browser_poc_manifest.json"

    val REQUIRED_MODEL_FILES = listOf(
        TTS_META_FILE_NAME,
        CODEC_META_FILE_NAME,
        MANIFEST_FILE_NAME,
        "tts/tokenizer.model",
        "tts/moss_tts_prefill.onnx",
        "tts/moss_tts_decode_step.onnx",
        "tts/moss_tts_local_decoder.onnx",
        "tts/moss_tts_local_cached_step.onnx",
        "tts/moss_tts_local_fixed_sampled_frame.onnx",
        "tts/moss_tts_global_shared.data",
        "tts/moss_tts_local_shared.data",
        "audio_tokenizer/moss_audio_tokenizer_encode.onnx",
        "audio_tokenizer/moss_audio_tokenizer_encode.data",
        "audio_tokenizer/moss_audio_tokenizer_decode_full.onnx",
        "audio_tokenizer/moss_audio_tokenizer_decode_step.onnx",
        "audio_tokenizer/moss_audio_tokenizer_decode_shared.data"
    )

    fun inspect(modelDirectory: String): MossTtsNanoModelStatus {
        val directoryPath = modelDirectory.trim()
        if (directoryPath.isBlank()) return MossTtsNanoModelStatus.DirectoryNotConfigured

        val directory = File(directoryPath)
        if (!directory.isDirectory) {
            return MossTtsNanoModelStatus.MissingFiles(REQUIRED_MODEL_FILES)
        }

        val missingFiles = REQUIRED_MODEL_FILES.filterNot { File(directory, it).isFile }
        if (missingFiles.isNotEmpty()) {
            return MossTtsNanoModelStatus.MissingFiles(missingFiles)
        }

        return runCatching {
            MossTtsNanoConfig.fromDirectory(directory)
        }.fold(
            onSuccess = { MossTtsNanoModelStatus.Ready },
            onFailure = { MossTtsNanoModelStatus.InvalidConfig(it.message ?: "配置 JSON 解析失败") }
        )
    }
}

data class MossTtsNanoConfig(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val channels: Int = 2,
    val numQuantizers: Int = 8,
    val downsampleRate: Int = 320,
    val ttsPrefillModelPath: String = "tts/moss_tts_prefill.onnx",
    val ttsDecodeStepModelPath: String = "tts/moss_tts_decode_step.onnx",
    val ttsLocalDecoderModelPath: String = "tts/moss_tts_local_decoder.onnx",
    val ttsLocalCachedStepModelPath: String = "tts/moss_tts_local_cached_step.onnx",
    val ttsLocalFixedSampledFrameModelPath: String = "tts/moss_tts_local_fixed_sampled_frame.onnx",
    val ttsLocalGreedyFrameModelPath: String? = null,
    val audioTokenizerEncodeModelPath: String = "audio_tokenizer/moss_audio_tokenizer_encode.onnx",
    val audioTokenizerDecodeFullModelPath: String = "audio_tokenizer/moss_audio_tokenizer_decode_full.onnx",
    val audioTokenizerDecodeStepModelPath: String = "audio_tokenizer/moss_audio_tokenizer_decode_step.onnx",
    val tokenizerModelPath: String = "tts/tokenizer.model",
    val ttsConfig: TtsConfig = TtsConfig(),
    val generationDefaults: GenerationDefaults = GenerationDefaults(),
    val promptTemplates: PromptTemplates = PromptTemplates(),
    val ttsMetaOnnx: TtsMetaOnnx = TtsMetaOnnx(),
    val codecMetaStreaming: CodecStreamingConfig = CodecStreamingConfig()
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000

        fun fromDirectory(directory: File): MossTtsNanoConfig {
            val ttsMeta = JSONObject(File(directory, MossTtsNanoModelPackage.TTS_META_FILE_NAME).readText())
            val codecMeta = JSONObject(File(directory, MossTtsNanoModelPackage.CODEC_META_FILE_NAME).readText())
            val manifest = JSONObject(File(directory, MossTtsNanoModelPackage.MANIFEST_FILE_NAME).readText())

            val codecConfig = codecMeta.getJSONObject("codec_config")
            val sampleRate = codecConfig.optInt("sample_rate", DEFAULT_SAMPLE_RATE)
            val channels = codecConfig.optInt("channels", 2)
            val numQuantizers = codecConfig.optInt("num_quantizers", 8)
            val downsampleRate = codecConfig.optInt("downsample_rate", 320)
            require(sampleRate in 8_000..96_000) { "sample_rate 必须在 8000 到 96000 之间" }
            require(channels in 1..2) { "channels 只支持 1 或 2" }

            val ttsFiles = ttsMeta.getJSONObject("files")
            val codecFiles = codecMeta.getJSONObject("files")

            // Parse TTS config from manifest
            val ttsConfigJson = manifest.getJSONObject("tts_config")
            val ttsConfig = TtsConfig(
                nVq = ttsConfigJson.optInt("n_vq", numQuantizers),
                audioPadTokenId = ttsConfigJson.optInt("audio_pad_token_id"),
                audioStartTokenId = ttsConfigJson.optInt("audio_start_token_id"),
                audioEndTokenId = ttsConfigJson.optInt("audio_end_token_id"),
                audioUserSlotTokenId = ttsConfigJson.optInt("audio_user_slot_token_id"),
                audioAssistantSlotTokenId = ttsConfigJson.optInt("audio_assistant_slot_token_id")
            )

            // Parse generation defaults
            val genJson = manifest.optJSONObject("generation_defaults") ?: JSONObject()
            val generationDefaults = GenerationDefaults(
                maxNewFrames = genJson.optInt("max_new_frames", 75),
                doSample = genJson.optBoolean("do_sample", true),
                sampleMode = genJson.optString("sample_mode", "fixed"),
                audioRepetitionPenalty = genJson.optDouble("audio_repetition_penalty", 1.2).toFloat(),
                audioTemperature = genJson.optDouble("audio_temperature", 0.8).toFloat(),
                audioTopK = genJson.optInt("audio_top_k", 25),
                audioTopP = genJson.optDouble("audio_top_p", 0.95).toFloat(),
                textTemperature = genJson.optDouble("text_temperature", 1.0).toFloat(),
                textTopK = genJson.optInt("text_top_k", 50),
                textTopP = genJson.optDouble("text_top_p", 1.0).toFloat()
            )

            // Parse prompt templates
            val promptJson = manifest.optJSONObject("prompt_templates") ?: JSONObject()
            val promptTemplates = PromptTemplates(
                userPromptPrefixTokenIds = parseIntArray(promptJson.optJSONArray("user_prompt_prefix_token_ids")),
                userPromptAfterReferenceTokenIds = parseIntArray(promptJson.optJSONArray("user_prompt_after_reference_token_ids")),
                assistantPromptPrefixTokenIds = parseIntArray(promptJson.optJSONArray("assistant_prompt_prefix_token_ids"))
            )

            // Parse ONNX input/output names for KV cache management
            val ttsOnnxJson = ttsMeta.optJSONObject("onnx") ?: JSONObject()
            val modelConfigJson = ttsMeta.optJSONObject("model_config") ?: JSONObject()
            val ttsMetaOnnx = TtsMetaOnnx(
                decodeInputNames = parseStringList(ttsOnnxJson.optJSONArray("decode_input_names")),
                decodeOutputNames = parseStringList(ttsOnnxJson.optJSONArray("decode_output_names")),
                localCachedInputNames = parseStringList(ttsOnnxJson.optJSONArray("local_cached_input_names")),
                localCachedOutputNames = parseStringList(ttsOnnxJson.optJSONArray("local_cached_output_names")),
                localHeads = modelConfigJson.optInt("local_heads", 12),
                localHeadDim = modelConfigJson.optInt("local_head_dim", 64),
                globalHeads = modelConfigJson.optInt("global_heads", 12),
                globalHeadDim = modelConfigJson.optInt("head_dim", 64)
            )

            // Parse codec streaming decode config
            val streamingJson = codecMeta.optJSONObject("streaming_decode") ?: JSONObject()
            val codecStreaming = CodecStreamingConfig(
                transformerOffsets = parseTransformerSpecs(streamingJson.optJSONArray("transformer_offsets")),
                attentionCaches = parseAttentionSpecs(streamingJson.optJSONArray("attention_caches"))
            )

            return MossTtsNanoConfig(
                sampleRate = sampleRate,
                channels = channels,
                numQuantizers = numQuantizers,
                downsampleRate = downsampleRate,
                ttsPrefillModelPath = "tts/${ttsFiles.getString("prefill")}",
                ttsDecodeStepModelPath = "tts/${ttsFiles.getString("decode_step")}",
                ttsLocalDecoderModelPath = "tts/${ttsFiles.getString("local_decoder")}",
                ttsLocalCachedStepModelPath = "tts/${ttsFiles.getString("local_cached_step")}",
                ttsLocalFixedSampledFrameModelPath = "tts/${ttsFiles.getString("local_fixed_sampled_frame")}",
                ttsLocalGreedyFrameModelPath = ttsFiles.optString("local_greedy_frame", null)?.let { "tts/$it" },
                audioTokenizerEncodeModelPath = "audio_tokenizer/${codecFiles.getString("encode")}",
                audioTokenizerDecodeFullModelPath = "audio_tokenizer/${codecFiles.getString("decode_full")}",
                audioTokenizerDecodeStepModelPath = "audio_tokenizer/${codecFiles.getString("decode_step")}",
                ttsConfig = ttsConfig,
                generationDefaults = generationDefaults,
                promptTemplates = promptTemplates,
                ttsMetaOnnx = ttsMetaOnnx,
                codecMetaStreaming = codecStreaming
            )
        }

        private fun parseIntArray(json: JSONArray?): IntArray {
            if (json == null) return intArrayOf()
            return IntArray(json.length()) { json.getInt(it) }
        }

        private fun parseStringList(json: JSONArray?): List<String> {
            if (json == null) return emptyList()
            return (0 until json.length()).map { json.getString(it) }
        }

        private fun parseTransformerSpecs(json: JSONArray?): List<TransformerSpec> {
            if (json == null) return emptyList()
            return (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                TransformerSpec(
                    inputName = obj.getString("input_name"),
                    outputName = obj.getString("output_name"),
                    shape = parseIntArray(obj.getJSONArray("shape"))
                )
            }
        }

        private fun parseAttentionSpecs(json: JSONArray?): List<AttentionSpec> {
            if (json == null) return emptyList()
            return (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                AttentionSpec(
                    offsetInputName = obj.getString("offset_input_name"),
                    offsetOutputName = obj.getString("offset_output_name"),
                    offsetShape = parseIntArray(obj.getJSONArray("offset_shape")),
                    cachedKeysInputName = obj.getString("cached_keys_input_name"),
                    cachedKeysOutputName = obj.getString("cached_keys_output_name"),
                    cachedValuesInputName = obj.getString("cached_values_input_name"),
                    cachedValuesOutputName = obj.getString("cached_values_output_name"),
                    cachedPositionsInputName = obj.getString("cached_positions_input_name"),
                    cachedPositionsOutputName = obj.getString("cached_positions_output_name"),
                    cacheShape = parseIntArray(obj.getJSONArray("cache_shape")),
                    positionsShape = parseIntArray(obj.getJSONArray("positions_shape"))
                )
            }
        }
    }
}

/** TTS 特殊 token ID 配置。 */
data class TtsConfig(
    val nVq: Int = 8,
    val audioPadTokenId: Int = 0,
    val audioStartTokenId: Int = 1,
    val audioEndTokenId: Int = 2,
    val audioUserSlotTokenId: Int = 3,
    val audioAssistantSlotTokenId: Int = 4
)

/** 生成参数默认值。 */
data class GenerationDefaults(
    val maxNewFrames: Int = 75,
    val doSample: Boolean = true,
    val sampleMode: String = "fixed",
    val audioRepetitionPenalty: Float = 1.2f,
    val audioTemperature: Float = 0.8f,
    val audioTopK: Int = 25,
    val audioTopP: Float = 0.95f,
    val textTemperature: Float = 1.0f,
    val textTopK: Int = 50,
    val textTopP: Float = 1.0f
)

/** Prompt 模板 token ID 序列。 */
data class PromptTemplates(
    val userPromptPrefixTokenIds: IntArray = intArrayOf(),
    val userPromptAfterReferenceTokenIds: IntArray = intArrayOf(),
    val assistantPromptPrefixTokenIds: IntArray = intArrayOf()
)

/** TTS ONNX 模型的 KV cache 输入/输出名称与维度信息。 */
data class TtsMetaOnnx(
    val decodeInputNames: List<String> = emptyList(),
    val decodeOutputNames: List<String> = emptyList(),
    val localCachedInputNames: List<String> = emptyList(),
    val localCachedOutputNames: List<String> = emptyList(),
    val localHeads: Int = 12,
    val localHeadDim: Int = 64,
    val globalHeads: Int = 12,
    val globalHeadDim: Int = 64
)

/** Codec streaming decode 配置。 */
data class CodecStreamingConfig(
    val transformerOffsets: List<TransformerSpec> = emptyList(),
    val attentionCaches: List<AttentionSpec> = emptyList()
)

data class TransformerSpec(
    val inputName: String,
    val outputName: String,
    val shape: IntArray
)

data class AttentionSpec(
    val offsetInputName: String,
    val offsetOutputName: String,
    val offsetShape: IntArray,
    val cachedKeysInputName: String,
    val cachedKeysOutputName: String,
    val cachedValuesInputName: String,
    val cachedValuesOutputName: String,
    val cachedPositionsInputName: String,
    val cachedPositionsOutputName: String,
    val cacheShape: IntArray,
    val positionsShape: IntArray
)

sealed class MossTtsNanoModelStatus {
    data object Ready : MossTtsNanoModelStatus()
    data object DirectoryNotConfigured : MossTtsNanoModelStatus()
    data class MissingFiles(val fileNames: List<String>) : MossTtsNanoModelStatus()
    data class InvalidConfig(val message: String) : MossTtsNanoModelStatus()

    fun displayName(lang: AppLanguage): String = when (this) {
        is Ready -> Strings.get(lang, StringsKey.voice_ready)
        is DirectoryNotConfigured -> Strings.get(lang, StringsKey.voice_moss_not_configured)
        is MissingFiles -> Strings.get(lang, StringsKey.voice_missing_files, fileNames.joinToString(", "))
        is InvalidConfig -> Strings.get(lang, StringsKey.voice_invalid_config, message)
    }
}
