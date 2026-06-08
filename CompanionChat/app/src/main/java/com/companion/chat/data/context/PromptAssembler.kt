package com.companion.chat.data.context

class PromptAssembler {

    fun assemble(
        baseSystemPrompt: String,
        userPreferences: String,
        persistentMemoryPrompt: String = "",
        memoryPrompt: String = "",
        historySummary: String,
        recentConversationSnippet: String = ""
    ): String {
        val sections = mutableListOf<String>()
        val hasMemoryContext = persistentMemoryPrompt.isNotBlank() || memoryPrompt.isNotBlank()

        if (baseSystemPrompt.isNotBlank()) {
            sections += baseSystemPrompt.trim()
        }
        if (userPreferences.isNotBlank()) {
            sections += userPreferences.trim()
        }
        if (hasMemoryContext) {
            sections += MEMORY_INTERPRETATION_RULES
        }
        if (persistentMemoryPrompt.isNotBlank()) {
            sections += persistentMemoryPrompt.trim()
        }
        if (memoryPrompt.isNotBlank()) {
            sections += memoryPrompt.trim()
        }
        if (historySummary.isNotBlank()) {
            sections += "之前对话的摘要：\n${historySummary.trim()}"
        }
        if (recentConversationSnippet.isNotBlank()) {
            sections += "最近几轮对话片段：\n${recentConversationSnippet.trim()}"
        }

        return sections.joinToString(separator = "\n\n")
    }

    companion object {
        private val MEMORY_INTERPRETATION_RULES = """
记忆解释规则：
- 以下记忆都描述用户本人的信息、关系、偏好或经历，不是助手自己的信息。
- 除非用户明确要求角色扮演、改写文案或切换叙述视角，记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。
- 回答涉及这些记忆时，应使用“你”或“用户”的视角理解和表达。
""".trimIndent()
    }
}
