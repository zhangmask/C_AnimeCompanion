package com.companion.chat.data.preferences

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole

class UnifiedExtractionPromptBuilder {

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
            appendLine("你是一个严格的用户信息提取器。")
            appendLine("请从下面最近 5 轮对话中，同时提取 memories 和 user_preferences。")
            appendLine("只输出一个 JSON 对象，不要输出解释、标题、Markdown 代码块或任何额外文字。")
            appendLine("输出格式必须严格为：")
            appendLine("""{"memories":[{"category":"fact","content":"..."}],"user_preferences":[{"category":"style","content":"..."}]}""")
            appendLine()
            appendLine("memories.category 固定只能是：fact / preference / event / relation / time / other")
            appendLine("user_preferences.category 固定只能是：name / style / interest / habit / other")
            appendLine()
            appendLine("提取规则：")
            appendLine("- 只提取用户明确表达的信息，不要猜测。")
            appendLine("- memories 用于记录用户相关事实、偏好、事件、关系、时间和其他长期信息。")
            appendLine("- user_preferences 只保留可稳定影响后续回答方式或用户画像的信息。")
            appendLine("- 同一条信息允许同时进入 memories 和 user_preferences，只要两边都合理。")
            appendLine("- 用户明确说出的兴趣、喜欢/不喜欢、习惯、时间规律、性格特征、自我描述、禁忌、回答偏好，通常都应该提取。")
            appendLine("- 如果一句话里包含多条稳定信息，尽量拆成多条分别提取，不要只保留一条。")
            appendLine("- content 使用简短短语，不要复述整句。")
            appendLine("- 如果某一部分没有内容，对应数组输出 []。")
            appendLine("- 对于“我喜欢什么 / 我不喜欢什么 / 我一般怎么样 / 我通常怎样 / 我比较怎样 / 以后请怎样回答”这类表达，应优先视为高价值信息。")
            appendLine()
            appendLine("常见可提取示例：")
            appendLine("- 我喜欢科幻和游戏")
            appendLine("- 我不喜欢太官方的回答")
            appendLine("- 我一般晚上十点后聊天")
            appendLine("- 我比较慢热")
            appendLine("- 以后请尽量直接一点，多举例")
            appendLine()
            appendLine("对话内容：")
            append(conversationText)
        }
    }

    companion object {
        private const val MAX_MESSAGE_COUNT = 10
    }
}
