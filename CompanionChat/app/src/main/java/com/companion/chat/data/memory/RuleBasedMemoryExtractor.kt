package com.companion.chat.data.memory

class RuleBasedMemoryExtractor : MemoryExtractor {

    override fun extract(userMessage: String, sessionId: String): List<ExtractedMemory> {
        val normalizedMessage = userMessage.trim()
        if (normalizedMessage.isEmpty()) {
            return emptyList()
        }

        return splitToClauses(normalizedMessage)
            .flatMap { clause -> extractFromClause(clause) }
            .distinctBy { "${it.category}|${it.content}" }
    }

    private fun extractFromClause(clause: String): List<ExtractedMemory> {
        return listOfNotNull(
            extractName(clause),
            extractPreference(clause),
            extractDislike(clause),
            extractLocation(clause),
            extractHabit(clause),
            extractSelfDescription(clause),
            extractResponseStyle(clause),
            extractAvoidance(clause)
        )
    }

    private fun extractName(message: String): ExtractedMemory? {
        val value = listOf(
            Regex("^记住我叫(.+)$"),
            Regex("^我叫(.+)$")
        ).firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(message)?.groupValues?.get(1)
        }?.sanitizeValue()

        return value?.let {
            createMemory(
                content = "用户叫$it",
                category = CATEGORY_FACT
            )
        }
    }

    private fun extractPreference(message: String): ExtractedMemory? {
        val value = Regex("^(?:我)?(?:很)?喜欢(.+)$")
            .matchEntire(message)
            ?.groupValues
            ?.get(1)
            ?.sanitizeValue()

        return value?.let {
            createMemory(
                content = "用户喜欢$it",
                category = CATEGORY_PREFERENCE
            )
        }
    }

    private fun extractDislike(message: String): ExtractedMemory? {
        val match = Regex("^(?:我)?(不喜欢|讨厌|不爱)(.+)$")
            .matchEntire(message)

        val prefix = match?.groupValues?.get(1)
        val value = match?.groupValues?.get(2)?.sanitizeValue()

        return if (prefix != null && value != null) {
            createMemory(
                content = "用户$prefix$value",
                category = CATEGORY_PREFERENCE
            )
        } else {
            null
        }
    }

    private fun extractLocation(message: String): ExtractedMemory? {
        val value = Regex("^我住在(.+)$")
            .matchEntire(message)
            ?.groupValues
            ?.get(1)
            ?.sanitizeValue()

        return value?.let {
            createMemory(
                content = "用户住在$it",
                category = CATEGORY_FACT
            )
        }
    }

    private fun extractHabit(message: String): ExtractedMemory? {
        val match = Regex("^我(一般|通常|经常|平时|常常)(.+)$")
            .matchEntire(message)

        val prefix = match?.groupValues?.get(1)
        val value = match?.groupValues?.get(2)?.sanitizeValue()

        return if (prefix != null && value != null) {
            createMemory(
                content = "用户$prefix$value",
                category = if (looksLikeTimeHabit("$prefix$value")) CATEGORY_TIME else CATEGORY_OTHER
            )
        } else {
            null
        }
    }

    private fun extractSelfDescription(message: String): ExtractedMemory? {
        val prefixedValue = Regex("^我(比较|很|挺|有点)(.+)$")
            .matchEntire(message)
            ?.let { match -> "${match.groupValues[1]}${match.groupValues[2]}" }
            ?.sanitizeValue()

        if (prefixedValue != null) {
            return createMemory(
                content = "用户$prefixedValue",
                category = CATEGORY_OTHER
            )
        }

        val identityValue = Regex("^我是个(.+?)(的人)?$")
            .matchEntire(message)
            ?.groupValues
            ?.get(1)
            ?.sanitizeValue()

        return identityValue?.let {
            createMemory(
                content = "用户是个${it}的人",
                category = CATEGORY_OTHER
            )
        }
    }

    private fun extractResponseStyle(message: String): ExtractedMemory? {
        val requestedStyle = listOf(
            Regex("^(?:以后)?请(?:尽量)?(.+)$"),
            Regex("^希望你(.+)$")
        ).firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(message)?.groupValues?.get(1)
        }?.sanitizeValue()

        if (requestedStyle != null) {
            return createMemory(
                content = "用户偏好$requestedStyle",
                category = CATEGORY_PREFERENCE
            )
        }

        val avoidStyle = Regex("^(?:回答时)?(?:别|不要)(太.+)$")
            .matchEntire(message)
            ?.groupValues
            ?.get(1)
            ?.sanitizeValue()

        return avoidStyle?.let {
            createMemory(
                content = "用户不喜欢$it",
                category = CATEGORY_PREFERENCE
            )
        }
    }

    private fun extractAvoidance(message: String): ExtractedMemory? {
        val value = Regex("^不要再说(.+?)(了)?$")
            .matchEntire(message)
            ?.groupValues
            ?.get(1)
            ?.sanitizeValue()

        return value?.let {
            createMemory(
                content = "不要再说$it",
                category = CATEGORY_PREFERENCE
            )
        }
    }

    private fun createMemory(content: String, category: String): ExtractedMemory {
        return ExtractedMemory(
            content = content,
            category = category,
            layer = LAYER_SHORT_TERM,
            source = SOURCE_RULE_EXTRACTOR
        )
    }

    private fun String.sanitizeValue(): String? {
        val sanitized = trim().trimEnd('。', '！', '？', '.', '!', '?')
        return sanitized.takeIf { it.isNotEmpty() }
    }

    private fun splitToClauses(message: String): List<String> {
        return message
            .split('，', ',', '。', ';', '；', '\n')
            .map { it.trim() }
            .map(::normalizeClause)
            .filter { it.isNotEmpty() }
    }

    private fun normalizeClause(clause: String): String {
        return clause
            .removePrefix("也")
            .removePrefix("而且")
            .removePrefix("另外")
            .trim()
    }

    private fun looksLikeTimeHabit(content: String): Boolean {
        return TIME_HINTS.any { hint -> content.contains(hint) }
    }

    companion object {
        private const val CATEGORY_FACT = "fact"
        private const val CATEGORY_PREFERENCE = "preference"
        private const val CATEGORY_TIME = "time"
        private const val CATEGORY_OTHER = "other"
        private const val LAYER_SHORT_TERM = "short_term"
        private const val SOURCE_RULE_EXTRACTOR = "rule_extractor"
        private val TIME_HINTS = listOf(
            "早上", "上午", "中午", "下午", "晚上", "凌晨",
            "点", "周末", "周一", "周二", "周三", "周四", "周五",
            "每天", "每日", "睡前", "起床后"
        )
    }
}
