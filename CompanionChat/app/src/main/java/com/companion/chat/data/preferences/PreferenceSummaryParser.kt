package com.companion.chat.data.preferences

class PreferenceSummaryParser {

    fun parse(raw: String): List<ExtractedPreference> {
        val normalized = extractJsonArray(raw)
        if (normalized == null) {
            return emptyList()
        }

        return buildList {
            OBJECT_REGEX.findAll(normalized).forEach { match ->
                val objectText = match.value
                val category = normalizeCategory(
                    extractField(objectText, CATEGORY_FIELD_NAMES)
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                )
                val content = extractField(objectText, CONTENT_FIELD_NAMES)?.trim().orEmpty()
                if (category in ALLOWED_CATEGORIES && content.isNotBlank()) {
                    add(ExtractedPreference(category = category, content = content))
                }
            }
        }
    }

    private fun extractJsonArray(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            return null
        }

        val unfenced = CODE_BLOCK_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.trim() ?: normalized
        val startIndex = unfenced.indexOf('[')
        val endIndex = unfenced.lastIndexOf(']')
        if (startIndex < 0 || endIndex <= startIndex) {
            return null
        }
        return unfenced.substring(startIndex, endIndex + 1)
    }

    private fun extractField(objectText: String, fieldNames: Set<String>): String? {
        val value = fieldNames.firstNotNullOfOrNull { fieldName ->
            val regex = Regex("""["']$fieldName["']\s*:\s*["']((?:\\.|[^"'\\])*)["']""")
            regex.find(objectText)?.groupValues?.getOrNull(1)
        } ?: return null
        return value
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    }

    private fun normalizeCategory(category: String): String {
        return CATEGORY_ALIASES[category] ?: category
    }

    companion object {
        private val ALLOWED_CATEGORIES = setOf("name", "style", "interest", "habit", "other")
        private val CATEGORY_FIELD_NAMES = setOf("category", "类别")
        private val CONTENT_FIELD_NAMES = setOf("content", "内容")
        private val CATEGORY_ALIASES = mapOf(
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
        private val OBJECT_REGEX = Regex("""\{[^{}]*\}""")
        private val CODE_BLOCK_REGEX = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
    }
}
