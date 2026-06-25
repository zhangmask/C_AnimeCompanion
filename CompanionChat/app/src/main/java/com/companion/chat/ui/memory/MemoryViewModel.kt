package com.companion.chat.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.companion.chat.appContainer
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.data.memory.MemoryRepository
import com.companion.chat.data.role.RoleCardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemoryViewModel(
    application: Application,
    private val memoryRepository: MemoryRepository = MemoryRepository(
        memoryDao = CompanionDatabase.getInstance(application).memoryDao()
    ),
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        memoryRepository = defaultMemoryRepository(application),
        workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private var allMemories: List<Memory> = emptyList()

    init {
        observeMemories()
        loadRoleCards()
    }

    fun loadMemories() {
        workerScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refreshMemories()
        }
    }

    fun loadRoleCards() {
        workerScope.launch {
            try {
                val repo = getApplication<Application>().appContainer.roleCardRepository
                val roles = repo.getAllRoleCards()
                _uiState.update { it.copy(roleCards = roles) }
            } catch (e: Exception) {
                android.util.Log.e("MemoryViewModel", "加载角色失败", e)
            }
        }
    }

    fun setFilter(filter: MemoryFilter) {
        _uiState.update { it.copy(filter = filter) }
        publishMemories()
    }

    fun setRoleCardFilter(roleCardId: Long?) {
        _uiState.update { it.copy(selectedRoleCardId = roleCardId) }
        publishMemories()
    }

    fun addMemory(content: String, category: String) {
        if (content.isBlank()) return
        workerScope.launch {
            memoryRepository.storeMemory(
                content = content,
                category = category,
                source = MemoryRepository.MANUAL_SOURCE
            )
            refreshMemories()
        }
    }

    fun updateMemory(memoryId: Long, content: String, category: String) {
        val existing = allMemories.firstOrNull { it.id == memoryId } ?: return
        workerScope.launch {
            memoryRepository.updateMemory(
                existing.copy(content = content, category = category)
            )
            refreshMemories()
        }
    }

    fun deleteMemory(memory: Memory) {
        workerScope.launch {
            memoryRepository.deleteMemory(memory)
            refreshMemories()
        }
    }

    /** 改造后：废除 promoteMemory，改为 strengthenMemory */
    fun strengthenMemory(memoryId: Long) {
        workerScope.launch {
            memoryRepository.strengthenMemory(memoryId, 0.15)
            refreshMemories()
        }
    }

    private fun observeMemories() {
        workerScope.launch {
            memoryRepository.observeAllMemories().collectLatest { memories ->
                allMemories = memories
                publishMemories(isLoading = false)
            }
        }
    }

    private suspend fun refreshMemories() {
        allMemories = memoryRepository.getAllMemories()
        publishMemories(isLoading = false)
    }

    private fun publishMemories(isLoading: Boolean = _uiState.value.isLoading) {
        val filter = _uiState.value.filter
        val selectedRoleCardId = _uiState.value.selectedRoleCardId
        val visibleMemories = when (filter) {
            MemoryFilter.ALL -> allMemories
            MemoryFilter.RELATION -> allMemories.filter {
                it.category == "relation" || it.category == "relationship"
            }
            else -> allMemories.filter { it.category == filter.category }
        }.let { memories ->
            if (selectedRoleCardId != null) {
                memories.filter { it.roleCardId == selectedRoleCardId }
            } else {
                memories
            }
        }
        _uiState.update {
            it.copy(
                memories = visibleMemories,
                isLoading = isLoading
            )
        }
    }
}

private fun defaultMemoryRepository(application: Application): MemoryRepository {
    return runCatching { application.appContainer.memoryRepository }.getOrElse {
        MemoryRepository(
            memoryDao = CompanionDatabase.getInstance(application).memoryDao()
        )
    }
}
