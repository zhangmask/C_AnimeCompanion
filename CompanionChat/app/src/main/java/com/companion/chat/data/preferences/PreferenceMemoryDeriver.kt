package com.companion.chat.data.preferences

import com.companion.chat.data.memory.ExtractedMemory
import com.companion.chat.data.memory.MemoryRepository

class PreferenceMemoryDeriver {

    fun derive(preferences: List<ExtractedPreference>): List<ExtractedMemory> {
        return preferences.mapNotNull { preference ->
            val content = preference.content.trim()
            if (content.isBlank()) {
                return@mapNotNull null
            }

            when (preference.category.trim().lowercase()) {
                "name" -> createMemory(
                    category = "fact",
                    content = ensureUserPrefix(content, fallbackPrefix = "用户叫")
                )
                "style" -> createMemory(
                    category = "preference",
                    content = ensureUserPrefix(
                        content = content,
                        fallbackPrefix = "用户偏好"
                    )
                )
                "interest" -> createMemory(
                    category = "preference",
                    content = normalizeInterestContent(content)
                )
                "habit" -> createMemory(
                    category = if (looksLikeTimeHabit(content)) "time" else "other",
                    content = normalizeHabitContent(content)
                )
                "other" -> createMemory(
                    category = "other",
                    content = normalizeOtherContent(content)
                )
                else -> null
            }
        }
    }

    private fun createMemory(category: String, content: String): ExtractedMemory {
        return ExtractedMemory(
            content = content,
            category = category,
            layer = "short_term",
            source = MemoryRepository.MODEL_SOURCE
        )
    }

    private fun ensureUserPrefix(content: String, fallbackPrefix: String): String {
        return when {
            content.startsWith("用户") -> content
            fallbackPrefix == "用户" -> "用户$content"
            else -> "$fallbackPrefix$content"
        }
    }

    private fun normalizeInterestContent(content: String): String {
        return when {
            content.startsWith("喜欢") ||
                content.startsWith("热爱") ||
                content.startsWith("偏爱") ||
                content.startsWith("不喜欢") ||
                content.startsWith("讨厌") ||
                content.startsWith("不爱") -> "用户$content"
            else -> "用户喜欢$content"
        }
    }

    private fun normalizeHabitContent(content: String): String {
        return if (looksLikeTimeHabit(content)) {
            ensureUserPrefix(
                content = content,
                fallbackPrefix = "用户"
            )
        } else {
            when {
                content.startsWith("比较") ||
                    content.startsWith("通常") ||
                    content.startsWith("经常") ||
                    content.startsWith("平时") ||
                    content.startsWith("一般") ||
                    content.startsWith("会") -> ensureUserPrefix(
                    content = content,
                    fallbackPrefix = "用户"
                )
                else -> ensureUserPrefix(
                    content = content,
                    fallbackPrefix = "用户习惯"
                )
            }
        }
    }

    private fun normalizeOtherContent(content: String): String {
        return when {
            content.startsWith("比较") ||
                content.startsWith("很") ||
                content.startsWith("挺") ||
                content.startsWith("有点") ||
                content.startsWith("容易") ||
                content.startsWith("偏") ||
                content.startsWith("是个") -> ensureUserPrefix(
                content = content,
                fallbackPrefix = "用户"
            )
            else -> ensureUserPrefix(
                content = content,
                fallbackPrefix = "用户"
            )
        }
    }

    private fun looksLikeTimeHabit(content: String): Boolean {
        return TIME_HINTS.any { hint -> content.contains(hint) }
    }

    companion object {
        private val TIME_HINTS = listOf(
            "早上", "上午", "中午", "下午", "晚上", "凌晨",
            "十点", "十一点", "十二点", "点后", "点前",
            "周一", "周二", "周三", "周四", "周五", "周末",
            "每天", "每日", "工作日", "放假", "睡前", "起床后"
        )
    }
}
