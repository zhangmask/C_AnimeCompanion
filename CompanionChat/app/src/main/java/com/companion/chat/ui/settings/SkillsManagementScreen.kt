package com.companion.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.local.entity.Skill
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsManagementScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onActivateSkill: suspend (Long) -> Unit = {},
    skillsManagementViewModel: SkillsManagementViewModel = viewModel()
) {
    val uiState by skillsManagementViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingSkill by remember { mutableStateOf<Skill?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Strings.txt(StringsKey.skills_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings.txt(StringsKey.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = Strings.txt(StringsKey.char_mgmt_new))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = Strings.txt(StringsKey.settings_sub_skills),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.activeSkill?.let { activeSkill ->
                item { SkillsSectionTitle(Strings.txt(StringsKey.char_mgmt_set_active)) }
                item {
                    SkillItem(
                        skill = activeSkill,
                        isActive = true,
                        onActivate = {},
                        onEdit = if (activeSkill.isBuiltIn) null else ({ { editingSkill = activeSkill } }),
                        onDelete = if (activeSkill.isBuiltIn) null else ({ { deletingSkill = activeSkill } })
                    )
                }
            }

            item { SkillsSectionTitle(Strings.txt(StringsKey.skills_title)) }
            if (uiState.builtInSkills.isEmpty()) {
                item {
                    SkillsEmptyState(Strings.txt(StringsKey.skills_empty), Strings.txt(StringsKey.skills_empty) + ".")
                }
            } else {
                items(uiState.builtInSkills, key = { it.id }) { skill ->
                    SkillItem(
                        skill = skill,
                        isActive = skill.isActive,
                        onActivate = {
                            scope.launch {
                                onActivateSkill(skill.id)
                                skillsManagementViewModel.refresh()
                            }
                        },
                        onEdit = null,
                        onDelete = null
                    )
                }
            }

            item { SkillsSectionTitle(Strings.txt(StringsKey.settings_item_skills)) }
            if (uiState.customSkills.isEmpty()) {
                item {
                    SkillsEmptyState(Strings.txt(StringsKey.skills_custom_empty_title), Strings.txt(StringsKey.skills_custom_create_hint))
                }
            } else {
                items(uiState.customSkills, key = { it.id }) { skill ->
                    SkillItem(
                        skill = skill,
                        isActive = skill.isActive,
                        onActivate = {
                            scope.launch {
                                onActivateSkill(skill.id)
                                skillsManagementViewModel.refresh()
                            }
                        },
                        onEdit = { editingSkill = skill },
                        onDelete = { deletingSkill = skill }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        SkillEditorDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, systemPrompt ->
                if (name.isBlank() || systemPrompt.isBlank()) {
                    return@SkillEditorDialog
                }
                skillsManagementViewModel.createSkill(name, description, systemPrompt)
                showCreateDialog = false
            }
        )
    }

    editingSkill?.let { skill ->
        SkillEditorDialog(
            skill = skill,
            onDismiss = { editingSkill = null },
            onSave = { name, description, systemPrompt ->
                if (name.isBlank() || systemPrompt.isBlank()) {
                    return@SkillEditorDialog
                }
                skillsManagementViewModel.updateSkill(
                    id = skill.id,
                    name = name,
                    description = description,
                    systemPrompt = systemPrompt,
                    icon = skill.icon
                )
                editingSkill = null
            }
        )
    }

    deletingSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { deletingSkill = null },
            title = { Text(Strings.txt(StringsKey.role_delete_title)) },
            text = { Text(Strings.txt(StringsKey.skills_delete_confirm, skill.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        skillsManagementViewModel.deleteSkill(skill.id)
                        deletingSkill = null
                    }
                ) {
                    Text(Strings.txt(StringsKey.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSkill = null }) {
                    Text(Strings.txt(StringsKey.cancel))
                }
            }
        )
    }
}

@Composable
private fun SkillsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SkillItem(
    skill: Skill,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = skill.name, style = MaterialTheme.typography.titleMedium)
                    if (skill.description.isNotBlank()) {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isActive) {
                    AssistChip(onClick = {}, label = { Text(Strings.txt(StringsKey.drawer_active_tag)) })
                }
            }

            Text(
                text = Strings.txt(StringsKey.skills_used_count, skill.usageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive) {
                    TextButton(onClick = onActivate) {
                        Text(Strings.txt(StringsKey.enable))
                    }
                }
                onEdit?.let {
                    TextButton(onClick = it) {
                        Text(Strings.txt(StringsKey.edit))
                    }
                }
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(Strings.txt(StringsKey.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsEmptyState(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Build, contentDescription = null)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
