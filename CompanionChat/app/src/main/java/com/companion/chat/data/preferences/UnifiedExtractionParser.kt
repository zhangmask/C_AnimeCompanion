package com.companion.chat.data.preferences

import com.companion.chat.data.memory.ExtractedMemory
import com.companion.chat.data.memory.MemoryRepository

class UnifiedExtractionParser {

    fun parse(raw: String): UnifiedExtractionResult {
        val jsonObject = extractJsonObject(raw) ?: return UnifiedExtractionResult()
        val rawMemoryItems = parseRawItems(jsonObject, MEMORY_ARRAY_FIELD_NAMES)
        val rawPreferenceItems = parseRawItems(jsonObject, PREFERENCE_ARRAY_FIELD_NAMES)
        val memories = parseMemories(rawMemoryItems)
        val userPreferences = parseUserPreferences(
            rawPreferenceItems + recoverPreferenceItemsFromMemories(rawMemoryItems)
        )
        return UnifiedExtractionResult(
            memories = memories,
            userPreferences = userPreferences
        )
    }

    private fun parseRawItems(objectText: String, fieldNames: Set<String>): List<RawExtractionItem> {
        val arrayText = extractArrayValue(objectText, fieldNames) ?: return emptyList()
        return OBJECT_REGEX.findAll(arrayText).mapNotNull { match ->
            val itemText = match.value
            val category = extractField(itemText, CATEGORY_FIELD_NAMES)?.trim().orEmpty()
            val content = extractField(itemText, CONTENT_FIELD_NAMES)?.trim().orEmpty()
            if (category.isBlank() || content.isBlank()) {
                null
            } else {
                RawExtractionItem(
                    category = category,
                    content = content
                )
            }
        }.toList()
    }

    private fun parseMemories(items: List<RawExtractionItem>): List<ExtractedMemory> {
        return items.mapNotNull { item ->
            val category = normalizeMemoryCategory(
                item.category.trim().lowercase()
            )
            val content = item.content.trim()
            if (category !in ALLOWED_MEMORY_CATEGORIES || content.isBlank()) {
                null
            } else {
                ExtractedMemory(
                    content = content,
                    category = category,
                    layer = "short_term",
                    source = MemoryRepository.MODEL_SOURCE
                )
            }
        }.distinctBy { "${it.category}|${it.content.trim().lowercase()}" }
    }

    private fun parseUserPreferences(items: List<RawExtractionItem>): List<ExtractedPreference> {
        return items.mapNotNull { item ->
            val category = normalizePreferenceCategory(
                item.category.trim().lowercase()
            )
            val content = item.content.trim()
            if (category !in ALLOWED_PREFERENCE_CATEGORIES || content.isBlank()) {
                null
            } else {
                ExtractedPreference(
                    category = category,
                    content = content
                )
            }
        }.distinctBy { "${it.category}|${it.content.trim().lowercase()}" }
    }

    private fun recoverPreferenceItemsFromMemories(items: List<RawExtractionItem>): List<RawExtractionItem> {
        return items.mapNotNull { item ->
            val normalizedPreferenceCategory = normalizePreferenceCategory(item.category.trim().lowercase())
            if (normalizedPreferenceCategory in ALLOWED_PREFERENCE_CATEGORIES) {
                return@mapNotNull RawExtractionItem(
                    category = normalizedPreferenceCategory,
                    content = item.content
                )
            }

            inferPreferenceCategoryFromMemoryItem(item)?.let { inferredCategory ->
                RawExtractionItem(
                    category = inferredCategory,
                    content = item.content
                )
            }
        }
    }

    private fun inferPreferenceCategoryFromMemoryItem(item: RawExtractionItem): String? {
        val normalizedMemoryCategory = normalizeMemoryCategory(item.category.trim().lowercase())
        val content = item.content.trim()

        return when {
            looksLikeName(content) -> "name"
            normalizedMemoryCategory == "time" || looksLikeHabit(content) -> "habit"
            looksLikeStyle(content) -> "style"
            looksLikeInterest(content) -> "interest"
            normalizedMemoryCategory == "other" || looksLikeOtherPreference(content) -> "other"
            else -> null
        }
    }

    private fun looksLikeName(content: String): Boolean {
        return content.startsWith("叫") || content.startsWith("名叫")
    }

    private fun looksLikeStyle(content: String): Boolean {
        return STYLE_HINTS.any { hint -> content.contains(hint) } ||
            content.startsWith("以后请") ||
            content.startsWith("请") ||
            content.startsWith("希望你")
    }

    private fun looksLikeInterest(content: String): Boolean {
        return INTEREST_HINTS.any { hint -> content.contains(hint) }
    }

