package com.companion.chat.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownMessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { MarkdownMessageParser.parse(text) }
    if (blocks.isEmpty()) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> CodeBlockText(block = block, color = color)
                is MarkdownBlock.Heading -> InlineMarkdownText(
                    text = block.text,
                    color = color,
                    style = headingStyle(block.level)
                )
                is MarkdownBlock.OrderedList -> ListBlock(
                    items = block.items,
                    color = color,
                    ordered = true
                )
                is MarkdownBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge
                )
                is MarkdownBlock.Quote -> QuoteBlock(block = block, color = color)
                is MarkdownBlock.UnorderedList -> ListBlock(
                    items = block.items,
                    color = color,
                    ordered = false
                )
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text = remember(text, color) { buildInlineMarkdown(text, color) },
        color = color,
        style = style,
        modifier = modifier
    )
}

@Composable
private fun CodeBlockText(block: MarkdownBlock.CodeBlock, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        block.language?.let {
            Text(
                text = it,
                color = color.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = block.code,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ListBlock(
    items: List<String>,
    color: Color,
    ordered: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    color = color.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge
                )
                InlineMarkdownText(
                    text = item,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote, color: Color) {
    Text(
        text = buildInlineMarkdown(block.text, color.copy(alpha = 0.82f)),
        color = color.copy(alpha = 0.82f),
        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    return when (level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }.copy(fontWeight = FontWeight.SemiBold)
}

private fun buildInlineMarkdown(text: String, color: Color): AnnotatedString {
    val patterns = listOf(
        InlinePattern(Regex("\\[([^\\]]+)]\\(([^)]+)\\)")) { match ->
            InlineSegment(
                text = match.groupValues[1],
                style = SpanStyle(
                    color = color,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            )
        },
        InlinePattern(Regex("`([^`]+)`")) { match ->
            InlineSegment(
                text = match.groupValues[1],
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = color.copy(alpha = 0.10f)
                )
            )
        },
        InlinePattern(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) { match ->
            InlineSegment(
                text = match.groupValues[1].ifEmpty { match.groupValues[2] },
                style = SpanStyle(fontWeight = FontWeight.Bold)
            )
        },
        InlinePattern(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")) { match ->
            InlineSegment(
                text = match.groupValues[1].ifEmpty { match.groupValues[2] },
                style = SpanStyle(fontStyle = FontStyle.Italic)
            )
        }
    )

    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val next = patterns
                .mapNotNull { pattern ->
                    pattern.regex.find(text, startIndex = index)?.let { match -> pattern to match }
                }
                .minByOrNull { it.second.range.first }

            if (next == null) {
                append(text.substring(index))
                break
            }

            val (pattern, match) = next
            if (match.range.first > index) {
                append(text.substring(index, match.range.first))
            }
            val segment = pattern.toSegment(match)
            pushStyle(segment.style)
            append(segment.text)
            pop()
            index = match.range.last + 1
        }
    }
}

private data class InlinePattern(
    val regex: Regex,
    val toSegment: (MatchResult) -> InlineSegment
)

private data class InlineSegment(
    val text: String,
    val style: SpanStyle
)
