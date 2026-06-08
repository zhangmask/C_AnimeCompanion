package com.companion.chat.data.memory

import androidx.sqlite.db.SimpleSQLiteQuery
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.entity.Memory

class MemoryRetriever(
    private val memoryDao: MemoryDao
) {

    suspend fun retrieveRelevantMemories(userMessage: String): List<Memory> {
        val keywords = extractKeywords(userMessage)
        if (keywords.isEmpty()) {
            return emptyList()
        }

        val ftsExpression = keywords.joinToString(separator = " OR ")
        val query = SimpleSQLiteQuery(
            """
            SELECT memories.* FROM memories
            JOIN memories_fts ON memories.id = memories_fts.docid
            WHERE memories_fts MATCH '${escapeSqlLiteral(ftsExpression)}'
            """.trimIndent()
        )

        val fallbackMatches = memoryDao.getAll().filter { memory ->
            val content = memory.content.lowercase()
            keywords.any { keyword -> content.contains(keyword) }
        }

        val results = (memoryDao.searchByFTS(query) + fallbackMatches)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Memory> { it.layer == LONG_TERM_LAYER }
                    .thenByDescending { it.referenceCount }
                    .thenByDescending { it.updatedAt }
            )
            .take(MAX_RESULTS)

        results.forEach { memoryDao.incrementReference(it.id) }
        return results
    }

    private fun extractKeywords(userMessage: String): List<String> {
        val normalized = normalizeMessage(userMessage)

        return normalized.split(WHITESPACE_REGEX)
            .flatMap { token ->
                TOKEN_REGEX.findAll(token).map { it.value }.toList()
            }
            .map { it.trim().lowercase() }
            .filter { isMeaningfulKeyword(it) }
            .distinct()
    }

    private fun normalizeMessage(userMessage: String): String {
        var normalized = userMessage.lowercase()
        NORMALIZATION_RULES.forEach { (pattern, replacement) ->
            normalized = normalized.replace(pattern, replacement)
        }
        normalized = normalized.replace(PUNCTUATION_REGEX, " ")
        return normalized.replace(WHITESPACE_REGEX, " ").trim()
    }

    private fun isMeaningfulKeyword(keyword: String): Boolean {
        if (keyword.isBlank() || keyword in STOP_WORDS) {
            return false
        }

        if (LATIN_OR_NUMBER_REGEX.matches(keyword)) {
            return keyword.length >= MIN_LATIN_KEYWORD_LENGTH
        }

        return keyword.length >= MIN_CJK_KEYWORD_LENGTH || keyword in SINGLE_CHAR_KEYWORDS
    }

    private fun escapeSqlLiteral(value: String): String {
        return value.replace("'", "''")
    }

    companion object {
        private const val LONG_TERM_LAYER = "long_term"
        private const val MAX_RESULTS = 5
        private const val MIN_LATIN_KEYWORD_LENGTH = 2
        private const val MIN_CJK_KEYWORD_LENGTH = 2
        private val TOKEN_REGEX = Regex("[a-z0-9]+|[\\u4E00-\\u9FFF]+")
        private val LATIN_OR_NUMBER_REGEX = Regex("[a-z0-9]+")
        private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\u4E00-\\u9FFF]+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val SINGLE_CHAR_KEYWORDS = setOf("叫")
        private val STOP_WORDS = setOf(
            "这个",
            "那个",
            "一下",
            "请问",
            "帮我",
            "帮忙",
            "可以",
            "吗",
            "呢",
            "呀",
            "啊"
        )
        private val NORMALIZATION_RULES = listOf(
            Regex("(和我|跟我|与我)什么关系") to " 关系 ",
            Regex("什么关系") to " 关系 ",
            Regex("是谁") to " ",
            Regex("喜欢什么") to " 喜欢 ",
            Regex("住在哪(里)?") to " 住在 ",
            Regex("叫什么名字") to " 叫 名字 ",
            Regex("叫什么") to " 叫 ",
            Regex("名字是什么") to " 名字 "
        )
    }
}
