package com.companion.chat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.companion.chat.appContainer
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.role.RoleCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoleManagementUiState(
    val activeRoleCard: RoleCard? = null,
    val roleCards: List<RoleCard> = emptyList(),
    val isLoading: Boolean = true
)

class RoleManagementViewModel(
    application: Application,
    private val roleCardRepository: RoleCardRepository,
    private val workerScope: CoroutineScope
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        roleCardRepository = defaultRoleCardRepository(application),
        workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private val _uiState = MutableStateFlow(RoleManagementUiState())
    val uiState: StateFlow<RoleManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        workerScope.launch {
            val roleCards = roleCardRepository.getAllRoleCards()
            _uiState.update {
                it.copy(
                    activeRoleCard = roleCardRepository.getActiveRoleCard(),
                    roleCards = roleCards,
                    isLoading = false
                )
            }
        }
    }

    fun createRoleCard(
        name: String,
        description: String,
        avatar: String,
        persona: String,
        speakingStyle: String,
        background: String,
        rules: String,
        taboos: String,
        openingMessage: String,
        exampleDialogue: String,
        avatarImageUri: String = "",
        galleryImageUris: List<String> = emptyList(),
        imageStylePrompt: String = "",
        voiceProfileUri: String = "",
        voiceMode: String = "CLONE",
        voiceDisplayName: String = ""
    ) {
        workerScope.launch {
            roleCardRepository.createRoleCard(
                name = name,
                description = description,
                avatar = avatar,
                persona = persona,
                speakingStyle = speakingStyle,
                background = background,
                rules = rules,
                taboos = taboos,
                openingMessage = openingMessage,
                exampleDialogue = exampleDialogue,
                avatarImageUri = avatarImageUri,
                galleryImageUris = galleryImageUris,
                imageStylePrompt = imageStylePrompt,
                voiceProfileUri = voiceProfileUri,
                voiceMode = voiceMode,
                voiceDisplayName = voiceDisplayName
            )
            refresh()
        }
    }

    fun updateRoleCard(
        id: Long,
        name: String,
        description: String,
        avatar: String,
        persona: String,
        speakingStyle: String,
        background: String,
        rules: String,
        taboos: String,
        openingMessage: String,
        exampleDialogue: String,
        avatarImageUri: String? = null,
        galleryImageUris: List<String>? = null,
        imageStylePrompt: String? = null,
        voiceProfileUri: String? = null,
        voiceMode: String? = null,
        voiceDisplayName: String? = null
    ) {
        workerScope.launch {
            roleCardRepository.updateRoleCard(
                id = id,
                name = name,
                description = description,
                avatar = avatar,
                persona = persona,
                speakingStyle = speakingStyle,
                background = background,
                rules = rules,
                taboos = taboos,
                openingMessage = openingMessage,
                exampleDialogue = exampleDialogue,
                avatarImageUri = avatarImageUri,
                galleryImageUris = galleryImageUris,
                imageStylePrompt = imageStylePrompt,
                voiceProfileUri = voiceProfileUri,
                voiceMode = voiceMode,
                voiceDisplayName = voiceDisplayName
            )
            refresh()
        }
    }

    fun deleteRoleCard(id: Long) {
        workerScope.launch {
            roleCardRepository.deleteRoleCard(id)
            refresh()
        }
    }
}

private fun defaultRoleCardRepository(application: Application): RoleCardRepository {
    return runCatching { application.appContainer.roleCardRepository }.getOrElse {
        RoleCardRepository(
            roleCardDao = CompanionDatabase.getInstance(application).roleCardDao()
        )
    }
}
