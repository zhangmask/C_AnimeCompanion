package com.companion.chat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.companion.chat.appContainer
import com.companion.chat.data.local.CompanionDatabase
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.data.skill.SkillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillsManagementUiState(
    val activeSkill: Skill? = null,
    val builtInSkills: List<Skill> = emptyList(),
    val customSkills: List<Skill> = emptyList(),
    val isLoading: Boolean = true
)

class SkillsManagementViewModel(
    application: Application,
    private val skillRepository: SkillRepository,
    private val workerScope: CoroutineScope
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        skillRepository = defaultSkillRepository(application),
        workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private val _uiState = MutableStateFlow(SkillsManagementUiState())
    val uiState: StateFlow<SkillsManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        workerScope.launch {
            val skills = skillRepository.getAllSkills()
            _uiState.update {
                it.copy(
                    activeSkill = skillRepository.getActiveSkill(),
                    builtInSkills = skills.filter { skill -> skill.isBuiltIn },
                    customSkills = skills.filterNot { skill -> skill.isBuiltIn },
                    isLoading = false
                )
            }
        }
    }

    fun createSkill(name: String, description: String, systemPrompt: String) {
        workerScope.launch {
            skillRepository.createSkill(
                name = name,
                description = description,
                systemPrompt = systemPrompt
            )
            refresh()
        }
    }

    fun updateSkill(id: Long, name: String, description: String, systemPrompt: String, icon: String) {
        workerScope.launch {
            skillRepository.updateSkill(
                id = id,
                name = name,
                description = description,
                systemPrompt = systemPrompt,
                icon = icon
            )
            refresh()
        }
    }

    fun deleteSkill(id: Long) {
        workerScope.launch {
            skillRepository.deleteSkill(id)
            refresh()
        }
    }
}

private fun defaultSkillRepository(application: Application): SkillRepository {
    return runCatching { application.appContainer.skillRepository }.getOrElse {
        SkillRepository(
            skillDao = CompanionDatabase.getInstance(application).skillDao()
        )
    }
}
