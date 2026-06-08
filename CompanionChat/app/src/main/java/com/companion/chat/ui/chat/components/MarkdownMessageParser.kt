package com.companion.chat.ui.chat.components

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class UnorderedList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
}

internal object MarkdownMessageParser {
    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
    private val unorderedRegex = Regex("^\\s*[-*+]\\s+(.+)$")
    private val orderedRegex = Regex("^\\s*\\d+[.)]\\s+(.+)$")
    private val fenceRegex = Regex("^\\s*```\\s*([^`]*)$")

    fun parse(message: String): List<MarkdownBlock> {
        if (message.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val lines = message.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> index++
                fenceRegex.matches(line) -> {
                    val language = fenceRegex.matchEntire(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    val codeLines = mutableListOf<String>()
                    index++
                    while (index < lines.size && !fenceRegex.matches(lines[index])) {
                        codeLines += lines[index]
                        index++
                    }
                    if (index < lines.size && fenceRegex.matches(lines[index])) {
                        index++
                    }
                    blocks += MarkdownBlock.CodeBlock(language = language, code = codeLines.joinToString("\n"))
                }
                headingRegex.matches(line) -> {
                    val match = headingRegex.matchEntire(line)!!
                    blocks += MarkdownBlock.Heading(
                        level = match.groupValues[1].length,
                        text = match.groupValues[2].trim()
                    )
                    index++
                }
                unorderedRegex.matches(line) -> {
                    val items = mutableListOf<String>()
                    while (index < lines.size) {
                        val match = unorderedRegex.matchEntire(lines[index]) ?: break
                        items += match.groupValues[1].trim()
                        index++
                    }
                    blocks += MarkdownBlock.UnorderedList(items)
                }
                orderedRegex.matches(line) -> {
                    val items = mutableListOf<String>()
                    while (index < lines.size) {
                        val match = orderedRegex.matchEntire(lines[index]) ?: break
                        items += match.groupValues[1].trim()
                        index++
                    }
                    blocks += MarkdownBlock.OrderedList(items)
                }
                line.trimStart().startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                        quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
                        index++
                    }
                    blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n"))
                }
                else -> {
                    val paragraphLines = mutableListOf<String>()
                    while (index < lines.size && shouldContinueParagraph(lines[index])) {
                        paragraphLines += lines[index].trim()
                        index++
                    }
                    blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n"))
                }
            }
        }

        return blocks
    }

    private fun shouldContinueParagraph(line: String): Boolean {
        if (line.isBlank()) return false
        return !fenceRegex.matches(line) &&
            !headingRegex.matches(line) &&
            !unorderedRegex.matches(line) &&
            !orderedRegex.matches(line) &&
            !line.trimStart().startsWith(">")
    }
}
