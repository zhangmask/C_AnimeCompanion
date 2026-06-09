package com.companion.chat.data.context.retrieval

import com.companion.chat.data.context.tokenizer.SimpleChineseTokenizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * BM25 算法实现
 * 适用于手机端的轻量级文本检索
 */
class BM25(
    private val k1: Double = 1.5,  // 词频饱和参数
    private val b: Double = 0.75   // 文档长度归一化参数
) {
    // 文档分词结果缓存
    private var documentTokens: List<List<String>> = emptyList()
    private var documentLengths: List<Int> = emptyList()
    private var avgDocLength: Double = 0.0
    private var totalDocs: Int = 0

    // 词频统计：term -> 文档频率
    private var documentFrequency: Map<String, Int> = emptyMap()
    // 每个文档的词频：docIndex -> (term -> freq)
    private var termFrequencies: List<Map<String, Int>> = emptyList()

    /**
     * 构建索引
     * @param documents 文档列表（每个文档是一个字符串）
     */
    fun buildIndex(documents: List<String>) {
        totalDocs = documents.size
        if (totalDocs == 0) return

        // 分词
        documentTokens = documents.map { SimpleChineseTokenizer.tokenize(it) }
        documentLengths = documentTokens.map { it.size }
        avgDocLength = documentLengths.average()

        // 计算词频和文档频率
        val df = mutableMapOf<String, Int>()
        val tfList = mutableListOf<Map<String, Int>>()

        for (tokens in documentTokens) {
            val tf = mutableMapOf<String, Int>()
            for (token in tokens) {
                tf[token] = (tf[token] ?: 0) + 1
            }
            tfList.add(tf)

            // 更新文档频率
            for (term in tf.keys) {
                df[term] = (df[term] ?: 0) + 1
            }
        }

        documentFrequency = df
        termFrequencies = tfList
    }

    /**
     * 计算查询与文档的 BM25 分数
     * @param query 查询文本
     * @param docIndex 文档索引
     * @return BM25 分数
     */
    fun score(query: String, docIndex: Int): Double {
        if (totalDocs == 0 || docIndex >= totalDocs) return 0.0

        val queryTokens = SimpleChineseTokenizer.tokenize(query)
        if (queryTokens.isEmpty()) return 0.0

        val docLength = documentLengths[docIndex].toDouble()
        val tf = termFrequencies[docIndex]

        var score = 0.0

        for (term in queryTokens) {
            val termFreq = tf[term] ?: 0
            if (termFreq == 0) continue

            val docFreq = documentFrequency[term] ?: 0
            if (docFreq == 0) continue

            // IDF 部分：log((N - df + 0.5) / (df + 0.5) + 1)
            val idf = ln((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0)

            // TF 部分：(tf * (k1 + 1)) / (tf + k1 * (1 - b + b * dl / avgdl))
            val tfNorm = (termFreq * (k1 + 1)) / (termFreq + k1 * (1 - b + b * docLength / avgDocLength))

            score += idf * tfNorm
        }

        return score
    }

    /**
     * 搜索最相关的文档
     * @param query 查询文本
     * @param topK 返回前 K 个结果
     * @return 相关文档索引和分数的列表，按分数降序排列
     */
    fun search(query: String, topK: Int = 5): List<SearchResult> {
        if (totalDocs == 0) return emptyList()

        val scores = (0 until totalDocs).map { index ->
            SearchResult(index = index, score = score(query, index))
        }

        return scores
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * 搜索结果
     */
    data class SearchResult(
        val index: Int,
        val score: Double
    )
}

/**
 * 带记忆的 BM25 检索器
 * 支持动态添加和删除文档
 */
class BM25Retriever {
    private val bm25 = BM25()
    private var documents: List<String> = emptyList()
    private var documentIds: List<Long> = emptyList()

    /**
     * 更新索引
     * @param memories 记忆列表，Pair<id, content>
     */
    fun updateIndex(memories: List<Pair<Long, String>>) {
        documentIds = memories.map { it.first }
        documents = memories.map { it.second }
        bm25.buildIndex(documents)
    }

    /**
     * 检索相关记忆
     * @param query 查询文本
     * @param topK 返回前 K 个结果
     * @return 相关记忆的 ID 列表
     */
    fun retrieve(query: String, topK: Int = 5): List<Long> {
        val results = bm25.search(query, topK)
        return results.map { documentIds[it.index] }
    }

    /**
     * 检索相关记忆（带分数）
     * @param query 查询文本
     * @param topK 返回前 K 个结果
     * @return 相关记忆的 ID 和分数列表
     */
    fun retrieveWithScores(query: String, topK: Int = 5): List<Pair<Long, Double>> {
        val results = bm25.search(query, topK)
        return results.map { documentIds[it.index] to it.score }
    }
}
