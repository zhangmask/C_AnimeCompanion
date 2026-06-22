package com.companion.chat.data.memory

class RuleBasedMemoryExtractor : MemoryExtractor {

    override fun extract(userMessage: String, sessionId: String): List<ExtractedMemory> {
        val normalizedMessage = userMessage.trim()
        if (normalizedMessage.isEmpty()) {
            return emptyList()
        }

        // 知知识问题的消息不提取记忆（如"勾股定理是什么""怎么做饭"）
        if (looksLikeKnowledgeQuery(normalizedMessage)) {
            return emptyList()
        }

        return splitToClauses(normalizedMessage)
            .flatMap { clause -> extractFromClause(clause) }
            .distinctBy { "${it.category}|${it.content}" }
    }

    /** 判断是否为知识问答（而非用户表达个人信息） */
    private fun looksLikeKnowledgeQuery(message: String): Boolean {
        return KNOWLEDGE_QUERY_PATTERNS.any { it.containsMatchIn(message) }
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
        var normalized = clause.trim()
        // 反复去除前缀修饰词，直到无法再去除（处理"其实我也挺…"等多层前缀）
        var changed = true
        while (changed) {
            changed = false
            for (prefix in DISCOURSE_PREFIXES) {
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.substring(prefix.length).trim()
                    changed = true
                }
            }
        }
        // 去除句末语气词，它们不影响语义但会阻断 $ 匹配
        normalized = normalized.trimEnd('的', '了', '吧', '呢', '啊', '呀', '嘛', '哦', '哈', '咯')
        return normalized.trim()
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
        // 话语前缀修饰词，去除后让核心正则能匹配
        private val DISCOURSE_PREFIXES = listOf(
            "其实", "实际上", "说真的", "说实话", "老实说",
            "不过", "但是", "然而", "可是", "只是",
            "而且", "并且", "另外", "还有", "同时",
            "也", "还", "又",
            "我觉得", "我认为", "我感觉", "我想",
            "对", "嗯", "啊", "哦"
        )
        // 知识问答模式：问知识而非表达个人信息
        private val KNOWLEDGE_QUERY_PATTERNS = listOf(
            Regex("什么是.+|什么叫做.+|什么叫.+"),
            Regex(".+是什么$|.+是什么意思$"),
            Regex("怎么.+|如何.+|怎样.+"),
            Regex("为什么.+"),
            Regex("请解释.+|请说明.+"),
            Regex("勾股|定理|公式|定义|原理")
        )
        private val TIME_HINTS = listOf(
            "早上", "上午", "中午", "下午", "晚上", "凌晨",
            "点", "周末", "周一", "周二", "周三", "周四", "周五",
            "每天", "每日", "睡前", "起床后"
        )
    }
}
