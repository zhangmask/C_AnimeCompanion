package com.companion.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.companion.chat.ui.chat.ChatViewModel
import com.companion.chat.ui.home.DiscoverViewModel
import com.companion.chat.ui.memory.MemoryViewModel
import com.companion.chat.ui.settings.ModelConfigViewModel
import com.companion.chat.ui.settings.RoleManagementViewModel
import com.companion.chat.ui.settings.SkillsManagementViewModel
import com.companion.chat.ui.settings.VoiceSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppViewModelFactory(
    private val application: Application,
    private val container: AppContainer = application.appContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(application, container) as T
            modelClass.isAssignableFrom(DiscoverViewModel::class.java) -> DiscoverViewModel(
                application = application,
                repository = container.discoverRoleRepository,
                imageConfigRepository = container.imageGenerationConfigRepository,
                imageEngineSelector = container.imageGenerationEngineSelector,
                roleCardRepository = container.roleCardRepository
            ) as T
            modelClass.isAssignableFrom(MemoryViewModel::class.java) -> MemoryViewModel(
                application = application,
                memoryRepository = container.memoryRepository,
                workerScope = ioScope
            ) as T
            modelClass.isAssignableFrom(RoleManagementViewModel::class.java) -> RoleManagementViewModel(
                application = application,
                roleCardRepository = container.roleCardRepository,
                workerScope = mainScope
            ) as T
            modelClass.isAssignableFrom(SkillsManagementViewModel::class.java) -> SkillsManagementViewModel(
                application = application,
                skillRepository = container.skillRepository,
                workerScope = mainScope
            ) as T
            modelClass.isAssignableFrom(ModelConfigViewModel::class.java) -> ModelConfigViewModel(
                modelConfigRepository = container.modelConfigRepository,
                contextConfigRepository = container.contextConfigRepository,
                imageConfigRepository = container.imageGenerationConfigRepository
            ) as T
            modelClass.isAssignableFrom(VoiceSettingsViewModel::class.java) -> VoiceSettingsViewModel(
                voiceInputConfigRepository = container.voiceInputConfigRepository,
                cloudAsrConfigRepository = container.cloudAsrConfigRepository,
                voiceCloneConfigRepository = container.voiceCloneConfigRepository,
                voiceOutputSettingsRepository = container.voiceOutputSettingsRepository
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
