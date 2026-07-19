package com.companion.chat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.companion.chat.data.local.entity.CustomApiConfig
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomApiConfigEditScreen(
    configId: Long,
    onBack: () -> Unit,
    viewModel: CustomApiConfigViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var notepadMode by rememberSaveable { mutableStateOf(false) }
    var notepadText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(configId) {
        viewModel.startEdit(if (configId > 0) configId else null)
    }

    val editing = uiState.editingConfig

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (configId > 0) "编辑配置" else "添加配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (editing != null) {
                            val json = buildConfigJson(editing)
                            notepadText = json.toString(2)
                        }
                        notepadMode = !notepadMode
                    }) {
                        Icon(Icons.Default.EditNote, contentDescription = "更多")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (editing == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("加载中...")
            }
            return@Scaffold
        }

        if (notepadMode) {
            NotepadView(
                text = notepadText,
                onTextChange = { notepadText = it },
                onSave = {
                    val merged = parseNotepadToConfig(notepadText, editing)
                    if (merged != null) {
                        viewModel.updateEditingConfig(merged)
                        notepadMode = false
                    }
                },
                onCancel = { notepadMode = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editing.name,
                onValueChange = { viewModel.updateEditingConfig(editing.copy(name = it)) },
                label = { Text("配置名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = editing.baseUrl,
                onValueChange = { viewModel.updateEditingConfig(editing.copy(baseUrl = it)) },
                label = { Text("Base URL（完整地址，如 https://api.deepseek.com）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = editing.apiKey,
                onValueChange = { viewModel.updateEditingConfig(editing.copy(apiKey = it)) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = editing.model,
                onValueChange = { viewModel.updateEditingConfig(editing.copy(model = it)) },
                label = { Text("模型名称（如 deepseek-chat）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "API 格式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val formats = listOf("OPENAI" to "OpenAI 兼容 (/v1/chat/completions)", "ANTHROPIC" to "Anthropic Claude (/v1/messages)")
            formats.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = editing.apiFormat == value,
                            onClick = { viewModel.updateEditingConfig(editing.copy(apiFormat = value)) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = editing.apiFormat == value,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(label)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        viewModel.saveEditingConfig()
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = editing.name.isNotBlank() &&
                             editing.baseUrl.isNotBlank() &&
                             editing.apiKey.isNotBlank() &&
                             editing.model.isNotBlank()
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun NotepadView(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "配置 JSON（可手动编辑自定义参数）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            minLines = 10
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("保存")
            }
        }
    }
}

private fun buildConfigJson(config: CustomApiConfig): JSONObject {
    val json = JSONObject()
    json.put("name", config.name)
    json.put("baseUrl", config.baseUrl)
    json.put("apiKey", config.apiKey)
    json.put("model", config.model)
    json.put("apiFormat", config.apiFormat)
    json.put("customParams", runCatching { JSONObject(config.customParams) }.getOrDefault(JSONObject()))
    return json
}

private fun parseNotepadToConfig(text: String, base: CustomApiConfig): CustomApiConfig? {
    return try {
        val json = JSONObject(text)
        base.copy(
            name = json.optString("name", base.name),
            baseUrl = json.optString("baseUrl", base.baseUrl),
            apiKey = json.optString("apiKey", base.apiKey),
            model = json.optString("model", base.model),
            apiFormat = json.optString("apiFormat", base.apiFormat),
            customParams = json.optJSONObject("customParams")?.toString() ?: base.customParams
        )
    } catch (_: Exception) {
        null
    }
}
