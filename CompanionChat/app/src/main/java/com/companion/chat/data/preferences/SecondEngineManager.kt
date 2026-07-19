package com.companion.chat.data.preferences

import com.companion.chat.data.engine.EngineConfig
import com.companion.chat.data.engine.InferenceEngine
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

sealed class SummaryRunResult {
    data class Completed(val content: String) : SummaryRunResult()
    data object SkippedPrimaryBusy : SummaryRunResult()
    data object SkippedAlreadyRunning : SummaryRunResult()
    data object Cancelled : SummaryRunResult()
    data object TimedOut : SummaryRunResult()
    data class Failed(val message: String) : SummaryRunResult()
}

class SecondEngineManager(
    private val primaryEngineStateProvider: () -> InferenceState,
    private val engineFactory: () -> InferenceEngine,
    private val timeoutMillis: Long = 60_000L
) {

    sealed class EngineState {
        object Idle : EngineState()
        data class Running(val engine: InferenceEngine, val job: kotlinx.coroutines.Job) : EngineState()
        object Cancelled : EngineState()
    }

    @Volatile
    private var state: EngineState = EngineState.Idle

    private val runMutex = Mutex()

    suspend fun runSummaryIfAllowed(
        config: EngineConfig,
        prompt: String
    ): SummaryRunResult {
        if (primaryEngineStateProvider() is InferenceState.Generating) {
            return SummaryRunResult.SkippedPrimaryBusy
        }
        if (prompt.isBlank()) {
            return SummaryRunResult.Failed("总结 prompt 不能为空")
        }
        if (runMutex.isLocked) {
            return SummaryRunResult.SkippedAlreadyRunning
        }

        return runMutex.withLock {
            val engine = engineFactory()
            val job = currentCoroutineContext()[kotlinx.coroutines.Job]
            state = EngineState.Running(engine, job!!)

            try {
                withTimeout(timeoutMillis) {
                    engine.initialize(config)
                    val response = StringBuilder()
                    val messages = buildList {
                        if (config.systemPrompt.isNotBlank()) {
                            add(ChatMessage(role = MessageRole.SYSTEM, content = config.systemPrompt))
                        }
                        add(ChatMessage(role = MessageRole.USER, content = prompt))
                    }
                    engine.sendMessageStream(messages).collect { token ->
                        response.append(token)
                    }
                    SummaryRunResult.Completed(response.toString())
                }
            } catch (_: TimeoutCancellationException) {
                engine.cancel()
                SummaryRunResult.TimedOut
            } catch (_: CancellationException) {
                engine.cancel()
                SummaryRunResult.Cancelled
            } catch (error: Exception) {
                SummaryRunResult.Failed(error.message ?: "后台总结失败")
            } finally {
                engine.cancel()
                engine.release()
                state = EngineState.Idle
            }
        }
    }

    fun cancelRunningSummary() {
        val s = state
        if (s is EngineState.Running) {
            s.engine.cancel()
            s.job.cancel()
            state = EngineState.Cancelled
        }
    }

    fun release() {
        cancelRunningSummary()
        val s = state
        if (s is EngineState.Running) {
            s.engine.release()
        }
        state = EngineState.Idle
    }
}
