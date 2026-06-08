package com.companion.chat.companion

import androidx.sqlite.db.SupportSQLiteQuery
import com.companion.chat.data.context.ContextManager
import com.companion.chat.data.context.ContextSettings
import com.companion.chat.data.context.ContextWindow
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.local.dao.MemoryDao
import com.companion.chat.data.local.dao.PreferenceDao
import com.companion.chat.data.local.dao.RoleCardDao
import com.companion.chat.data.local.dao.SkillDao
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.data.local.entity.UserPreference
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import com.companion.chat.data.memory.MemoryPromptBuilder
import com.companion.chat.data.memory.MemoryRepository
import com.companion.chat.data.preferences.PreferenceRepository
import com.companion.chat.data.role.RoleCardRepository
import com.companion.chat.data.skill.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRuntimeTest {

    @Test
    fun `base prompt includes active RoleCard identity and active Skill behavior`() = runBlocking {
        val roleRepository = RoleCardRepository(
            roleCardDao = FakeRoleCardDao(
                mutableListOf(
                    roleCard(
                        id = 1L,
                        name = "小夏",
                        persona = "温柔可靠的陪伴者",
                        speakingStyle = "轻松自然"
                    )
                )
            )
        )
        val skillRepository = SkillRepository(
            skillDao = FakeSkillDao(
                mutableListOf(
                    skill(
                        id = 1L,
                        name = "翻译助手",
                        systemPrompt = "请根据用户语境给出自然翻译"
                    )
                )
            )
        )
        val runtime = CompanionRuntime(
            roleCardRepository = roleRepository,
            skillRepository = skillRepository
        )

        val prompt = runtime.refreshBasePrompt()

        assertTrue(prompt.contains("Anime Companion"))
        assertTrue(prompt.contains("小夏"))
        assertTrue(prompt.contains("温柔可靠的陪伴者"))
        assertTrue(prompt.contains("轻松自然"))
        assertTrue(prompt.contains("请根据用户语境给出自然翻译"))
    }

    @Test
    fun `activating RoleCard refreshes identity without losing active Skill behavior`() = runBlocking {
        val roleRepository = RoleCardRepository(
            roleCardDao = FakeRoleCardDao(
                mutableListOf(
                    roleCard(
                        id = 1L,
                        name = "小夏",
                        persona = "温柔可靠的陪伴者",
                        isActive = true
                    ),
                    roleCard(
                        id = 2L,
                        name = "阿澈",
                        persona = "冷静克制的陪伴者",
                        isActive = false
                    )
                )
            )
        )
        val skillRepository = SkillRepository(
            skillDao = FakeSkillDao(
                mutableListOf(
                    skill(
                        id = 1L,
                        name = "翻译助手",
                        systemPrompt = "请根据用户语境给出自然翻译"
                    )
                )
            )
        )
        val runtime = CompanionRuntime(
            roleCardRepository = roleRepository,
            skillRepository = skillRepository
        )

        val prompt = runtime.activateRoleCardAndRefreshPrompt(2L)

        assertTrue(prompt.contains("阿澈"))
        assertTrue(prompt.contains("冷静克制的陪伴者"))
        assertFalse(prompt.contains("小夏"))
        assertFalse(prompt.contains("温柔可靠的陪伴者"))
        assertTrue(prompt.contains("请根据用户语境给出自然翻译"))
    }

    @Test
    fun `activating Skill refreshes behavior without replacing active RoleCard identity`() = runBlocking {
        val roleRepository = RoleCardRepository(
            roleCardDao = FakeRoleCardDao(
                mutableListOf(
                    roleCard(
                        id = 1L,
                        name = "小夏",
                        persona = "温柔可靠的陪伴者",
                        speakingStyle = "轻松自然"
                    )
                )
            )
        )
        val skillRepository = SkillRepository(
            skillDao = FakeSkillDao(
                mutableListOf(
                    skill(
                        id = 1L,
                        name = "翻译助手",
                        systemPrompt = "请根据用户语境给出自然翻译",
                        isActive = true
                    ),
                    skill(
                        id = 2L,
                        name = "会议总结",
                        systemPrompt = "请提炼重点和待办事项",
                        isActive = false
                    )
                )
            )
        )
        val runtime = CompanionRuntime(
            roleCardRepository = roleRepository,
            skillRepository = skillRepository
        )

        val prompt = runtime.activateSkillAndRefreshPrompt(2L)

        assertTrue(prompt.contains("小夏"))
        assertTrue(prompt.contains("温柔可靠的陪伴者"))
        assertTrue(prompt.contains("轻松自然"))
        assertTrue(prompt.contains("请提炼重点和待办事项"))
        assertFalse(prompt.contains("请根据用户语境给出自然翻译"))
    }

    @Test
    fun `confirmed UserPreference prompt only includes stable preferences`() = runBlocking {
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            preferenceRepository = PreferenceRepository(
                preferenceDao = FakePreferenceDao(
                    mutableListOf(
                        userPreference(id = 1L, content = "喜欢简洁回答", confidence = 3),
                        userPreference(id = 2L, content = "偶尔提到一次的语气", confidence = 1)
                    )
                )
            )
        )

        val prompt = runtime.buildConfirmedPreferencePrompt()

        assertTrue(prompt.contains("关于当前用户的已知信息"))
        assertTrue(prompt.contains("喜欢简洁回答"))
        assertFalse(prompt.contains("偶尔提到一次的语气"))
    }

    @Test
    fun `memory context includes persistent and relevant Memory prompts`() = runBlocking {
        val memoryDao = FakeMemoryDao(
            mutableListOf(
                memory(id = 1L, content = "用户叫小明", category = "fact", layer = "long_term"),
                memory(id = 2L, content = "用户喜欢简洁回答", category = "preference", layer = "short_term")
            )
        )
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            memoryRepository = MemoryRepository(memoryDao),
            memoryPromptBuilder = MemoryPromptBuilder()
        )

        val context = runtime.buildMemoryContext("你还记得我喜欢什么回答风格吗")

        assertTrue(context.persistentPrompt.contains("长期记忆中的关键信息"))
        assertTrue(context.persistentPrompt.contains("用户叫小明"))
        assertTrue(context.retrievedPrompt.contains("从记忆中检索到的与当前对话相关的信息"))
        assertTrue(context.retrievedPrompt.contains("用户喜欢简洁回答"))
        assertTrue(context.persistentMemoryCount == 1)
        assertTrue(context.retrievedMemoryCount == 1)
    }

    @Test
    fun `context rebuild falls back to recent conversation snippet when replay fails`() = runBlocking {
        val engine = FakeInferenceEngine(replayResult = false)
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            contextManager = FakeContextManager(),
            inferenceEngineProvider = { engine }
        )
        val messages = listOf(
            ChatMessage(id = "u1", role = MessageRole.USER, content = "我今天有点累"),
            ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "那先慢一点，我陪你")
        )

        val result = runtime.rebuildConversationWithContext(
            stableMessages = messages,
            baseSystemPrompt = "基础 prompt",
            settings = ContextSettings(),
            forceRebuild = true
        )

        assertTrue(result.rebuildAttempted)
        assertTrue(result.replaySucceeded == false)
        assertTrue(result.fallbackSucceeded == true)
        assertTrue(engine.rebuildPrompts.single() == "基础 prompt")
        assertTrue(engine.replayedMessages == messages)
        assertTrue(engine.fallbackPrompts.single().contains("最近几轮对话片段"))
        assertTrue(engine.fallbackPrompts.single().contains("用户：我今天有点累"))
        assertTrue(engine.fallbackPrompts.single().contains("助手：那先慢一点，我陪你"))
    }

    @Test
    fun `context rebuild is skipped when compression is not needed and no context is injected`() = runBlocking {
        val engine = FakeInferenceEngine()
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            contextManager = FakeContextManager(shouldCompress = false),
            inferenceEngineProvider = { engine }
        )

        val result = runtime.rebuildConversationWithContext(
            stableMessages = listOf(ChatMessage(role = MessageRole.USER, content = "你好")),
            baseSystemPrompt = "基础 prompt",
            settings = ContextSettings(),
            forceRebuild = false
        )

        assertFalse(result.rebuildAttempted)
        assertTrue(engine.rebuildPrompts.isEmpty())
        assertTrue(engine.replayedMessages.isEmpty())
        assertTrue(engine.fallbackPrompts.isEmpty())
    }

    @Test
    fun `post turn learning is scheduled and triggered through runtime`() {
        val learner = FakePostTurnLearning()
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            postTurnLearning = learner
        )
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "我喜欢简洁回答"))

        runtime.onTurnFinished(
            sessionIdProvider = { "session-1" },
            messagesProvider = { messages }
        )
        runtime.onConversationBoundary(
            reason = "切换会话",
            sessionId = "session-1",
            messages = messages
        )
        runtime.cancelPostTurnLearning()
        runtime.release()

        assertTrue(learner.scheduledSessionId == "session-1")
        assertTrue(learner.scheduledMessages == messages)
        assertTrue(learner.triggeredReason == "切换会话")
        assertTrue(learner.triggeredSessionId == "session-1")
        assertTrue(learner.triggeredMessages == messages)
        assertTrue(learner.cancelCalled)
        assertTrue(learner.releaseCalled)
    }

    @Test
    fun `runTurn rebuilds context and emits assistant token events`() = runBlocking {
        val engine = FakeInferenceEngine(tokens = listOf("你好", "呀"))
        val runtime = CompanionRuntime(
            roleCardRepository = RoleCardRepository(FakeRoleCardDao()),
            skillRepository = SkillRepository(FakeSkillDao()),
            contextManager = FakeContextManager(shouldCompress = true),
            inferenceEngineProvider = { engine }
        )
        val messages = listOf(
            ChatMessage(id = "u1", role = MessageRole.USER, content = "你好"),
            ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "", isStreaming = true)
        )

        val events = runtime.runTurn(
            messages = messages,
            baseSystemPrompt = "基础 prompt",
            settings = ContextSettings()
        ).toList()

        assertTrue(events == listOf(CompanionTurnEvent.AssistantToken("你好"), CompanionTurnEvent.AssistantToken("呀")))
        assertTrue(engine.rebuildPrompts.single() == "基础 prompt")
        assertTrue(engine.replayedMessages == messages.filterNot { it.isStreaming })
        assertTrue(engine.sentMessages == messages)
    }

    private fun roleCard(
        id: Long,
        name: String,
        persona: String,
        speakingStyle: String = "",
        isActive: Boolean = true
    ) = RoleCard(
        id = id,
        name = name,
        description = "",
        avatar = "person",
        persona = persona,
        speakingStyle = speakingStyle,
        background = "",
        rules = "",
        taboos = "",
        openingMessage = "",
        exampleDialogue = "",
        isBuiltIn = false,
        isActive = isActive,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun skill(
        id: Long,
        name: String,
        systemPrompt: String,
        isActive: Boolean = true
    ) = Skill(
        id = id,
        name = name,
        description = "",
        systemPrompt = systemPrompt,
        icon = "custom",
        isBuiltIn = false,
        isActive = isActive,
        usageCount = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun userPreference(
        id: Long,
        content: String,
        confidence: Int
    ) = UserPreference(
        id = id,
        category = "style",
        content = content,
        confidence = confidence,
        createdAt = id,
        updatedAt = id
    )

    private fun memory(
        id: Long,
        content: String,
        category: String,
        layer: String
    ) = Memory(
        id = id,
        content = content,
        category = category,
        layer = layer,
        source = "test",
        referenceCount = 0,
        sessionId = null,
        createdAt = id,
        updatedAt = id,
        expiresAt = null
    )

    private class FakeRoleCardDao(
        val roleCards: MutableList<RoleCard> = mutableListOf()
    ) : RoleCardDao {

        override suspend fun insert(roleCard: RoleCard): Long {
            roleCards += roleCard
            return roleCard.id
        }

        override suspend fun update(roleCard: RoleCard) {
            val index = roleCards.indexOfFirst { it.id == roleCard.id }
            if (index >= 0) roleCards[index] = roleCard
        }

        override suspend fun delete(roleCard: RoleCard) {
            roleCards.removeAll { it.id == roleCard.id }
        }

        override suspend fun getAll(): List<RoleCard> = roleCards

        override suspend fun getActive(): RoleCard? = roleCards.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): RoleCard? = roleCards.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int {
            roleCards.replaceAll { it.copy(isActive = false) }
            return roleCards.size
        }

        override suspend fun activate(id: Long, now: Long): Int {
            val index = roleCards.indexOfFirst { it.id == id }
            if (index < 0) return 0
            roleCards[index] = roleCards[index].copy(isActive = true, updatedAt = now)
            return 1
        }
    }

    private class FakeSkillDao(
        val skills: MutableList<Skill> = mutableListOf()
    ) : SkillDao {

        override suspend fun insert(skill: Skill): Long {
            skills += skill
            return skill.id
        }

        override suspend fun insertAll(skills: List<Skill>): List<Long> = skills.map { insert(it) }

        override suspend fun update(skill: Skill) {
            val index = skills.indexOfFirst { it.id == skill.id }
            if (index >= 0) this.skills[index] = skill
        }

        override suspend fun delete(skill: Skill) {
            skills.removeAll { it.id == skill.id }
        }

        override suspend fun getAll(): List<Skill> = skills

        override suspend fun getActive(): Skill? = skills.firstOrNull { it.isActive }

        override suspend fun getById(id: Long): Skill? = skills.firstOrNull { it.id == id }

        override suspend fun deactivateAll(): Int {
            skills.replaceAll { it.copy(isActive = false) }
            return skills.size
        }

        override suspend fun activate(id: Long, now: Long): Int {
            val index = skills.indexOfFirst { it.id == id }
            if (index < 0) return 0
            val skill = skills[index]
            skills[index] = skill.copy(isActive = true, usageCount = skill.usageCount + 1, updatedAt = now)
            return 1
        }
    }

    private class FakePreferenceDao(
        val preferences: MutableList<UserPreference> = mutableListOf()
    ) : PreferenceDao {

        override suspend fun insert(preference: UserPreference): Long {
            preferences += preference
            return preference.id
        }

        override suspend fun update(preference: UserPreference) {
            val index = preferences.indexOfFirst { it.id == preference.id }
            if (index >= 0) preferences[index] = preference
        }

        override suspend fun getByCategory(category: String): List<UserPreference> {
            return preferences.filter { it.category == category }.sortedByDescending { it.updatedAt }
        }

        override suspend fun findExactMatch(category: String, content: String): UserPreference? {
            return preferences.firstOrNull { it.category == category && it.content.equals(content, ignoreCase = true) }
        }

        override suspend fun getConfirmed(minimumConfidence: Int): List<UserPreference> {
            return preferences
                .filter { it.confidence >= minimumConfidence }
                .sortedByDescending { it.updatedAt }
        }
    }

    private class FakeMemoryDao(
        val memories: MutableList<Memory> = mutableListOf()
    ) : MemoryDao {

        override suspend fun insert(memory: Memory): Long {
            memories += memory
            return memory.id
        }

        override suspend fun insertAll(memories: List<Memory>): List<Long> = memories.map { insert(it) }

        override suspend fun update(memory: Memory) {
            val index = memories.indexOfFirst { it.id == memory.id }
            if (index >= 0) memories[index] = memory
        }

        override suspend fun delete(memory: Memory) {
            memories.removeAll { it.id == memory.id }
        }

        override suspend fun getAll(): List<Memory> = memories.sortedByDescending { it.updatedAt }

        override fun observeAll(): Flow<List<Memory>> = emptyFlow()

        override suspend fun getByLayer(layer: String): List<Memory> {
            return memories.filter { it.layer == layer }.sortedByDescending { it.updatedAt }
        }

        override suspend fun getPersistentMemories(): List<Memory> {
            return getByLayer("long_term")
        }

        override suspend fun getByCategory(category: String): List<Memory> {
            return memories.filter { it.category == category }.sortedByDescending { it.updatedAt }
        }

        override suspend fun findExactMatch(category: String, content: String): Memory? {
            return memories.firstOrNull { it.category == category && it.content == content }
        }

        override suspend fun searchByFTS(query: SupportSQLiteQuery): List<Memory> {
            return memories.filter { it.layer != "long_term" && it.content.contains("简洁") }
        }

        override suspend fun incrementReference(id: Long): Int = 1

        override suspend fun promoteToLongTerm(id: Long, now: Long): Int {
            val index = memories.indexOfFirst { it.id == id }
            if (index < 0) return 0
            memories[index] = memories[index].copy(layer = "long_term", updatedAt = now)
            return 1
        }

        override suspend fun cleanupExpiredShortTerm(now: Long): Int = 0

        override suspend fun getPromotableShortTerm(): List<Memory> = emptyList()
    }

    private class FakeContextManager(
        private val shouldCompress: Boolean = false
    ) : ContextManager {

        override fun shouldCompress(messages: List<ChatMessage>, settings: ContextSettings): Boolean = shouldCompress

        override suspend fun buildContext(
            messages: List<ChatMessage>,
            systemPrompt: String,
            userPreferences: String,
            persistentMemoryPrompt: String,
            memoryPrompt: String,
            settings: ContextSettings
        ): ContextWindow {
            return ContextWindow(
                systemPrompt = systemPrompt,
                userPreferences = userPreferences,
                persistentMemoryPrompt = persistentMemoryPrompt,
                memoryPrompt = memoryPrompt,
                historySummary = "",
                recentMessages = messages,
                currentMessage = messages.last { it.role == MessageRole.USER }
            )
        }

        override suspend fun compressHistory(
            messages: List<ChatMessage>,
            settings: ContextSettings
        ): String = ""
    }

    private class FakeInferenceEngine(
        private val rebuildResult: Boolean = true,
        private val replayResult: Boolean = true,
        private val fallbackResult: Boolean = true,
        private val tokens: List<String> = emptyList()
    ) : InferenceEngine {

        private val mutableState = MutableStateFlow<InferenceState>(InferenceState.Ready)
        override val state: StateFlow<InferenceState> = mutableState

        val rebuildPrompts = mutableListOf<String>()
        val fallbackPrompts = mutableListOf<String>()
        var replayedMessages: List<ChatMessage> = emptyList()
        var sentMessages: List<ChatMessage> = emptyList()

        override suspend fun initialize(config: EngineConfig) = Unit

        override fun getCurrentConfig(): EngineConfig? = EngineConfig(modelPath = "/tmp/model.gguf")

        override suspend fun rebuildConversation(systemPrompt: String): Boolean {
            rebuildPrompts += systemPrompt
            return rebuildResult
        }

        override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean {
            fallbackPrompts += systemPrompt
            return fallbackResult
        }

        override suspend fun replayMessages(messages: List<ChatMessage>): Boolean {
            replayedMessages = messages
            return replayResult
        }

        override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> {
            sentMessages = messages
            return flowOf(*tokens.toTypedArray())
        }

        override fun cancel() = Unit

        override fun release() = Unit
    }

    private class FakePostTurnLearning : CompanionPostTurnLearning {
        var scheduledSessionId = ""
        var scheduledMessages: List<ChatMessage> = emptyList()
        var triggeredReason = ""
        var triggeredSessionId = ""
        var triggeredMessages: List<ChatMessage> = emptyList()
        var cancelCalled = false
        var releaseCalled = false

        override fun scheduleAfterIdle(
            sessionIdProvider: () -> String,
            messagesProvider: () -> List<ChatMessage>
        ) {
            scheduledSessionId = sessionIdProvider()
            scheduledMessages = messagesProvider()
        }

        override fun triggerNow(
            reason: String,
            sessionId: String,
            messages: List<ChatMessage>
        ) {
            triggeredReason = reason
            triggeredSessionId = sessionId
            triggeredMessages = messages
        }

        override fun cancelRunningSummary() {
            cancelCalled = true
        }

        override fun release() {
            releaseCalled = true
        }
    }
}
