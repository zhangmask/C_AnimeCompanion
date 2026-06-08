package com.companion.chat.data.preferences

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole

class PreferenceSummaryPromptBuilder {

    fun buildPrompt(messages: List<ChatMessage>): String {
        val recentMessages = messages
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .takeLast(MAX_MESSAGE_COUNT)

        val conversationText = recentMessages.joinToString(separator = "\n") { message ->
            val speaker = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$speaker]: ${message.content}"
        }

        return buildString {
            appendLine("你是一个严格的用户偏好提取器。")
            appendLine("你的任务是从下面最近 5 轮对话中提取值得长期记住的用户偏好。")
            appendLine("只输出一个 JSON 数组，不要输出解释、标题、Markdown 代码块或任何额外文字。")
            appendLine("每个对象必须只包含 category 和 content 两个字段。")
            appendLine()
            appendLine("类别包括：")
            appendLine("- name: 用户称呼/名字")
            appendLine("- style: 回答风格偏好（简洁/详细/幽默等）")
            appendLine("- interest: 兴趣领域")
            appendLine("- habit: 使用习惯")
            appendLine("- other: 其他值得记住的信息")
            appendLine()
            appendLine("提取规则：")
            appendLine("- 只提取用户明确表达的信息，不要猜测。")
            appendLine("- content 使用简短短语，不要复述整句。")
            appendLine("- 如果没有值得记录的偏好，输出 []。")
            appendLine("- 示例输出: [{\"category\":\"style\",\"content\":\"喜欢简洁回答\"}]")
            appendLine()
            appendLine("对话内容：")
            append(conversationText)
        }
    }

    companion object {
        private const val MAX_MESSAGE_COUNT = 10
    }
}
