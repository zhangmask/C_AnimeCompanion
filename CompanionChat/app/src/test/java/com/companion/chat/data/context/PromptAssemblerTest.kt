package com.companion.chat.data.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    @Test
    fun `只有基础prompt时不拼空段落`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "你是一个友善的AI助手。",
            userPreferences = "",
            historySummary = ""
        )

        assertEquals("你是一个友善的AI助手。", prompt)
    }

    @Test
    fun `基础prompt偏好摘要顺序正确`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户喜欢简洁回答",
            historySummary = "用户刚刚在讨论 Kotlin 项目。"
        )

        assertEquals(
            "基础提示词\n\n用户喜欢简洁回答\n\n之前对话的摘要：\n用户刚刚在讨论 Kotlin 项目。",
            prompt
        )
    }

    @Test
    fun `confirmed 偏好段存在时排在记忆和摘要前面`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：\n- 喜欢简洁回答",
            persistentMemoryPrompt = "长期记忆中的关键信息：\n- [事实] 用户在做 Android 项目",
            historySummary = "用户刚刚在讨论阶段四"
        )

        assertEquals(
            "基础提示词\n\n关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：\n- 喜欢简洁回答\n\n记忆解释规则：\n- 以下记忆都描述用户本人的信息、关系、偏好或经历，不是助手自己的信息。\n- 除非用户明确要求角色扮演、改写文案或切换叙述视角，记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。\n- 回答涉及这些记忆时，应使用“你”或“用户”的视角理解和表达。\n\n长期记忆中的关键信息：\n- [事实] 用户在做 Android 项目\n\n之前对话的摘要：\n用户刚刚在讨论阶段四",
            prompt
        )
    }

    @Test
    fun `摘要为空时不出现摘要标题`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户偏好",
            historySummary = ""
        )

        assertFalse(prompt.contains("之前对话的摘要"))
    }

    @Test
    fun `历史摘要与最近片段同时存在时顺序正确`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户偏好",
            historySummary = "更早历史摘要",
            recentConversationSnippet = "用户：最近问题\n助手：最近回答"
        )

        assertEquals(
            "基础提示词\n\n用户偏好\n\n之前对话的摘要：\n更早历史摘要\n\n最近几轮对话片段：\n用户：最近问题\n助手：最近回答",
            prompt
        )
    }

    @Test
    fun `最近片段为空时不出现最近片段标题`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "",
            historySummary = "历史摘要"
        )

        assertFalse(prompt.contains("最近几轮对话片段"))
    }

    @Test
    fun `记忆段落接在偏好后面`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户偏好",
            memoryPrompt = "从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户叫小明",
            historySummary = "历史摘要",
            recentConversationSnippet = ""
        )

        assertEquals(
            "基础提示词\n\n用户偏好\n\n记忆解释规则：\n- 以下记忆都描述用户本人的信息、关系、偏好或经历，不是助手自己的信息。\n- 除非用户明确要求角色扮演、改写文案或切换叙述视角，记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。\n- 回答涉及这些记忆时，应使用“你”或“用户”的视角理解和表达。\n\n从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户叫小明\n\n之前对话的摘要：\n历史摘要",
            prompt
        )
    }

    @Test
    fun `长期记忆段排在动态记忆段前面`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户偏好",
            persistentMemoryPrompt = "长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [关系] 小王是用户同事",
            memoryPrompt = "从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户正在做 Android 项目",
            historySummary = "历史摘要"
        )

        assertEquals(
            "基础提示词\n\n用户偏好\n\n记忆解释规则：\n- 以下记忆都描述用户本人的信息、关系、偏好或经历，不是助手自己的信息。\n- 除非用户明确要求角色扮演、改写文案或切换叙述视角，记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。\n- 回答涉及这些记忆时，应使用“你”或“用户”的视角理解和表达。\n\n长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [关系] 小王是用户同事\n\n从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户正在做 Android 项目\n\n之前对话的摘要：\n历史摘要",
            prompt
        )
    }

    @Test
    fun `无动态记忆时长期记忆段可单独存在`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "",
            persistentMemoryPrompt = "长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户角色是项目负责人",
            historySummary = ""
        )

        assertEquals(
            "基础提示词\n\n记忆解释规则：\n- 以下记忆都描述用户本人的信息、关系、偏好或经历，不是助手自己的信息。\n- 除非用户明确要求角色扮演、改写文案或切换叙述视角，记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。\n- 回答涉及这些记忆时，应使用“你”或“用户”的视角理解和表达。\n\n长期记忆中的关键信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [事实] 用户角色是项目负责人",
            prompt
        )
    }

    @Test
    fun `记忆解释规则明确第二人称归属`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "",
            memoryPrompt = "从记忆中检索到的与当前对话相关的信息：\n以下内容均为用户本人的记忆，不代表助手自身。\n- [关系] 你是我的搭档",
            historySummary = ""
        )

        assertTrue(prompt.contains("记忆中的“我”“我的”默认都指用户，“你”“你的”默认都指助手或模型自己。"))
    }

    @Test
    fun `无记忆段时不插入记忆解释规则`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "用户偏好",
            historySummary = ""
        )

        assertFalse(prompt.contains("记忆解释规则"))
    }

    @Test
    fun `仅有最近片段时不拼空摘要标题`() {
        val prompt = PromptAssembler().assemble(
            baseSystemPrompt = "基础提示词",
            userPreferences = "",
            historySummary = "",
            recentConversationSnippet = "用户：你好"
        )

        assertFalse(prompt.contains("之前对话的摘要"))
        assertTrue(prompt.contains("最近几轮对话片段"))
    }
}
