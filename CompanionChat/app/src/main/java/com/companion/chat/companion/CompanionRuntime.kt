package com.companion.chat.companion

import com.companion.chat.data.context.ContextManager
import com.companion.chat.data.context.ContextSettings
import com.companion.chat.data.context.SystemPromptBuilder
import com.companion.chat.data.context.PromptAssembler
import com.companion.chat.locale.AppLanguage
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import com.companion.chat.data.local.dao.FtsQueryHelper
import com.companion.chat.data.memory.MemoryPromptBuilder
import com.companion.chat.data.memory.MemoryRepository
import com.companion.chat.data.profile.UserProfileRepository
import com.companion.chat.data.role.RoleCardPromptBuilder
import com.companion.chat.data.role.RoleCardRepository
import com.companion.chat.data.preferences.PreferenceRepository
import com.companion.chat.data.skill.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CompanionRuntime(
    private val roleCardRepository: RoleCardRepository,
    private val skillRepository: SkillRepository,
    private val preferenceRepository: PreferenceRepository? = null,
    private val memoryRepository: MemoryRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val contextManager: ContextManager? = null,
    private val inferenceEngineProvider: () -> InferenceEngine? = { null },
    private val postTurnLearning: CompanionPostTurnLearning? = null,
    private val promptAssembler: PromptAssembler = PromptAssembler(),
    private val memoryPromptBuilder: MemoryPromptBuilder = MemoryPromptBuilder(),
    private val roleCardPromptBuilder: RoleCardPromptBuilder = RoleCardPromptBuilder(),
    private val appLanguage: AppLanguage = AppLanguage.DEFAULT,
    private val defaultBasePrompt: String = DEFAULT_BASE_PROMPT
) {
    /** 缓存上次构建的 system prompt，避免每轮都重建 Conversation */
    @Volatile
    private var lastBuiltSystemPrompt: String = ""

    suspend fun refreshBasePrompt(): String {
        val systemPrompt = SystemPromptBuilder.build(appLanguage)
        val rolePrompt = roleCardPromptBuilder.build(roleCardRepository.getActiveRoleCard())
        val skillPrompt = skillRepository.getActiveSkill()?.systemPrompt?.trim().orEmpty()
        val userProfilePrompt = buildUserProfilePrompt()

        return buildList {
            add(defaultBasePrompt)
            if (rolePrompt.isNotBlank()) {
                add(rolePrompt)
            }
            if (skillPrompt.isNotBlank()) {
                add(skillPrompt)
            }
            if (userProfilePrompt.isNotBlank()) {
                add(userProfilePrompt)
            }
        }.joinToString(separator = "\n\n")
    }

    private fun buildUserProfilePrompt(): String {
        val repository = userProfileRepository ?: return ""
        val profile = repository.getProfile()
        val parts = mutableListOf<String>()

        if (profile.nickname.isNotBlank()) {
            parts.add("用户昵称：${profile.nickname}")
        }
        if (profile.gender.isNotBlank()) {
            parts.add("性别：${profile.gender}")
        }
        if (profile.age.isNotBlank()) {
            parts.add("年龄：${profile.age}")
        }
        if (profile.introduction.isNotBlank()) {
            parts.add("个人介绍：${profile.introduction}")
        }
        if (profile.importantInfo.isNotBlank()) {
            parts.add("重要信息：${profile.importantInfo}")
        }
        if (profile.interestTags.isNotBlank()) {
            parts.add("兴趣标签：${profile.interestTags}")
        }

        if (parts.isEmpty()) return ""

        return buildString {
            appendLine("关于当前用户的基本信息（请自然地融入对话，不要刻意提及你知道这些）：")
            parts.forEach { appendLine("- $it") }
        }.trim()
    }

    suspend fun activateRoleCardAndRefreshPrompt(roleCardId: Long): String {
        roleCardRepository.activateRoleCard(roleCardId)
        return refreshBasePrompt()
    }

    suspend fun activateSkillAndRefreshPrompt(skillId: Long): String {
        skillRepository.activateSkill(skillId)
        return refreshBasePrompt()
    }

    suspend fun buildConfirmedPreferencePrompt(roleCardId: Long? = null): String {
        val confirmedPreferences = if (roleCardId != null) {
            preferenceRepository?.getConfirmedPreferencesForRole(roleCardId = roleCardId).orEmpty()
        } else {
            preferenceRepository?.getConfirmedPreferences().orEmpty()
        }
        if (confirmedPreferences.isEmpty()) {
            return ""
        }
        return buildString {
            appendLine("关于当前用户的已知信息（请自然地融入对话，不要刻意提及你知道这些）：")
            confirmedPreferences.forEach { preference ->
                appendLine("- ${preference.content}")
            }
        }.trim()
    }

    suspend fun buildMemoryContext(userInput: String, roleCardId: Long? = null): CompanionMemoryContext {
        val repository = memoryRepository ?: return CompanionMemoryContext()

        // 改造后：使用 FTS 检索替代 getPersistentMemories
        val keywords = extractKeywords(userInput)
        val ftsTerms = if (keywords.isNotEmpty()) {
            keywords.map { "\"${it.replace("\"", "")}\"" }.joinToString(" OR ")
        } else null

        val relevantMemories = if (ftsTerms != null && roleCardId != null) {
            repository.searchByFTSWithRole(ftsTerms, roleCardId, 5)
        } else if (ftsTerms != null) {
            repository.searchByFTS(ftsTerms, 5)
        } else {
            emptyList()
        }

        return CompanionMemoryContext(
            persistentPrompt = memoryPromptBuilder.build(relevantMemories),
            retrievedPrompt = memoryPromptBuilder.build(relevantMemories),
            persistentMemoryCount = relevantMemories.size,
            retrievedMemoryCount = relevantMemories.size
        )
    }

    private fun extractKeywords(userInput: String): List<String> {
        return userInput.lowercase()
            .replace(Regex("[^a-z0-9\\u4E00-\\u9FFF]+"), " ")
            .trim().split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .distinct()
    }

    suspend fun rebuildConversationWithContext(
        stableMessages: List<ChatMessage>,
        baseSystemPrompt: String,
        settings: ContextSettings,
        userPreferences: String = "",
        persistentMemoryPrompt: String = "",
        memoryPrompt: String = "",
        forceRebuild: Boolean = false
    ): CompanionRebuildResult {
        val manager = contextManager ?: return CompanionRebuildResult.skipped()
        val engine = inferenceEngineProvider() ?: return CompanionRebuildResult.skipped()
        val shouldInjectContext = userPreferences.isNotBlank() ||
            persistentMemoryPrompt.isNotBlank() ||
            memoryPrompt.isNotBlank()

        // 先计算新 system prompt，与缓存比较决定是否需要重建
        val contextWindow = if (!forceRebuild && !manager.shouldCompress(stableMessages, settings) && !shouldInjectContext) {
            // 无压缩需求且无上下文注入，跳过
            return CompanionRebuildResult.skipped()
        } else {
            manager.buildContext(
                messages = stableMessages,
                systemPrompt = baseSystemPrompt,
                userPreferences = userPreferences,
                persistentMemoryPrompt = persistentMemoryPrompt,
                memoryPrompt = memoryPrompt,
                settings = settings
            )
        }

        // 如果 system prompt 未变化且不需要压缩，跳过重建
        val needsCompress = manager.shouldCompress(stableMessages, settings)
        if (!forceRebuild && !needsCompress && contextWindow.systemPrompt == lastBuiltSystemPrompt) {
            return CompanionRebuildResult.skipped()
        }

        val rebuildSucceeded = engine.rebuildConversation(contextWindow.systemPrompt)
        if (!rebuildSucceeded) {
            return CompanionRebuildResult(
                rebuildAttempted = true,
                rebuildSucceeded = false,
                replaySucceeded = null,
                fallbackSucceeded = null,
                recentMessageCount = contextWindow.recentMessages.size,
                historySummaryEmpty = contextWindow.historySummary.isBlank(),
                preferenceInjected = contextWindow.userPreferences.isNotBlank(),
                persistentMemoryInjected = contextWindow.persistentMemoryPrompt.isNotBlank(),
                memoryInjected = contextWindow.memoryPrompt.isNotBlank()
            )
        }

        val replaySucceeded = engine.replayMessages(contextWindow.recentMessages)
        if (replaySucceeded) {
            lastBuiltSystemPrompt = contextWindow.systemPrompt
            return CompanionRebuildResult(
                rebuildAttempted = true,
                rebuildSucceeded = true,
                replaySucceeded = true,
                fallbackSucceeded = null,
                recentMessageCount = contextWindow.recentMessages.size,
                historySummaryEmpty = contextWindow.historySummary.isBlank(),
                preferenceInjected = contextWindow.userPreferences.isNotBlank(),
                persistentMemoryInjected = contextWindow.persistentMemoryPrompt.isNotBlank(),
                memoryInjected = contextWindow.memoryPrompt.isNotBlank()
            )
        }

        val fallbackPrompt = promptAssembler.assemble(
            baseSystemPrompt = contextWindow.systemPrompt,
            userPreferences = "",
            persistentMemoryPrompt = "",
            memoryPrompt = "",
            historySummary = "",
            recentConversationSnippet = buildRecentConversationSnippet(contextWindow.recentMessages)
        )
        val fallbackSucceeded = engine.rebuildConversationWithFallbackContext(fallbackPrompt)
        return CompanionRebuildResult(
            rebuildAttempted = true,
            rebuildSucceeded = true,
            replaySucceeded = false,
            fallbackSucceeded = fallbackSucceeded,
            recentMessageCount = contextWindow.recentMessages.size,
            historySummaryEmpty = contextWindow.historySummary.isBlank(),
            preferenceInjected = contextWindow.userPreferences.isNotBlank(),
            persistentMemoryInjected = contextWindow.persistentMemoryPrompt.isNotBlank(),
            memoryInjected = contextWindow.memoryPrompt.isNotBlank()
        )
    }

    private fun buildRecentConversationSnippet(messages: List<ChatMessage>): String {
        return messages.mapNotNull { message ->
            val content = message.content.trim()
            if (content.isBlank()) {
                return@mapNotNull null
            }

            val roleLabel = when (message.role) {
                MessageRole.USER -> "用户"
                MessageRole.ASSISTANT -> "助手"
                MessageRole.SYSTEM -> "系统"
            }
            "$roleLabel：${content.take(80)}"
        }.joinToString(separator = "\n")
    }

    fun onTurnFinished(
        sessionIdProvider: () -> String,
        messagesProvider: () -> List<ChatMessage>
    ) {
        postTurnLearning?.scheduleAfterIdle(
            sessionIdProvider = sessionIdProvider,
            messagesProvider = messagesProvider
        )
    }

    fun onConversationBoundary(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>
    ) {
        postTurnLearning?.triggerNow(
            reason = reason,
            sessionId = sessionId,
            messages = messages
        )
    }

    fun cancelPostTurnLearning() {
        postTurnLearning?.cancelRunningSummary()
    }

    fun release() {
        postTurnLearning?.release()
    }

    fun runTurn(
        messages: List<ChatMessage>,
        baseSystemPrompt: String,
        settings: ContextSettings,
        userPreferences: String = "",
        persistentMemoryPrompt: String = "",
        memoryPrompt: String = ""
    ): Flow<CompanionTurnEvent> = flow {
        val engine = inferenceEngineProvider() ?: return@flow
        val stableMessages = messages.filterNot { it.isStreaming }
        rebuildConversationWithContext(
            stableMessages = stableMessages,
            baseSystemPrompt = baseSystemPrompt,
            settings = settings,
            userPreferences = userPreferences,
            persistentMemoryPrompt = persistentMemoryPrompt,
            memoryPrompt = memoryPrompt,
            forceRebuild = false
        )
        engine.sendMessageStream(messages).collect { token ->
            emit(CompanionTurnEvent.AssistantToken(token))
        }
    }

    companion object {
        /** @deprecated Use [SystemPromptBuilder.build] instead. */
        val DEFAULT_BASE_PROMPT: String = SystemPromptBuilder.build(AppLanguage.DEFAULT)
    }
}

