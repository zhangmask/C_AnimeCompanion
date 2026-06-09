package com.companion.chat.data.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer

/**
 * ONNX Runtime 嵌入引擎
 * 使用小型嵌入模型生成语义向量
 */
class OnnxEmbeddingEngine(private val context: Context) {

    private var session: OrtSession? = null
    private val env = OrtEnvironment.getEnvironment()
    private var tokenizer: SimpleTokenizer? = null
    private var isInitialized = false

    // 模型配置
    private val maxLength = 128  // 最大序列长度
    private val embeddingDim = 512  // 嵌入维度

    /**
     * 初始化引擎
     * @param modelPath 模型文件路径（assets 目录下）
     * @param vocabPath 词表文件路径（assets 目录下）
     */
    suspend fun initialize(modelPath: String, vocabPath: String) = withContext(Dispatchers.IO) {
        try {
            // 加载词表
            tokenizer = SimpleTokenizer(context).apply {
                loadVocab(vocabPath)
            }

            // 加载 ONNX 模型
            val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
            session = env.createSession(modelBytes)

            isInitialized = true
            Log.d(TAG, "嵌入引擎初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "嵌入引擎初始化失败", e)
            isInitialized = false
        }
    }

    /**
     * 生成文本嵌入向量
     * @param text 输入文本
     * @return 嵌入向量，失败返回 null
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isInitialized || session == null || tokenizer == null) {
            return@withContext null
        }

        try {
            // Tokenize
            val tokenIds = tokenizer!!.tokenize(text, maxLength)
            val inputIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
            val attentionMask = LongArray(tokenIds.size) { if (tokenIds[it] != 0) 1L else 0L }
            val tokenTypeIds = LongArray(tokenIds.size) { 0L }

            // 创建输入张量
            val inputIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(inputIds),
                longArrayOf(1, inputIds.size.toLong())
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(attentionMask),
                longArrayOf(1, attentionMask.size.toLong())
            )
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenTypeIds),
                longArrayOf(1, tokenTypeIds.size.toLong())
            )

            // 运行推理
            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            val results = session!!.run(inputs)
            val output = results[0].value

            // 提取嵌入向量
            val embedding = when (output) {
                is Array<*> -> {
                    // 输出形状: [batch_size, sequence_length, embedding_dim]
                    // 取 [CLS] token 的嵌入（第一个 token）
                    val firstBatch = output[0] as? Array<*>
                    val firstToken = firstBatch?.get(0) as? FloatArray
                    firstToken
                }
                is FloatArray -> output
                else -> null
            }

            // 释放资源
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            results.close()

            embedding
        } catch (e: Exception) {
            Log.e(TAG, "嵌入生成失败", e)
            null
        }
    }

    /**
     * 批量生成嵌入向量
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        texts.map { embed(it) }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Double {
        if (vec1.size != vec2.size) return 0.0

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2))
    }

    /**
     * 释放资源
     */
    fun release() {
        session?.close()
        session = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "OnnxEmbeddingEngine"

        // 默认模型路径
        const val DEFAULT_MODEL_PATH = "embedding/model.onnx"
        const val DEFAULT_VOCAB_PATH = "embedding/vocab.txt"
    }
}
