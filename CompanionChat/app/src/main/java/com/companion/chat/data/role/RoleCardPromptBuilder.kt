package com.companion.chat.data.role

import com.companion.chat.data.local.entity.RoleCard

class RoleCardPromptBuilder {

    fun build(roleCard: RoleCard?): String {
        if (roleCard == null) {
            return ""
        }

        val sections = buildList {
            add("当前激活角色卡设定：")
            add("角色名称：${roleCard.name}")
            if (roleCard.description.isNotBlank()) {
                add("角色简介：${roleCard.description.trim()}")
            }
            add("核心人设：${roleCard.persona.trim()}")
            if (roleCard.speakingStyle.isNotBlank()) {
                add("说话风格：${roleCard.speakingStyle.trim()}")
            }
            if (roleCard.background.isNotBlank()) {
                add("背景设定：${roleCard.background.trim()}")
            }
            if (roleCard.rules.isNotBlank()) {
                add("行为规则：${roleCard.rules.trim()}")
            }
            if (roleCard.taboos.isNotBlank()) {
                add("禁止项：${roleCard.taboos.trim()}")
            }
            if (roleCard.openingMessage.isNotBlank()) {
                add("开场白参考：${roleCard.openingMessage.trim()}")
            }
            if (roleCard.exampleDialogue.isNotBlank()) {
                add("示例对话参考：${roleCard.exampleDialogue.trim()}")
            }
            if (roleCard.imageStylePrompt.isNotBlank()) {
                add("视觉风格参考：${roleCard.imageStylePrompt.trim()}")
            }
            add("以上角色设定具有最高优先级。无论用户问什么问题，你都必须始终维持该角色身份回答，不要说自己是无差别的 AI 助手或通用助手。")
            add("对话方式：先回应再分享，偶尔好奇，不要每句都追问，不要主动科普，回答简洁。")
        }

        return sections.joinToString(separator = "\n")
    }
}