sealed class CompanionTurnEvent {
    data class AssistantToken(val token: String) : CompanionTurnEvent()
}

interface CompanionPostTurnLearning {
    fun scheduleAfterIdle(
        sessionIdProvider: () -> String,
        messagesProvider: () -> List<ChatMessage>
    )

    fun triggerNow(
        reason: String,
        sessionId: String,
        messages: List<ChatMessage>
    )

    fun cancelRunningSummary()

    fun release()
}

data class CompanionMemoryContext(
    val persistentPrompt: String = "",
    val retrievedPrompt: String = "",
    val persistentMemoryCount: Int = 0,
    val retrievedMemoryCount: Int = 0
)

data class CompanionRebuildResult(
    val rebuildAttempted: Boolean,
    val rebuildSucceeded: Boolean?,
    val replaySucceeded: Boolean?,
    val fallbackSucceeded: Boolean?,
    val recentMessageCount: Int = 0,
    val historySummaryEmpty: Boolean = true,
    val preferenceInjected: Boolean = false,
    val persistentMemoryInjected: Boolean = false,
    val memoryInjected: Boolean = false
) {
    companion object {
        fun skipped(): CompanionRebuildResult {
            return CompanionRebuildResult(
                rebuildAttempted = false,
                rebuildSucceeded = null,
                replaySucceeded = null,
                fallbackSucceeded = null
            )
        }
    }
}