    private fun looksLikeHabit(content: String): Boolean {
        return HABIT_HINTS.any { hint -> content.contains(hint) }
    }

    private fun looksLikeOtherPreference(content: String): Boolean {
        return OTHER_HINTS.any { hint -> content.startsWith(hint) || content.contains(hint) }
    }

    private fun extractJsonObject(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            return null
        }

        val unfenced = CODE_BLOCK_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.trim() ?: normalized
        val startIndex = unfenced.indexOf('{')
        val endIndex = unfenced.lastIndexOf('}')
        if (startIndex < 0 || endIndex <= startIndex) {
            return null
        }
        return unfenced.substring(startIndex, endIndex + 1)
    }

    private fun extractArrayValue(objectText: String, fieldNames: Set<String>): String? {
        fieldNames.forEach { fieldName ->
            val fieldRegex = Regex("""["']$fieldName["']\s*:\s*\[""", RegexOption.IGNORE_CASE)
            val match = fieldRegex.find(objectText) ?: return@forEach
            val arrayStart = match.range.last
            val arrayEnd = findMatchingBracket(objectText, arrayStart)
            if (arrayEnd > arrayStart) {
                return objectText.substring(arrayStart, arrayEnd + 1)
            }
        }
        return null
    }

    private fun findMatchingBracket(text: String, startIndex: Int): Int {
        var depth = 0
        var inString = false
        var stringQuote = '"'
        var escaped = false

        for (index in startIndex until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
                continue
            }

            when {
                char == '\\' -> escaped = true
                inString && char == stringQuote -> inString = false
                !inString && (char == '"' || char == '\'') -> {
                    inString = true
                    stringQuote = char
                }
                !inString && char == '[' -> depth += 1
                !inString && char == ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }

        return -1
    }

    private fun extractField(objectText: String, fieldNames: Set<String>): String? {
        return fieldNames.firstNotNullOfOrNull { fieldName ->
            val regex = Regex("""["']$fieldName["']\s*:\s*["']((?:\\.|[^"'\\])*)["']""")
            regex.find(objectText)?.groupValues?.getOrNull(1)
        }?.let { value ->
            value
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
        }
    }

    private fun normalizeMemoryCategory(category: String): String {
        return MEMORY_CATEGORY_ALIASES[category] ?: category
    }

    private fun normalizePreferenceCategory(category: String): String {
        return PREFERENCE_CATEGORY_ALIASES[category] ?: category
    }

    companion object {
        private val MEMORY_ARRAY_FIELD_NAMES = setOf("memories", "memory", "记忆")
        private val PREFERENCE_ARRAY_FIELD_NAMES = setOf("user_preferences", "preferences", "偏好")
        private val CATEGORY_FIELD_NAMES = setOf("category", "类别")
        private val CONTENT_FIELD_NAMES = setOf("content", "内容")
        private val ALLOWED_MEMORY_CATEGORIES = setOf("fact", "preference", "event", "relation", "time", "other")
        private val ALLOWED_PREFERENCE_CATEGORIES = setOf("name", "style", "interest", "habit", "other")
        private val MEMORY_CATEGORY_ALIASES = mapOf(
            "fact" to "fact",
            "事实" to "fact",
            "preference" to "preference",
            "偏好" to "preference",
            "event" to "event",
            "事件" to "event",
            "relation" to "relation",
            "relationship" to "relation",
            "关系" to "relation",
            "time" to "time",
            "时间" to "time",
            "other" to "other",
            "其他" to "other"
        )
        private val PREFERENCE_CATEGORY_ALIASES = mapOf(
            "name" to "name",
            "名字" to "name",
            "称呼" to "name",
            "style" to "style",
            "风格" to "style",
            "interest" to "interest",
            "兴趣" to "interest",
            "habit" to "habit",
            "习惯" to "habit",
            "other" to "other",
            "其他" to "other"
        )
        private val STYLE_HINTS = listOf(
            "回答", "简洁", "直接", "官方", "举例", "详细", "语气", "风格"
        )
        private val INTEREST_HINTS = listOf(
            "喜欢", "热爱", "偏爱", "不喜欢", "讨厌", "不爱"
        )
        private val HABIT_HINTS = listOf(
            "一般", "通常", "经常", "平时", "常常",
            "早上", "上午", "中午", "下午", "晚上", "凌晨",
            "周末", "每天", "每日", "睡前", "起床后", "点后", "点前"
        )
        private val OTHER_HINTS = listOf(
            "比较", "很", "挺", "有点", "容易", "偏", "慢热", "内向", "外向"
        )
        private val OBJECT_REGEX = Regex("""\{[^{}]*\}""")
        private val CODE_BLOCK_REGEX = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
    }

    private data class RawExtractionItem(
        val category: String,
        val content: String
    )
}
