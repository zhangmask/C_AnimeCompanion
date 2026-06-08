package com.companion.chat.engine

/**
 * MOSS-TTS-Nano 文本规范化器。
 * 移植自 browser_onnx_runtime.js 的 normalizeTtsText() 管线。
 */
object MossTtsTextNormalizer {

    // ── CJK 范围 ──
    private val CJK_RE = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff]")

    // ── 需要保护的特殊标识符 ──
    private val URL_RE = Regex("https?://[^\\s\\u3000，。！？；、）】》〉」』]+")
    private val EMAIL_RE = Regex("(?<![\\w.+-])[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}(?![\\w.\\-])")
    private val MENTION_RE = Regex("(?<![A-Za-z0-9_])@[A-Za-z0-9_]{1,32}")
    private val HASHTAG_RE = Regex("(?<![A-Za-z0-9_])#(?!\\s)[^\\s#]+")

    // ── 零宽字符 ──
    private val ZERO_WIDTH_RE = Regex("[\\u200b-\\u200d\\ufeff]")

    // ── 尾部闭合符 ──
    private val TRAILING_CLOSERS = setOf(
        '"', '\'', ')', ']', '}',
        '）', '】', '》', '〉', '」', '』', '\u201D', '\u2019'
    )

    /**
     * 主入口：规范化文本以供 TTS 合成。
     */
    fun normalize(text: String): String {
        var result = baseCleanup(text)
        result = normalizeMarkdownAndLines(result)
        result = normalizeFlowArrows(result)

        // 保护 URL/email/mention 等特殊标识符
        val protectedSpans = mutableListOf<String>()
        result = protectSpans(result, protectedSpans)
        result = normalizeVisibleUnderscores(result)
        result = normalizeSpaces(result)
        result = normalizeStructuralPunctuation(result)
        result = normalizeRepeatedPunctuation(result)
        result = normalizeSpaces(result)
        result = restoreSpans(result, protectedSpans)
        result = result.trim()
        result = ensureTerminalPunctuationByLine(result)
        return result
    }

    private fun baseCleanup(text: String): String {
        var normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u3000', ' ')
        normalized = normalized.replace(ZERO_WIDTH_RE, "")
        val cleaned = StringBuilder()
        for (ch in normalized) {
            if (ch == '\n' || ch == '\t' || ch == ' ') {
                cleaned.append(ch)
            } else if (!isControlCharacter(ch)) {
                cleaned.append(ch)
            }
        }
        return cleaned.toString()
    }

    private fun isControlCharacter(ch: Char): Boolean {
        val type = Character.getType(ch).toByte()
        return type == Character.CONTROL || type == Character.FORMAT
    }

    private fun isUnicodePunctuation(ch: Char): Boolean {
        val type = Character.getType(ch).toByte()
        return type in byteArrayOf(
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            Character.MATH_SYMBOL,
            Character.CURRENCY_SYMBOL
        )
    }

    private fun ensureTerminalPunctuation(text: String): String {
        if (text.isEmpty()) return text
        var index = text.length - 1
        while (index >= 0 && text[index].isWhitespace()) index--
        while (index >= 0 && text[index] in TRAILING_CLOSERS) index--
        if (index >= 0 && isUnicodePunctuation(text[index])) return text
        return "$text。"
    }

    private fun ensureTerminalPunctuationByLine(text: String): String {
        if (text.isEmpty()) return text
        return text.split('\n').joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) ensureTerminalPunctuation(trimmed) else ""
        }.trim()
    }

    private fun normalizeMarkdownAndLines(text: String): String {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""

        val cleaned = lines.map { line ->
            var l = line
            l = l.replace(Regex("^#{1,6}\\s+"), "")
            l = l.replace(Regex("^>\\s+"), "")
            l = l.replace(Regex("^[-*+]\\s+"), "")
            l = l.replace(Regex("^\\d+[.)]\\s+"), "")
            // Markdown links: [text](url) -> text url
            l = l.replace(Regex("\\[([^\\[\\]]+?)]\\((https?://[^)\\s]+)\\)"), "$1 $2")
            l
        }

        val merged = mutableListOf(cleaned[0])
        for (i in 1 until cleaned.size) {
            merged[merged.lastIndex] = ensureTerminalPunctuation(merged.last())
            merged.add(cleaned[i])
        }
        return merged.joinToString("")
    }

    private fun normalizeFlowArrows(text: String): String {
        return text.replace(
            Regex("\\s*(?:<[-=]+>|[-=]+>|<[-=]+|[→←↔⇒⇐⇔⟶⟵⟷⟹⟸⟺↦↤↪↩])\\s*"),
            "，"
        )
    }

    private fun protectSpans(text: String, spans: MutableList<String>): String {
        val patterns = listOf(URL_RE, EMAIL_RE, MENTION_RE, HASHTAG_RE)
        var result = text
        for (pattern in patterns) {
            result = pattern.replace(result) { match ->
                val token = "___PROT${spans.size}___"
                spans.add(match.value)
                token
            }
        }
        return result
    }

    private fun restoreSpans(text: String, spans: List<String>): String {
        var result = text
        for (i in spans.indices) {
            result = result.replace("___PROT${i}___", spans[i])
        }
        return result
    }

    private fun normalizeVisibleUnderscores(text: String): String {
        return text.split(Regex("(___PROT\\d+___)")).joinToString("") { part ->
            if (part.matches(Regex("___PROT\\d+___"))) part else part.replace('_', ' ')
        }
    }

    private fun normalizeSpaces(text: String): String {
        var n = text
        // Collapse horizontal whitespace
        n = n.replace(Regex("[ \\t\\r\\f\\v]+"), " ")
        // Remove spaces between CJK chars
        n = n.replace(Regex("([\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])\\s+(?=[\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])"), "$1")
        n = n.replace(Regex("([\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])\\s+(?=\\d)"), "$1")
        n = n.replace(Regex("(\\d)\\s+(?=[\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])"), "$1")
        // Space before/after CJK + Latin
        n = n.replace(Regex("([\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])(?=[A-Za-z])"), "$1 ")
        n = n.replace(Regex("([A-Za-z0-9._/+:\\-])(?=[\\u3400-\\u4dbf\\u4e00-\\u9fff\\u3040-\\u30ff])"), "$1 ")
        // Collapse double spaces
        n = n.replace(Regex(" {2,}"), " ")
        // Remove spaces before CJK punctuation
        n = n.replace(Regex("\\s+([，。！？；：、\"'」』】）》])"), "$1")
        n = n.replace(Regex("([（【「『《\"'])\\s+"), "$1")
        n = n.replace(Regex("([，。！？；：、])\\s*"), "$1")
        n = n.replace(Regex("\\s+([,.;!?])"), "$1")
        return n.replace(Regex(" {2,}"), " ").trim()
    }

    private fun normalizeStructuralPunctuation(text: String): String {
        var n = text
        n = n.replace(Regex("\\[\\s*([^\\[\\]]+?)\\s*]"), "\"$1\"")
        n = n.replace(Regex("[{]\\s*([^{}]+?)\\s*[}]"), "\"$1\"")
        n = n.replace(Regex("[【〖『「]\\s*([^】〗』」]+?)\\s*[】〗』」]"), "\"$1\"")
        n = normalizeFlowArrows(n)
        n = n.replace(Regex("\\s*(?:—|–|―|-){2,}\\s*"), "。")
        return n
    }

    private fun normalizeRepeatedPunctuation(text: String): String {
        var n = text
        n = n.replace(Regex("(?:\\.{3,}|…{2,}|……+)"), "。")
        n = n.replace(Regex("[。．]{2,}"), "。")
        n = n.replace(Regex("[，,]{2,}"), "，")
        n = n.replace(Regex("[!！]{2,}"), "！")
        n = n.replace(Regex("[?？]{2,}"), "？")
        return n
    }

    /**
     * 按 token 预算拆分长文本。使用简单的字符级估算（约 1.5 字符/token）
     * 作为 SentencePiece 的近似，实际精确拆分需要 tokenizer。
     */
    fun splitByApproxTokenBudget(text: String, maxTokens: Int): List<String> {
        val approxCharsPerToken = 1.5
        val maxChars = (maxTokens * approxCharsPerToken).toInt()
        if (text.length <= maxChars) return listOf(text)

        val pieces = mutableListOf<String>()
        var remaining = text.trim()
        val boundaryChars = setOf('。', '！', '？', '；', '，', '.', '!', '?', ';', ',', ' ')

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxChars) {
                pieces.add(remaining)
                break
            }
            // Find a good cut point
            val window = remaining.take(maxChars)
            var cutIndex = window.length
            for (i in window.length - 1 downTo maxOf(0, window.length - 30)) {
                if (window[i] in boundaryChars) {
                    cutIndex = i + 1
                    break
                }
            }
            val piece = remaining.take(cutIndex).trim()
            if (piece.isNotEmpty()) {
                pieces.add(piece)
            }
            remaining = remaining.drop(cutIndex).trim()
        }
        return pieces
    }
}
