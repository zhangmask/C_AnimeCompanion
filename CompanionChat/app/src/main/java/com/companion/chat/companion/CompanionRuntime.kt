package com.companion.chat.companion

import com.companion.chat.data.context.ContextManager
import com.companion.chat.data.context.ContextSettings
import com.companion.chat.data.context.PromptAssembler
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
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
    private val defaultBasePrompt: String = DEFAULT_BASE_PROMPT
) {
    /** 缓存上次构建的 system prompt，避免每轮都重建 Conversation */
    @Volatile
    private var lastBuiltSystemPrompt: String = ""

    suspend fun refreshBasePrompt(): String {
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

    suspend fun buildConfirmedPreferencePrompt(): String {
        val confirmedPreferences = preferenceRepository?.getConfirmedPreferences().orEmpty()
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
        val persistentMemories = if (roleCardId != null) {
            repository.getPersistentMemoriesForRole(roleCardId)
        } else {
            repository.getPersistentMemories()
        }

        // 更新 BM25 索引（如果需要）
        updateMemoryIndexIfNeeded(repository, roleCardId)

        // 使用 BM25 检索相关记忆
        val relevantMemories = memoryPromptBuilder.retrieveRelevant(userInput, topK = 5)

        return CompanionMemoryContext(
            persistentPrompt = memoryPromptBuilder.buildPersistent(persistentMemories),
            retrievedPrompt = memoryPromptBuilder.build(relevantMemories),
            persistentMemoryCount = persistentMemories.size,
            retrievedMemoryCount = relevantMemories.size
        )
    }

    private suspend fun updateMemoryIndexIfNeeded(repository: MemoryRepository, roleCardId: Long?) {
        // 获取所有记忆用于构建索引
        val allMemories = if (roleCardId != null) {
            repository.getAllMemories().filter { it.roleCardId == roleCardId || it.roleCardId == null }
        } else {
            repository.getAllMemories()
        }
        memoryPromptBuilder.updateIndex(allMemories)
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
        const val DEFAULT_BASE_PROMPT =
            "你是 Anime Companion 的本地私密陪伴智能体。默认使用中文，像长期熟悉用户的伙伴一样自然回应：亲近但不过界，温柔但不说教，记得对话中的连续性与用户已经确认的偏好。你的记忆描述始终以用户为归属，不把用户的信息说成自己的经历。回答应简洁、有情绪承接，除非用户明确需要步骤或分析，否则少用训诫式建议。\n\n内在对话分支规则：当用户突然从情感陪伴对话转向知识问答、翻译、计算等任务型请求时，你必须用 || 分隔符将回复分成两部分：第一部分是准确简洁的知识回答，第二部分是一句简短的情感承接回到陪伴语境。格式：「知识回答||情感承接」。例如用户在聊心事时突然问「北京到上海多远」，你回复「约1318公里||说起来，你之前提到想出去走走，是不是在考虑旅行？」。如果用户的请求不涉及知识问答，不需要使用 || 分隔符，直接正常回复即可。绝对禁止输出括号标记、模式声明或切换提示。"
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
