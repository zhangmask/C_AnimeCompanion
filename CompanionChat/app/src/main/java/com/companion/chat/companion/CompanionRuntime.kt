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

    suspend fun refreshBasePrompt(language: AppLanguage = appLanguage, roleCardId: Long? = null): String {
        val systemPrompt = SystemPromptBuilder.build(language)
        val roleCard = roleCardId?.let { roleCardRepository.getRoleCard(it) } ?: roleCardRepository.getActiveRoleCard()
        val rolePrompt = roleCardPromptBuilder.build(roleCard)
        val skillPrompt = skillRepository.getActiveSkill()?.systemPrompt?.trim().orEmpty()
        val userProfilePrompt = buildUserProfilePrompt()

        android.util.Log.i("CompanionRuntime", "刷新系统提示: language=$language, roleCardId=$roleCardId, role=${roleCard?.name ?: "null"}, rolePromptLen=${rolePrompt.length}, skillPromptLen=${skillPrompt.length}, userProfilePromptLen=${userProfilePrompt.length}")

        return buildList {
            // 角色设定放最前，确保模型优先识别角色身份
            if (rolePrompt.isNotBlank()) {
                add(rolePrompt)
            }
            add(systemPrompt)
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

    suspend fun buildMemoryContext(
        userInput: String,
        roleCardId: Long? = null,
        baseSystemPrompt: String = ""
    ): CompanionMemoryContext {
        val repository = memoryRepository ?: return CompanionMemoryContext()

        // Step 1: AI-based keyword extraction
        val keywords = extractKeywordsWithAI(userInput, baseSystemPrompt)
        android.util.Log.i("CompanionRuntime", "记忆检索关键词: $keywords (input=${userInput.take(40)})")

        // Step 2: LIKE search with AI-generated keywords (FTS4 不支持中文分词，改用 LIKE)
        val relevantMemories = if (keywords.isNotEmpty() && roleCardId != null) {
            repository.searchByKeywordsWithRole(keywords, roleCardId, 5)
        } else if (keywords.isNotEmpty()) {
            // 无 roleCardId 时退化为不带角色过滤的 LIKE 搜索
            keywords.flatMap { kw ->
                if (kw.length >= 2) repository.findSimilarByKeywords("preference", kw, 5) else emptyList()
            }.distinctBy { it.id }.take(5)
        } else {
            emptyList()
        }

        android.util.Log.i("CompanionRuntime", "LIKE搜索结果: 找到${relevantMemories.size}条记忆, keywords=$keywords")
        relevantMemories.forEach { mem ->
            android.util.Log.i("CompanionRuntime", "  记忆: [${mem.category}] ${mem.content.take(50)} strength=${mem.strength}")
        }

        val memoryPrompt = memoryPromptBuilder.build(relevantMemories)
        android.util.Log.i("CompanionRuntime", "记忆提示词长度: ${memoryPrompt.length}, 内容预览: ${memoryPrompt.take(100)}")

        return CompanionMemoryContext(
            persistentPrompt = memoryPrompt,
            retrievedPrompt = memoryPrompt,
            persistentMemoryCount = relevantMemories.size,
            retrievedMemoryCount = relevantMemories.size
        )
    }

    /**
     * AI 关键词提取（两阶段推理的第一次调用）：
     * 调用 LLM 从用户输入中提取搜索关键词，用于 FTS 搜索记忆库。
     * 这一步的输出不显示在 UI 上，只用于记忆检索。
     * 第二次调用（runTurn）才生成给人看的回复。
     */
    private suspend fun extractKeywordsWithAI(userInput: String, baseSystemPrompt: String): List<String> {
        val engine = inferenceEngineProvider() ?: return extractKeywords(userInput)
        if (userInput.isBlank()) return emptyList()

        return try {
            // 极明确的工具指令：禁止回答问题，只输出关键词
            val keywordSystemPrompt = buildString {
                appendLine("你是关键词提取工具，不是聊天机器人。")
                appendLine("你的唯一任务：从用户输入中提取3-5个用于搜索记忆库的关键词。")
                appendLine("绝对禁止：回答问题、对话、解释、评论用户说的内容。")
                appendLine("只输出关键词，用空格分隔，不要任何其他文字。")
                appendLine("示例：")
                appendLine("输入：我今天去吃了火锅感觉不错 → 输出：火锅 美食 今天 吃饭")
                appendLine("输入：我最近压力好大想休息 → 输出：压力 休息 最近")
            }
            engine.rebuildConversation(keywordSystemPrompt)

            val keywordPrompt = "提取关键词：$userInput"
            val response = StringBuilder()
            engine.sendMessageStream(
                listOf(ChatMessage(role = MessageRole.USER, content = keywordPrompt))
            ).collect { token ->
                response.append(token)
            }
            android.util.Log.i("CompanionRuntime", "关键词提取原始响应: ${response.toString().take(100)}")

            // 恢复原始系统提示并清空 KV 缓存
            engine.rebuildConversation(baseSystemPrompt)

            val keywords = response.toString()
                .trim()
                .split(Regex("[\\s,，、;；\n]+"))
                .map { it.trim() }
                .filter { it.length in 1..10 && it.isNotBlank() }
                .distinct()
                .take(5)

            if (keywords.isNotEmpty()) keywords else extractKeywords(userInput)
        } catch (e: Exception) {
            android.util.Log.w("CompanionRuntime", "AI关键词提取失败，回退到简单提取: ${e.message}")
            try { engine.rebuildConversation(baseSystemPrompt) } catch (_: Exception) {}
            extractKeywords(userInput)
        }
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
