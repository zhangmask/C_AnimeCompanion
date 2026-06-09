package com.companion.chat.data.memory

import com.companion.chat.data.embedding.VectorRetriever
import com.companion.chat.data.local.entity.Memory

class MemoryPromptBuilder(
    private val vectorRetriever: VectorRetriever? = null
) {

    private var allMemories: List<Memory> = emptyList()

    /**
     * 更新向量索引
     * 应在记忆变化时调用
     */
    suspend fun updateIndex(memories: List<Memory>) {
        allMemories = memories
        val indexData = memories.map { memory ->
            memory.id to "${memory.category} ${memory.content}"
        }
        vectorRetriever?.updateIndex(indexData)
    }

    /**
     * 使用向量检索相关记忆
     * @param query 用户输入
     * @param topK 返回前 K 个结果
     * @return 相关记忆列表
     */
    suspend fun retrieveRelevant(query: String, topK: Int = 5): List<Memory> {
        if (allMemories.isEmpty() || vectorRetriever == null) return emptyList()

        val relevantIds = vectorRetriever.retrieve(query, topK)
        val memoryMap = allMemories.associateBy { it.id }

        return relevantIds.mapNotNull { memoryMap[it] }
    }

    /**
     * 使用向量检索相关记忆（带分数）
     */
    suspend fun retrieveRelevantWithScores(query: String, topK: Int = 5): List<Pair<Memory, Double>> {
        if (allMemories.isEmpty() || vectorRetriever == null) return emptyList()

        val results = vectorRetriever.retrieveWithScores(query, topK)
        val memoryMap = allMemories.associateBy { it.id }

        return results.mapNotNull { (id, score) ->
            memoryMap[id]?.let { it to score }
        }
    }

    fun build(memories: List<Memory>): String {
        return buildSection(
            title = "从记忆中检索到的与当前对话相关的信息：",
            memories = memories
        )
    }

    fun buildPersistent(memories: List<Memory>): String {
        return buildSection(
            title = "长期记忆中的关键信息：",
            memories = memories
        )
    }

    fun buildCombined(
        persistentMemories: List<Memory>,
        retrievedMemories: List<Memory>
    ): String {
        val sections = listOf(
            buildPersistent(persistentMemories),
            build(retrievedMemories)
        ).filter { it.isNotBlank() }

        return sections.joinToString(separator = "\n\n")
    }

    /**
     * 构建分层注入的提示
     * 优先级：长期记忆 > 高相关性记忆 > 低相关性记忆
     */
    suspend fun buildLayered(
        persistentMemories: List<Memory>,
        query: String,
        maxMemories: Int = 10
    ): String {
        val sections = mutableListOf<String>()

        // 1. 长期记忆（始终注入）
        if (persistentMemories.isNotEmpty()) {
            sections.add(buildPersistent(persistentMemories))
        }

        // 2. 使用向量检索相关记忆
        val relevantWithScores = retrieveRelevantWithScores(query, maxMemories)
        if (relevantWithScores.isNotEmpty()) {
            val relevantMemories = relevantWithScores.map { it.first }
            sections.add(build(relevantMemories))
        }

        return sections.filter { it.isNotBlank() }.joinToString(separator = "\n\n")
    }

    private fun buildSection(title: String, memories: List<Memory>): String {
        if (memories.isEmpty()) {
            return ""
        }

        val items = memories.joinToString(separator = "\n") { memory ->
            "- [${formatCategory(memory.category)}] ${memory.content}"
        }

        return "$title\n$USER_MEMORY_NOTE\n$items"
    }

    private fun formatCategory(category: String): String {
        return when (category) {
            "fact" -> "事实"
            "preference" -> "偏好"
            "event" -> "事件"
            "relation", "relationship" -> "关系"
            "time" -> "时间"
            "other" -> "其他"
            else -> category
        }
    }

    companion object {
        private const val USER_MEMORY_NOTE = "以下内容均为用户本人的记忆，不代表助手自身。"
    }
}
