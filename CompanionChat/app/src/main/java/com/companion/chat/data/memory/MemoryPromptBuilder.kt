package com.companion.chat.data.memory

import com.companion.chat.data.local.entity.Memory

class MemoryPromptBuilder {

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
