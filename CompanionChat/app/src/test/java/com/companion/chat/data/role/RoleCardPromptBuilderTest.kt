package com.companion.chat.data.role

import com.companion.chat.data.local.entity.RoleCard
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleCardPromptBuilderTest {

    @Test
    fun `会把角色核心字段拼进 prompt`() {
        val roleCard = RoleCard(
            id = 1L,
            name = "小夏",
            description = "日常陪伴角色",
            avatar = "person",
            persona = "温柔可靠的陪伴者",
            speakingStyle = "轻松自然，不端着",
            background = "熟悉用户的日常情绪变化",
            rules = "优先共情，再给建议",
            taboos = "不要说教和命令式表达",
            openingMessage = "今天想聊点什么？",
            exampleDialogue = "用户：我今天有点累。 角色：那先缓一缓，我陪你慢慢说。",
            imageStylePrompt = "清爽明亮的日常插画",
            voiceProfileUri = "file:///voice.wav",
            createdAt = 0L,
            updatedAt = 0L
        )

        val prompt = RoleCardPromptBuilder().build(roleCard)

        assertTrue(prompt.contains("小夏"))
        assertTrue(prompt.contains("温柔可靠的陪伴者"))
        assertTrue(prompt.contains("轻松自然，不端着"))
        assertTrue(prompt.contains("优先共情，再给建议"))
        assertTrue(prompt.contains("不要说教和命令式表达"))
        assertTrue(prompt.contains("清爽明亮的日常插画"))
        assertTrue(!prompt.contains("voice.wav"))
    }
}
