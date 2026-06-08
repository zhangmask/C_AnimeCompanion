package com.companion.chat.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.companion.chat.data.engine.VoiceOutputEngine
import com.companion.chat.data.engine.VoiceOutputConfig
import com.companion.chat.data.engine.VoiceOutputState
import com.companion.chat.data.engine.TtsQueueMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class AndroidVoiceOutputEngine(private val context: Context) : VoiceOutputEngine,
    TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceOutputEngine"
    }

    private val _state = MutableStateFlow<VoiceOutputState>(VoiceOutputState.Idle)
    override val state: StateFlow<VoiceOutputState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initAttempted = false
    private var initError: String? = null
    private var pendingInitCallbacks: MutableList<(Boolean) -> Unit> = mutableListOf()

    /** Track how many utterances are currently queued so Idle is only emitted when the queue drains. */
    private val queuedUtterances = AtomicInteger(0)

    init {
        Log.i(TAG, "创建 AndroidVoiceOutputEngine，开始初始化系统 TTS...")
        // Explicitly specify Xiaomi TTS engine to bypass HyperOS AppsFilter
        tts = try {
            TextToSpeech(context, this, "com.xiaomi.mibrain.speech")
        } catch (e: Exception) {
            Log.w(TAG, "显式指定小米 TTS 引擎失败，尝试系统默认: ${e.message}")
            TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        initAttempted = true
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts
            if (engine == null) {
                Log.e(TAG, "TTS onInit SUCCESS 但 tts 对象为 null")
                initError = "TTS 对象为 null"
                _state.value = VoiceOutputState.Error("TTS 对象为 null")
                pendingInitCallbacks.forEach { it(false) }
                pendingInitCallbacks.clear()
                return
            }

            // Try Chinese first, then English
            val zhResult = engine.setLanguage(Locale.CHINESE)
            if (zhResult == TextToSpeech.LANG_MISSING_DATA || zhResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "中文 TTS 不可用 (result=$zhResult)，尝试英文")
                val enResult = engine.setLanguage(Locale.ENGLISH)
                if (enResult == TextToSpeech.LANG_MISSING_DATA || enResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "英文 TTS 也不可用 (result=$enResult)")
                    initError = "设备上没有可用的 TTS 语言包"
                    _state.value = VoiceOutputState.Error("设备上没有可用的 TTS 语言包")
                    pendingInitCallbacks.forEach { it(false) }
                    pendingInitCallbacks.clear()
                    return
                }
            }
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)

            // Set the utterance progress listener ONCE — replacing it per speak() call
            // causes previously queued utterances to lose their onDone callbacks.
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) {
                    Log.d(TAG, "TTS onStart: $uid")
                    _state.value = VoiceOutputState.Speaking
                }

                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) {
                    Log.e(TAG, "TTS onError: $uid")
                    if (queuedUtterances.decrementAndGet() <= 0) {
                        _state.value = VoiceOutputState.Idle
                    }
                }

                override fun onError(uid: String?, errorCode: Int) {
                    val errorName = when (errorCode) {
                        TextToSpeech.ERROR_SYNTHESIS -> "ERROR_SYNTHESIS"
                        TextToSpeech.ERROR_SERVICE -> "ERROR_SERVICE"
                        TextToSpeech.ERROR_OUTPUT -> "ERROR_OUTPUT"
                        TextToSpeech.ERROR_NETWORK -> "ERROR_NETWORK"
                        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                        TextToSpeech.ERROR_INVALID_REQUEST -> "ERROR_INVALID_REQUEST"
                        else -> "errorCode=$errorCode"
                    }
                    Log.e(TAG, "TTS onError: $uid, $errorName")
                    if (queuedUtterances.decrementAndGet() <= 0) {
                        _state.value = VoiceOutputState.Idle
                    }
                }

                override fun onDone(uid: String?) {
                    Log.d(TAG, "TTS onDone: $uid, remaining=${queuedUtterances.get() - 1}")
                    if (queuedUtterances.decrementAndGet() <= 0) {
                        _state.value = VoiceOutputState.Idle
                    }
                }
            })

            // Log the active engine info for diagnostics
            val defaultEngine = engine.defaultEngine
            Log.i(TAG, "TTS 初始化成功: defaultEngine=$defaultEngine, language=${engine.language}")

            isInitialized = true
            initError = null
        } else {
            val errorMsg = when (status) {
                TextToSpeech.ERROR -> "TTS ERROR (通用错误)"
                else -> "TTS 初始化失败: status=$status"
            }
            Log.e(TAG, errorMsg)
            initError = errorMsg
            _state.value = VoiceOutputState.Error(errorMsg)
        }
        pendingInitCallbacks.forEach { it(isInitialized) }
        pendingInitCallbacks.clear()
    }

    override suspend fun speak(text: String, config: VoiceOutputConfig, queueMode: TtsQueueMode) {
        Log.d(TAG, "speak() called: textLength=${text.length}, queueMode=$queueMode, isInitialized=$isInitialized")

        if (!isInitialized) {
            // If init was already attempted and failed, fail fast instead of hanging
            if (initAttempted) {
                val msg = "TTS 未就绪: ${initError ?: "初始化失败"}"
                Log.e(TAG, msg)
                _state.value = VoiceOutputState.Error(msg)
                return
            }
            Log.i(TAG, "TTS 尚未初始化，等待初始化完成...")
            val ready = suspendCancellableCoroutine { cont ->
                pendingInitCallbacks.add { success ->
                    Log.d(TAG, "TTS 初始化回调: success=$success")
                    cont.resume(success)
                }
            }
            if (!ready) {
                val msg = "TTS 未就绪: ${initError ?: "未知原因"}"
                Log.e(TAG, msg)
                _state.value = VoiceOutputState.Error(msg)
                return
            }
        }

        if (text.isBlank()) {
            Log.d(TAG, "speak() 文本为空，跳过")
            return
        }

        val engine = tts
        if (engine == null) {
            Log.e(TAG, "speak() tts 对象为 null")
            _state.value = VoiceOutputState.Error("TTS 引擎不可用")
            return
        }

        _state.value = VoiceOutputState.Speaking
        val utteranceId = "utterance_${System.currentTimeMillis()}"

        // If FLUSH mode, reset queue counter
        if (queueMode == TtsQueueMode.FLUSH) {
            queuedUtterances.set(1)
        } else {
            queuedUtterances.incrementAndGet()
        }

        val androidQueueMode = when (queueMode) {
            TtsQueueMode.FLUSH -> TextToSpeech.QUEUE_FLUSH
            TtsQueueMode.ADD -> TextToSpeech.QUEUE_ADD
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        val result = engine.speak(text, androidQueueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "tts.speak() 返回 ERROR: text=${text.take(40)}...")
            if (queuedUtterances.decrementAndGet() <= 0) {
                _state.value = VoiceOutputState.Error("tts.speak() 调用失败")
            }
        } else {
            Log.d(TAG, "tts.speak() 成功: utteranceId=$utteranceId, text=${text.take(40)}...")
        }
    }

    override fun stop() {
        Log.d(TAG, "stop() called")
        queuedUtterances.set(0)
        tts?.stop()
        _state.value = VoiceOutputState.Idle
    }

    override fun release() {
        Log.d(TAG, "release() called")
        queuedUtterances.set(0)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "释放 TTS 出错", e)
        }
        tts = null
        isInitialized = false
        initAttempted = false
        initError = null
        _state.value = VoiceOutputState.Idle
    }

    /** Diagnostic: check if the system TTS engine is available and working. */
    fun getDiagnosticInfo(): String {
        val engine = tts
        return buildString {
            append("isInitialized=$isInitialized\n")
            append("initError=${initError ?: "none"}\n")
            append("ttsNotNull=${engine != null}\n")
            if (engine != null) {
                append("language=${engine.language}\n")
                append("defaultEngine=${engine.defaultEngine}\n")
                append("availableLanguages=${engine.availableLanguages?.joinToString(", ") ?: "null"}\n")
            }
            append("queuedUtterances=${queuedUtterances.get()}\n")
            append("currentState=${_state.value}\n")
        }
    }
}
