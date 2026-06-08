package com.companion.chat.data.preferences

import com.companion.chat.data.engine.BackendType
import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondEngineManagerTest {

    private val config = EngineConfig(
        modelPath = "/tmp/model.litertlm",
        backend = BackendType.CPU,
        systemPrompt = "你是一个总结器"
    )

    @Test
    fun `Engine-B 能创建独立推理引擎实例`() = runBlocking {
        var createdCount = 0
        val manager = SecondEngineManager(
            primaryEngineStateProvider = { InferenceState.Ready },
            engineFactory = {
                createdCount += 1
                FakeInferenceEngine(tokens = listOf("[]"))
            },
            timeoutMillis = 1_000L
        )

        val result = manager.runSummaryIfAllowed(config, "总结这段对话")

        assertTrue(result is SummaryRunResult.Completed)
        assertEquals(1, createdCount)
    }

    @Test
    fun `Engine-A 为 Generating 时不启动`() = runBlocking {
        var created = false
        val manager = SecondEngineManager(
            primaryEngineStateProvider = { InferenceState.Generating() },
            engineFactory = {
                created = true
                FakeInferenceEngine(tokens = listOf("[]"))
            }
        )

        val result = manager.runSummaryIfAllowed(config, "总结这段对话")

        assertEquals(SummaryRunResult.SkippedPrimaryBusy, result)
        assertTrue(!created)
    }

    @Test
    fun `Engine-B 运行中被取消后返回取消结果而不是异常`() = runBlocking {
        val engine = FakeInferenceEngine(
            tokens = listOf("[", "]"),
            tokenDelayMillis = 100L
        )
        val manager = SecondEngineManager(
            primaryEngineStateProvider = { InferenceState.Ready },
            engineFactory = { engine },
            timeoutMillis = 1_000L
        )

        val deferred = launch {
            val result = manager.runSummaryIfAllowed(config, "总结这段对话")
            assertEquals(SummaryRunResult.Cancelled, result)
        }

        delay(50L)
        manager.cancelRunningSummary()
        deferred.join()
        assertTrue(engine.cancelCalled)
    }

    @Test
    fun `总结超时后被硬中断`() = runBlocking {
        val engine = FakeInferenceEngine(
            tokens = listOf("["),
            tokenDelayMillis = 100L
        )
        val manager = SecondEngineManager(
            primaryEngineStateProvider = { InferenceState.Ready },
            engineFactory = { engine },
            timeoutMillis = 10L
        )

        val result = manager.runSummaryIfAllowed(config, "总结这段对话")

        assertEquals(SummaryRunResult.TimedOut, result)
        assertTrue(engine.cancelCalled)
        assertTrue(engine.releaseCalled)
    }

    @Test
    fun `完成后一定执行 cancel 和 release`() = runBlocking {
        val engine = FakeInferenceEngine(tokens = listOf("[]"))
        val manager = SecondEngineManager(
            primaryEngineStateProvider = { InferenceState.Ready },
            engineFactory = { engine },
            timeoutMillis = 1_000L
        )

        val result = manager.runSummaryIfAllowed(config, "总结这段对话")

        assertTrue(result is SummaryRunResult.Completed)
        assertTrue(engine.cancelCalled)
        assertTrue(engine.releaseCalled)
    }

    private class FakeInferenceEngine(
        private val tokens: List<String>,
        private val tokenDelayMillis: Long = 0L
    ) : InferenceEngine {

        private val mutableState = MutableStateFlow<InferenceState>(InferenceState.Idle)
        override val state: StateFlow<InferenceState> = mutableState

        var initializeCalled = false
        var cancelCalled = false
        var releaseCalled = false

        override suspend fun initialize(config: EngineConfig) {
            initializeCalled = true
            mutableState.value = InferenceState.Ready
        }

        override fun getCurrentConfig(): EngineConfig? = null

        override suspend fun rebuildConversation(systemPrompt: String): Boolean = true

        override suspend fun rebuildConversationWithFallbackContext(systemPrompt: String): Boolean = true

        override suspend fun replayMessages(messages: List<ChatMessage>): Boolean = true

        override fun sendMessageStream(messages: List<ChatMessage>): Flow<String> = flow {
            mutableState.value = InferenceState.Generating()
            tokens.forEach { token ->
                if (tokenDelayMillis > 0) {
                    delay(tokenDelayMillis)
                }
                emit(token)
            }
            mutableState.value = InferenceState.Ready
        }

        override fun cancel() {
            cancelCalled = true
        }

        override fun release() {
            releaseCalled = true
            mutableState.value = InferenceState.Idle
        }
    }
}
