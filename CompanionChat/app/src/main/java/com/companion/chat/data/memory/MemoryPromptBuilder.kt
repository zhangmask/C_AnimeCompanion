package com.companion.chat.data.memory

import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.MetaMemory
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

/**
 * 记忆 Prompt 构建器 — 支持 L0/L1 分层注入 + Token 预算裁剪 + 多语言 + Meta-memory。
 *
 * 改造后：
 * - [buildLayered] 使用 L0 摘要 + L1 概览按 token 预算裁剪
 * - [build] 使用多语言标题（从 Strings.get 获取）
 * - [buildMetaMemorySection] 注入元记忆
 * - 废弃旧 VectorRetriever 依赖（检索移至 PprRetriever）
 */
class MemoryPromptBuilder {

    /**
     * 构建基础记忆 section（多语言）。
     */
    fun build(
        memories: List<Memory>,
        lang: AppLanguage = AppLanguage.ZH
    ): String {
        return buildSection(
            title = Strings.get(lang, StringsKey.memory_retrieved_title),
            note = Strings.get(lang, StringsKey.memory_user_note),
            memories = memories,
            lang = lang
        )
    }

    /**
     * 构建分层注入的提示。
     * 策略：先塞 L0 摘要列表（低成本快速扫描），再塞 L1 概览（按 token 预算裁剪）。
     *
     * @param memories 按评分降序排列的记忆
     * @param tokenBudget 注入的总 token 预算（默认 1200）
     * @param lang 语言
     * @param metaMemories 元记忆（可选）
     */
    fun buildLayered(
        memories: List<Memory>,
        tokenBudget: Int = MemoryConfig.DEFAULT_TOKEN_BUDGET,
        lang: AppLanguage = AppLanguage.ZH,
        metaMemories: List<MetaMemory> = emptyList()
    ): String {
        if (memories.isEmpty()) return ""

        val sorted = memories.sortedByDescending { it.strength }
        var usedTokens = 0

        return buildString {
            // 0. Meta-memory 提示（如果有）
            if (metaMemories.isNotEmpty()) {
                val metaSection = buildMetaMemorySection(metaMemories, lang)
                appendLine(metaSection)
                usedTokens += estimateTokens(metaSection)
            }

            // 1. L0 摘要列表（低成本快速扫描）
            val l0Items = sorted.mapNotNull { it.l0Summary?.takeIf { s -> s.isNotBlank() } }
            if (l0Items.isNotEmpty()) {
                val l0Text = buildSummaryList(l0Items, lang)
                appendLine(l0Text)
                usedTokens += estimateTokens(l0Text)
            }

            // 2. L1 概览（按 token 预算裁剪）
            val l1Items = mutableListOf<Memory>()
            for (memory in sorted) {
                val text = memory.l1Overview ?: memory.content
                val tokens = estimateTokens(text)
                if (usedTokens + tokens > tokenBudget) break
                l1Items.add(memory)
                usedTokens += tokens
            }

            if (l1Items.isNotEmpty()) {
                appendLine(buildSection(
                    title = Strings.get(lang, StringsKey.memory_retrieved_title),
                    note = Strings.get(lang, StringsKey.memory_user_note),
                    memories = l1Items,
                    lang = lang
                ))
            }
        }
    }

    /**
     * 构建元记忆注入 section。
     */
    fun buildMetaMemorySection(
        metaMemories: List<MetaMemory>,
        lang: AppLanguage = AppLanguage.ZH
    ): String {
        if (metaMemories.isEmpty()) return ""
        val title = Strings.get(lang, StringsKey.memory_meta_section_title)
        val items = metaMemories.joinToString("\n") { "- ${it.content}" }
        return "$title\n$items"
    }

    private fun buildSummaryList(items: List<String>, lang: AppLanguage): String {
        val itemsText = items.joinToString("\n") { "- $it" }
        return "${Strings.get(lang, StringsKey.memory_summary_title)}\n$itemsText"
    }

    private fun buildSection(
        title: String,
        note: String,
        memories: List<Memory>,
        lang: AppLanguage
    ): String {
        if (memories.isEmpty()) return ""

        val items = memories.joinToString("\n") { memory ->
            "- [${formatCategory(memory.category, lang)}] ${
                memory.l1Overview ?: memory.content
            }"
        }

        return "$title\n$note\n$items"
    }

    private fun formatCategory(category: String, lang: AppLanguage): String {
        return when (category) {
            "fact" -> Strings.get(lang, StringsKey.memory_category_fact)
            "preference" -> Strings.get(lang, StringsKey.memory_category_preference)
            "event" -> Strings.get(lang, StringsKey.memory_category_event)
            "behavior" -> Strings.get(lang, StringsKey.memory_category_behavior)
            "knowledge" -> Strings.get(lang, StringsKey.memory_category_knowledge)
            "skill" -> Strings.get(lang, StringsKey.memory_category_skill)
            "relation", "relationship" -> Strings.get(lang, StringsKey.memory_category_relation)
            "time" -> "时间"
            "other" -> Strings.get(lang, StringsKey.memory_category_other)
            else -> category
        }
    }

    /** 粗略估算 token 数：中文每字2 tokens，英文每4字符1 token。 */
    private fun estimateTokens(text: String): Int {
        val chineseCount = text.count { it in '\u4e00'..'\u9fff' || it in '\u3000'..'\u303f' }
        val otherCount = text.length - chineseCount
        return (chineseCount * 2 + otherCount / 4) + 1
    }
}
