package com.companion.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companion.chat.data.engine.CustomApiConfigRepository
import com.companion.chat.data.local.entity.CustomApiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomApiConfigUiState(
    val configs: List<CustomApiConfig> = emptyList(),
    val activeConfigId: Long = -1L,
    val editingConfig: CustomApiConfig? = null,
    val isLoading: Boolean = false
)

class CustomApiConfigViewModel(
    private val repository: CustomApiConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomApiConfigUiState(isLoading = true))
    val uiState: StateFlow<CustomApiConfigUiState> = _uiState.asStateFlow()

    init {
        loadList()
    }

    fun loadList() {
        viewModelScope.launch {
            val configs = repository.getAll()
            val active = repository.getActive()
            _uiState.update {
                it.copy(
                    configs = configs,
                    activeConfigId = active?.id ?: -1L,
                    isLoading = false
                )
            }
        }
    }

    fun activate(id: Long) {
        viewModelScope.launch {
            repository.activate(id)
            loadList()
        }
    }

    fun startEdit(id: Long? = null) {
        viewModelScope.launch {
            val config = if (id != null && id > 0) repository.getById(id) else null
            val editing = config ?: CustomApiConfig(
                name = "",
                apiKey = "",
                baseUrl = "",
                model = ""
            )
            _uiState.update { it.copy(editingConfig = editing) }
        }
    }

    fun updateEditingConfig(updated: CustomApiConfig) {
        _uiState.update { it.copy(editingConfig = updated) }
    }

    fun saveEditingConfig() {
        val editing = _uiState.value.editingConfig ?: return
        viewModelScope.launch {
            val id = repository.upsert(editing)
            // 没有活跃配置时自动激活新保存的配置
            if (_uiState.value.activeConfigId <= 0L) {
                repository.activate(id)
            }
            _uiState.update { it.copy(editingConfig = null) }
            loadList()
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingConfig = null) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            loadList()
        }
    }
}
