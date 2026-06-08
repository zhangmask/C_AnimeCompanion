package com.companion.chat.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.companion.chat.appContainer
import com.companion.chat.data.discover.DiscoverRoleCardItem
import com.companion.chat.data.discover.DiscoverRoleRepository
import com.companion.chat.data.discover.RoleSortMode
import com.companion.chat.data.image.HttpImageGenerationEngine
import com.companion.chat.data.image.ImageGenerationConfigRepository
import com.companion.chat.data.image.ImageGenerationEngineSelector
import com.companion.chat.data.image.ImageGenerationPurpose
import com.companion.chat.data.image.ImageGenerationRequest
import com.companion.chat.data.image.LocalImageGenerationEngine
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.role.RoleCardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val query: String = "",
    val tags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val includeMature: Boolean = false,
    val sortMode: RoleSortMode = RoleSortMode.HOT,
    val items: List<DiscoverRoleCardItem> = emptyList(),
    val selectedItem: DiscoverRoleCardItem? = null,
    val isGeneratingImage: Boolean = false,
    val message: String = ""
)

class DiscoverViewModel(
    application: Application,
    private val repository: DiscoverRoleRepository = DiscoverRoleRepository(
        context = application,
        roleCardRepository = RoleCardRepository(
            CompanionDatabase.getInstance(application).roleCardDao()
        )
    ),
    private val imageConfigRepository: ImageGenerationConfigRepository = ImageGenerationConfigRepository(application),
    private val imageEngineSelector: ImageGenerationEngineSelector = ImageGenerationEngineSelector(
        httpEngine = HttpImageGenerationEngine(application),
        localEngine = LocalImageGenerationEngine(application)
    ),
    private val roleCardRepository: RoleCardRepository = RoleCardRepository(
        CompanionDatabase.getInstance(application).roleCardDao()
    )
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = defaultDiscoverRoleRepository(application),
        imageConfigRepository = defaultImageGenerationConfigRepository(application),
        imageEngineSelector = defaultImageGenerationEngineSelector(application),
        roleCardRepository = defaultRoleCardRepository(application)
    )

    private val _uiState = MutableStateFlow(DiscoverUiState(tags = repository.getTags()))
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        refresh()
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = if (it.selectedTag == tag) null else tag) }
        refresh()
    }

    fun setIncludeMature(include: Boolean) {
        _uiState.update { it.copy(includeMature = include) }
        refresh()
    }

    fun setSortMode(sortMode: RoleSortMode) {
        _uiState.update { it.copy(sortMode = sortMode) }
        refresh()
    }

    fun selectRole(roleId: String) {
        _uiState.update { it.copy(selectedItem = repository.getRoleItem(roleId), message = "") }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItem = null, message = "") }
    }

    fun toggleFavorite(roleId: String) {
        repository.toggleFavorite(roleId)
        refresh()
        selectRoleIfOpen(roleId)
    }

    fun unlock(roleId: String) {
        repository.unlock(roleId)
        refresh()
        selectRoleIfOpen(roleId)
    }

    fun copyAndActivate(roleId: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val id = repository.copyToMyRoleCard(roleId)
                roleCardRepository.activateRoleCard(id)
                id
            }.onSuccess { id ->
                refresh()
                selectRoleIfOpen(roleId)
                onReady(id)
            }.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "导入角色失败") }
            }
        }
    }

    fun generateRoleImage(roleId: String) {
        val item = repository.getRoleItem(roleId) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingImage = true, message = "") }
            val baseConfig = imageConfigRepository.getConfig()
            val provider = baseConfig.provider
            val request = ImageGenerationRequest(
                prompt = item.role.generationPreset.defaultPrompt,
                negativePrompt = item.role.generationPreset.negativePrompt,
                roleId = roleId,
                purpose = ImageGenerationPurpose.ROLE_GALLERY
            )
            imageEngineSelector.generate(request, baseConfig.copy(provider = provider))
                .onSuccess { uri ->
                    if (repository.getCollection(roleId).importedRoleCardId == null) {
                        repository.copyToMyRoleCard(roleId)
                    }
                    val attached = repository.attachGeneratedImage(roleId, uri)
                    refresh()
                    selectRoleIfOpen(roleId)
                    _uiState.update {
                        it.copy(
                            isGeneratingImage = false,
                            message = if (attached) "图片已加入角色图库" else "图片已生成: $uri"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isGeneratingImage = false, message = error.message ?: "图片生成失败")
                    }
                }
        }
    }

    private fun refresh() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                items = repository.getRoleItems(
                    query = state.query,
                    selectedTag = state.selectedTag,
                    includeMature = state.includeMature,
                    sortMode = state.sortMode
                )
            )
        }
    }

    private fun selectRoleIfOpen(roleId: String) {
        if (_uiState.value.selectedItem?.role?.id == roleId) {
            selectRole(roleId)
        }
    }
}

private fun defaultDiscoverRoleRepository(application: Application): DiscoverRoleRepository {
    return runCatching { application.appContainer.discoverRoleRepository }.getOrElse {
        DiscoverRoleRepository(
            context = application,
            roleCardRepository = defaultRoleCardRepository(application)
        )
    }
}

private fun defaultImageGenerationConfigRepository(application: Application): ImageGenerationConfigRepository {
    return runCatching { application.appContainer.imageGenerationConfigRepository }
        .getOrElse { ImageGenerationConfigRepository(application) }
}

private fun defaultImageGenerationEngineSelector(application: Application): ImageGenerationEngineSelector {
    return runCatching { application.appContainer.imageGenerationEngineSelector }
        .getOrElse {
            ImageGenerationEngineSelector(
                httpEngine = HttpImageGenerationEngine(application),
                localEngine = LocalImageGenerationEngine(application)
            )
        }
}

private fun defaultRoleCardRepository(application: Application): RoleCardRepository {
    return runCatching { application.appContainer.roleCardRepository }.getOrElse {
        RoleCardRepository(CompanionDatabase.getInstance(application).roleCardDao())
    }
}
