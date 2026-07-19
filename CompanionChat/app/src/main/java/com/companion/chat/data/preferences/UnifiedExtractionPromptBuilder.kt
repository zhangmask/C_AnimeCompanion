package com.companion.chat.data.preferences

import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole

/**
 * 统一提取 Prompt 构建器。
 *
 * 改造后扩展输出格式：
 * - memories（原有）
 * - user_preferences（原有）
 * - entities（[v2 新增] 实体列表）
 * - links（[v2 新增] 链接关系）
 * - metaMemories（[v2 新增] 元记忆）
 */
class UnifiedExtractionPromptBuilder {

    fun buildPrompt(messages: List<ChatMessage>): String {
        val conversationText = messages
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .joinToString(separator = "\n") { message ->
            val speaker = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "[$speaker]: ${message.content}"
        }

        return buildString {
            appendLine("你的任务：从下面最近 5 轮对话中，直接提取并输出一个 JSON 对象。")
            appendLine("必须且只能输出 JSON，不要输出任何解释、思考、分析、Markdown 代码块或额外文字。")
            appendLine("输出格式：")
            appendLine("""{"memories":[...],"user_preferences":[...],"entities":[...],"metaMemories":[...]}""")
            appendLine()
            appendLine("1. memories：用户相关的个人记忆")
            appendLine("2. user_preferences：影响回答方式的偏好")
            appendLine("3. entities：提取到的实体（人物、话题、概念）")
            appendLine("4. metaMemories：关于如何使用记忆的策略（可选）")
            appendLine()
            appendLine("### 1. memories.category 可以是：")
            appendLine("fact / preference / event / behavior / knowledge / skill / relation / time / other")
            appendLine()
            appendLine("### 2. user_preferences.category 固定：")
            appendLine("name / style / interest / habit / other")
            appendLine()
            appendLine("### 3. entities 格式：")
            appendLine("""{"name":"实体名","type":"person/org/topic/concept"}""")
            appendLine("entities 用于提取对话中出现的独立实体，如人名、组织名、话题名。")
            appendLine("每个 memories 项可以通过 entityName 字段关联到实体。")
            appendLine()
            appendLine("### 4. metaMemories 格式（可选）：")
            appendLine("""{"content":"策略描述","category":"retrieval/reasoning/conflict"}""")
            appendLine("元记忆是'如何使用记忆'的通用策略,约 30 词以内的单句指导.")
            appendLine("示例：")
            appendLine("""{"content":"当记忆包含时间信息时，优先使用最新数据","category":"retrieval"}""")
            appendLine("""{"content":"同一主题有矛盾记忆时，列出两个版本让用户选择","category":"conflict"}""")
            appendLine()
            appendLine("### 提取规则：")
            appendLine("- 宁可漏记也不要错记。只在用户明确表达个人信息时才提取。")
            appendLine("- 只提取与用户本人相关的长期稳定信息，不要提取通用知识、事实定义、百科内容。")
            appendLine("- 如果用户在问知识问题（如「勾股定理是什么」），这是知识查询，不要提取。")
            appendLine("- 临时闲聊、一次性行为、随口一句话不值得记忆。只有反复出现的偏好、明确的自我描述才值得提取。")
            appendLine("- 用户明确说出的兴趣、喜欢/不喜欢、习惯、时间规律、性格特征、自我描述、禁忌、回答偏好，通常都应该提取。")
            appendLine("- content 使用简短短语，不要复述整句。")
            appendLine("- 如果某一部分没有内容，对应数组输出 []。")
            appendLine("- 对于「我喜欢什么 / 我不喜欢什么 / 我一般怎么样 / 我通常怎样 / 我比较怎样 / 以后请怎样回答」这类表达，应优先视为高价值信息。")
            appendLine()
            appendLine("### 常见可提取示例：")
            appendLine("- 我喜欢科幻和游戏")
            appendLine("- 我不喜欢太官方的回答")
            appendLine("- 我一般晚上十点后聊天")
            appendLine("- 我比较慢热")
            appendLine("- 以后请尽量直接一点，多举例")
            appendLine()
            appendLine("### entities 提取示例：")
            appendLine("- 张三 → {\"name\":\"张三\",\"type\":\"person\"}")
            appendLine("- 篮球 → {\"name\":\"篮球\",\"type\":\"topic\"}")
            appendLine()
            appendLine("### 输出示例（直接输出这个格式的 JSON，不要输出其他内容）：")
            appendLine("{\"memories\":[{\"category\":\"preference\",\"content\":\"喜欢科幻\"},{\"category\":\"habit\",\"content\":\"晚上十点后聊天\"}],\"user_preferences\":[{\"category\":\"interest\",\"content\":\"科幻\"},{\"category\":\"habit\",\"content\":\"晚睡\"}],\"entities\":[{\"name\":\"科幻\",\"type\":\"topic\"}],\"metaMemories\":[]}")
            appendLine()
            appendLine("对话内容：")
            append(conversationText)
            appendLine()
            appendLine("请直接输出 JSON：")
        }
    }
}
