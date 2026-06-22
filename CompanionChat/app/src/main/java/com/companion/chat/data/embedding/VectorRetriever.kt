package com.companion.chat.data.embedding

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 向量检索器
 * 使用嵌入模型和余弦相似度进行语义检索
 */
class VectorRetriever(private val embeddingEngine: OnnxEmbeddingEngine) {

    // 记忆索引：id -> 嵌入向量
    private val embeddingIndex = mutableMapOf<Long, FloatArray>()
    private var isIndexDirty = true
    // 缓存上次索引源数据，用于懒重建
    private var lastIndexedMemories: List<Pair<Long, String>> = emptyList()

    /**
     * 更新索引
     * @param memories 记忆列表，Pair<id, content>
     */
    suspend fun updateIndex(memories: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        lastIndexedMemories = memories
        embeddingIndex.clear()

        for ((id, content) in memories) {
            val embedding = embeddingEngine.embed(content)
            if (embedding != null) {
                embeddingIndex[id] = embedding
            }
        }

        isIndexDirty = false
        Log.d(TAG, "索引更新完成，共 ${embeddingIndex.size} 条记忆")
    }

    /**
     * 懒重建：索引为空但有缓存源数据时，自动从缓存重建
     */
    private suspend fun ensureIndexReady() {
        if (embeddingIndex.isEmpty() && lastIndexedMemories.isNotEmpty() && embeddingEngine.isInitialized) {
            Log.d(TAG, "索引为空但有缓存数据，执行懒重建")
            updateIndex(lastIndexedMemories)
        }
    }

    /**
     * 检索相关记忆
     * @param query 查询文本
     * @param topK 返回前 K 个结果
     * @return 相关记忆的 ID 列表
     */
    suspend fun retrieve(query: String, topK: Int = 5): List<Long> = withContext(Dispatchers.IO) {
        ensureIndexReady()
        if (embeddingIndex.isEmpty()) return@withContext emptyList()

        val queryEmbedding = embeddingEngine.embed(query)
            ?: return@withContext emptyList()

        // 计算与所有记忆的相似度
        val similarities = embeddingIndex.map { (id, embedding) ->
            val similarity = embeddingEngine.cosineSimilarity(queryEmbedding, embedding)
            id to similarity
        }

        // 按相似度降序排序，取 topK
        similarities
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    /**
     * 检索相关记忆（带分数）
     * @param query 查询文本
     * @param topK 返回前 K 个结果
     * @return 相关记忆的 ID 和分数列表
     */
    suspend fun retrieveWithScores(query: String, topK: Int = 5): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        ensureIndexReady()
        if (embeddingIndex.isEmpty()) return@withContext emptyList()

        val queryEmbedding = embeddingEngine.embed(query)
            ?: return@withContext emptyList()

        // 计算与所有记忆的相似度
        val similarities = embeddingIndex.map { (id, embedding) ->
            val similarity = embeddingEngine.cosineSimilarity(queryEmbedding, embedding)
            id to similarity
        }

        // 按相似度降序排序，取 topK
        similarities
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first to it.second }
    }

    /**
     * 获取索引大小
     */
    fun indexSize(): Int = embeddingIndex.size

    /**
     * 清空索引
     */
    fun clearIndex() {
        embeddingIndex.clear()
        isIndexDirty = true
    }

    companion object {
        private const val TAG = "VectorRetriever"
    }
}
