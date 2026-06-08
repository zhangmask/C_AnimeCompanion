package com.companion.chat.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.engine.ModelRuntime
import com.companion.chat.data.image.ImageGenerationConfig
import com.companion.chat.data.image.ImageGenerationProvider
import com.companion.chat.data.image.DreamLiteModelStatus
import com.companion.chat.data.image.StableDiffusionModelStatus
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandSurfaceContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onModelConfigChanged: () -> Unit = {},
    viewModel: ModelConfigViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val retainedRounds = uiState.retainedRounds
    val modelConfig = uiState.modelConfig
    val imageConfig = uiState.imageConfig
    val dreamLiteModelStatus = uiState.dreamLiteModelStatus
    val stableDiffusionModelStatus = uiState.stableDiffusionModelStatus
    val options = listOf(3, 5, 10, 15, 20)
    val resolvedModelPath = uiState.resolvedModelPath
    val resolvedMmprojPath = uiState.resolvedMmprojPath
    val mmprojReady = uiState.isMmprojReady

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "模型配置",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BrandSurfaceContainer)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "上下文窗口大小",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前保留最近 $retainedRounds 轮完整对话。\n修改后会在下一次发送消息时生效。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "推理后端",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ModelRuntimeOptionItem(
                    title = "llama.cpp GGUF",
                    description = "默认文本后端，读取外部 GGUF uncensor 模型。",
                    selected = modelConfig.runtime == ModelRuntime.LLAMA_CPP_GGUF,
                    onClick = {
                        viewModel.setRuntime(ModelRuntime.LLAMA_CPP_GGUF)
                        onModelConfigChanged()
                    }
                )
                ModelRuntimeOptionItem(
                    title = "LiteRT-LM",
                    description = "可选多模态后端，继续支持图片输入链路。",
                    selected = modelConfig.runtime == ModelRuntime.LITERT_LM,
                    onClick = {
                        viewModel.setRuntime(ModelRuntime.LITERT_LM)
                        onModelConfigChanged()
                    }
                )
                ModelConfigField("模型路径", modelConfig.modelPath) {
                    viewModel.updateModelPath(it)
                }
                Text(
                    text = "留空使用默认路径：$resolvedModelPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "图片 projector：${if (mmprojReady) "已就绪" else "缺失"}\n$resolvedMmprojPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mmprojReady) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                ModelConfigField("Context Size", modelConfig.contextSize.toString()) {
                    viewModel.updateContextSize(it)
                }
                ModelConfigField("Max Tokens", modelConfig.maxTokens.toString()) {
                    viewModel.updateMaxTokens(it)
                }
                ModelConfigField("Temperature", modelConfig.temperature.toString()) {
                    viewModel.updateTemperature(it)
                }
                ModelConfigField("Top K", modelConfig.topK.toString()) {
                    viewModel.updateTopK(it)
                }
                ModelConfigField("Top P", modelConfig.topP.toString()) {
                    viewModel.updateTopP(it)
                }
                androidx.compose.material3.Button(
                    onClick = onModelConfigChanged,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("应用模型配置")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
            ) {
                options.forEach { option ->
                    ContextWindowOptionItem(
                        rounds = option,
                        selected = retainedRounds == option,
                        onClick = {
                            viewModel.updateRetainedRounds(option)
                        }
                    )
                    if (option != options.last()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            Text(
                text = "建议范围 3~20。轮数越大，保留原始上下文越多；轮数越小，越早触发压缩。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "图片生成 Provider 配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                ImageProviderOptionItem(
                    title = "HTTP 联网生成",
                    description = "使用通用 HTTP 图片接口，配置可真实生成图片",
                    selected = imageConfig.provider == ImageGenerationProvider.HTTP,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.HTTP)
                    }
                )
                ImageProviderOptionItem(
                    title = "本地 SD1.5 Hyper-SD",
                    description = "stable-diffusion.cpp + Vulkan，本地私有出图",
                    selected = imageConfig.provider == ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP)
                    }
                )
                ImageProviderOptionItem(
                    title = "本地 DreamLite",
                    description = "端侧接入框架已准备，等待官方权重/端侧包",
                    selected = imageConfig.provider == ImageGenerationProvider.LOCAL_DREAMLITE,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.LOCAL_DREAMLITE)
                    }
                )
                ImageConfigField("本地模型路径", imageConfig.localModelPath) {
                    viewModel.updateLocalModelPath(it)
                }
                Text(
                    text = when (imageConfig.provider) {
                        ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP ->
                            "Stable Diffusion 状态：${stableDiffusionModelStatus.displayName()}"
                        else -> "DreamLite 状态：${dreamLiteModelStatus.displayName()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        dreamLiteModelStatus is DreamLiteModelStatus.Ready ||
                        stableDiffusionModelStatus is StableDiffusionModelStatus.Ready
                    ) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
                ImageConfigField("本地宽度", imageConfig.localWidth.toString()) {
                    viewModel.updateLocalWidth(it)
                }
                ImageConfigField("本地高度", imageConfig.localHeight.toString()) {
                    viewModel.updateLocalHeight(it)
                }
                ImageConfigField("本地 Steps", imageConfig.localSteps.toString()) {
                    viewModel.updateLocalSteps(it)
                }
                ImageConfigField("本地 CFG Scale", imageConfig.localCfgScale.toString()) {
                    viewModel.updateLocalCfgScale(it)
                }
                ImageConfigField("本地 Seed（留空随机）", imageConfig.localSeed?.toString().orEmpty()) {
                    viewModel.updateLocalSeed(it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = imageConfig.localUseVulkan,
                        onCheckedChange = { viewModel.setLocalUseVulkan(it) }
                    )
                    Text(
                        text = "启用 Vulkan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                ImageConfigField("Base URL", imageConfig.baseUrl) {
                    viewModel.updateImageBaseUrl(it)
                }
                ImageConfigField("API Key", imageConfig.apiKey) {
                    viewModel.updateImageApiKey(it)
                }
                ImageConfigField("Model", imageConfig.model) {
                    viewModel.updateImageModel(it)
                }
                ImageConfigField("Request Template", imageConfig.requestTemplate, minLines = 3) {
                    viewModel.updateRequestTemplate(it)
                }
                ImageConfigField("Response Image Field Path", imageConfig.responseImageFieldPath) {
                    viewModel.updateResponseImageFieldPath(it)
                }
                ImageConfigField("Timeout Millis", imageConfig.timeoutMillis.toString()) {
                    viewModel.updateTimeoutMillis(it)
                }
                Text(
                    text = "模板支持 {{model}} 与 {{prompt}}。响应字段示例：data.0.url 或 data.0.b64_json。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun DreamLiteModelStatus.displayName(): String {
    return when (this) {
        DreamLiteModelStatus.Ready -> "模型就绪，可以生成图片"
        DreamLiteModelStatus.DirectoryNotConfigured -> "模型目录未配置"
        is DreamLiteModelStatus.InvalidConfig -> "配置无效：$message"
        is DreamLiteModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"
    }
}

private fun StableDiffusionModelStatus.displayName(): String {
    return when (this) {
        is StableDiffusionModelStatus.Ready -> "模型包已就绪：${config.modelName}"
        StableDiffusionModelStatus.DirectoryNotConfigured -> "模型目录未配置"
        is StableDiffusionModelStatus.InvalidConfig -> "配置无效：$message"
        is StableDiffusionModelStatus.MissingFiles -> "文件缺失：${fileNames.joinToString()}"
    }
}

@Composable
private fun ImageProviderOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = BrandPrimary,
                unselectedColor = BrandOutlineVariant
            )
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImageConfigField(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandPrimary,
            unfocusedBorderColor = BrandOutlineVariant,
            focusedLabelColor = BrandPrimary,
            cursorColor = BrandPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun ModelConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandPrimary,
            unfocusedBorderColor = BrandOutlineVariant,
            focusedLabelColor = BrandPrimary,
            cursorColor = BrandPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun ModelRuntimeOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = BrandPrimary,
                unselectedColor = BrandOutlineVariant
            )
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContextWindowOptionItem(
    rounds: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = BrandPrimary,
                unselectedColor = BrandOutlineVariant
            )
        )
        Spacer(modifier = Modifier.height(0.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "保留最近 $rounds 轮",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "压缩阈值约为 ${rounds * 2 + 10} 条消息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
