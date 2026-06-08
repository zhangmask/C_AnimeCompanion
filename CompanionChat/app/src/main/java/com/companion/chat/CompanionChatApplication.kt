package com.companion.chat

import android.app.Application
import android.content.Context
import com.companion.chat.data.memory.MemoryLifecycleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CompanionChatApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        logToFile("Application.onCreate")

        val sessionRepository = appContainer.chatSessionRepository
        val memoryRepository = appContainer.memoryRepository
        val memoryLifecycleManager = MemoryLifecycleManager(memoryRepository)
        applicationScope.launch {
            runCatching {
                logToFile("开始 ensureInitialized")
                sessionRepository.ensureInitialized()
                logToFile("ensureInitialized 完成")
                memoryLifecycleManager.runStartupMaintenance()
                logToFile("记忆生命周期维护完成")
            }.onFailure {
                logToFile("ensureInitialized 失败: ${it.javaClass.simpleName}: ${it.message}")
            }
        }

        // 预热 MOSS TTS 语音模型（后台执行，不阻塞启动）
        applicationScope.launch {
            runCatching {
                logToFile("开始预热 MOSS TTS 模型")
                appContainer.warmUp()
                logToFile("MOSS TTS 模型预热完成")
            }.onFailure {
                logToFile("MOSS TTS 预热失败: ${it.message}")
            }
        }
    }

    private fun logToFile(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        openFileOutput("app_init_log.txt", Context.MODE_APPEND).use { output ->
            output.write("[$time] $message\n".toByteArray())
        }
    }
}
