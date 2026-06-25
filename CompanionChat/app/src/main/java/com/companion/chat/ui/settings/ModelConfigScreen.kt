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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.engine.BackendType
import com.companion.chat.data.engine.ModelRuntime
import com.companion.chat.data.image.ImageGenerationConfig
import com.companion.chat.data.image.ImageGenerationProvider
import com.companion.chat.data.image.DreamLiteModelStatus
import com.companion.chat.data.image.StableDiffusionModelStatus
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandSurfaceContainer
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    modifier: Modifier = Modifier,
    scrollTarget: ModelConfigScrollTarget = ModelConfigScrollTarget.DEFAULT,
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
                        text = Strings.txt(StringsKey.model_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings.txt(StringsKey.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()

        // 根据 scrollTarget 滚动到对应位置
        LaunchedEffect(scrollTarget) {
            if (scrollTarget != ModelConfigScrollTarget.DEFAULT) {
                // 延迟一下等待布局完成
                kotlinx.coroutines.delay(300)
                when (scrollTarget) {
                    ModelConfigScrollTarget.CONTEXT_WINDOW -> {
                        // 滚动到上下文窗口区域（大约在页面中部）
                        scrollState.animateScrollTo(scrollState.maxValue / 3)
                    }
                    ModelConfigScrollTarget.IMAGE_GENERATION -> {
                        // 滚动到图片生成区域（大约在页面后部）
                        scrollState.animateScrollTo(scrollState.maxValue * 2 / 3)
                    }
                    else -> {}
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BrandSurfaceContainer)
                .verticalScroll(scrollState),
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
                    text = Strings.txt(StringsKey.model_context_window),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.txt(StringsKey.model_context_desc, retainedRounds),
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
                        text = Strings.txt(StringsKey.model_backend),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ModelRuntimeOptionItem(
                    title = "llama.cpp GGUF",
                    description = Strings.txt(StringsKey.model_backend_llama_desc),
                    selected = modelConfig.runtime == ModelRuntime.LLAMA_CPP_GGUF,
                    onClick = {
                        viewModel.setRuntime(ModelRuntime.LLAMA_CPP_GGUF)
                        onModelConfigChanged()
                    }
                )
                ModelRuntimeOptionItem(
                    title = "LiteRT-LM",
                    description = Strings.txt(StringsKey.model_backend_litert_desc),
                    selected = modelConfig.runtime == ModelRuntime.LITERT_LM,
                    onClick = {
                        viewModel.setRuntime(ModelRuntime.LITERT_LM)
                        onModelConfigChanged()
                    }
                )

                // GPU 加速开关（仅 LiteRT 后端有效）
                if (modelConfig.runtime == ModelRuntime.LITERT_LM) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Strings.txt(StringsKey.model_gpu),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Strings.txt(StringsKey.model_gpu_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = modelConfig.backend == BackendType.GPU,
                            onCheckedChange = { useGpu ->
                                viewModel.setBackend(if (useGpu) BackendType.GPU else BackendType.CPU)
                                onModelConfigChanged()
                            }
                        )
                    }
                }

                ModelConfigField(Strings.txt(StringsKey.model_path), modelConfig.modelPath) {
                    viewModel.updateModelPath(it)
                }
                Text(
                    text = Strings.txt(StringsKey.model_path_hint, resolvedModelPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = Strings.txt(StringsKey.image_mmproj_status, if (mmprojReady) Strings.txt(StringsKey.model_status_ready) else Strings.txt(StringsKey.model_status_missing)) + "\n$resolvedMmprojPath",
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
                    Text(Strings.txt(StringsKey.model_apply))
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
                text = Strings.txt(StringsKey.model_context_hint),
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
                    text = Strings.txt(StringsKey.image_provider_config),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                ImageProviderOptionItem(
                    title = Strings.txt(StringsKey.image_http_title),
                    description = Strings.txt(StringsKey.image_http_desc),
                    selected = imageConfig.provider == ImageGenerationProvider.HTTP,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.HTTP)
                    }
                )
                ImageProviderOptionItem(
                    title = Strings.txt(StringsKey.image_local_sd_title),
                    description = Strings.txt(StringsKey.image_local_sd_desc),
                    selected = imageConfig.provider == ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP)
                    }
                )
                ImageProviderOptionItem(
                    title = Strings.txt(StringsKey.image_dreamlite_title),
                    description = Strings.txt(StringsKey.image_dreamlite_desc),
                    selected = imageConfig.provider == ImageGenerationProvider.LOCAL_DREAMLITE,
                    onClick = {
                        viewModel.setImageProvider(ImageGenerationProvider.LOCAL_DREAMLITE)
                    }
                )
                ImageConfigField(Strings.txt(StringsKey.model_path), imageConfig.localModelPath) {
                    viewModel.updateLocalModelPath(it)
                }
                Text(
                    text = when (imageConfig.provider) {
                        ImageGenerationProvider.LOCAL_STABLE_DIFFUSION_CPP ->
                            Strings.txt(StringsKey.image_status_sd, stableDiffusionModelStatus.displayName(LocalLanguage.current))
                        else -> Strings.txt(StringsKey.image_status_dreamlite, dreamLiteModelStatus.displayName(LocalLanguage.current))
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
                ImageConfigField(Strings.txt(StringsKey.image_local_width), imageConfig.localWidth.toString()) {
                    viewModel.updateLocalWidth(it)
                }
                ImageConfigField(Strings.txt(StringsKey.image_local_height), imageConfig.localHeight.toString()) {
                    viewModel.updateLocalHeight(it)
                }
                ImageConfigField(Strings.txt(StringsKey.image_local_steps), imageConfig.localSteps.toString()) {
                    viewModel.updateLocalSteps(it)
                }
                ImageConfigField(Strings.txt(StringsKey.image_local_cfg), imageConfig.localCfgScale.toString()) {
                    viewModel.updateLocalCfgScale(it)
                }
                ImageConfigField(Strings.txt(StringsKey.image_local_seed), imageConfig.localSeed?.toString().orEmpty()) {
                    viewModel.updateLocalSeed(it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = imageConfig.localUseVulkan,
                        onCheckedChange = { viewModel.setLocalUseVulkan(it) }
                    )
                    Text(
                        text = Strings.txt(StringsKey.image_enable_vulkan),
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
                    text = Strings.txt(StringsKey.image_template_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun DreamLiteModelStatus.displayName(lang: AppLanguage): String {
    return when (this) {
        DreamLiteModelStatus.Ready -> Strings.get(lang, StringsKey.model_ready_generate)
        DreamLiteModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.model_dir_not_configured)
        is DreamLiteModelStatus.InvalidConfig -> Strings.get(lang, StringsKey.model_invalid_config, message)
        is DreamLiteModelStatus.MissingFiles -> Strings.get(lang, StringsKey.model_missing_files, fileNames.joinToString())
    }
}

private fun StableDiffusionModelStatus.displayName(lang: AppLanguage): String {
    return when (this) {
        is StableDiffusionModelStatus.Ready -> Strings.get(lang, StringsKey.model_package_ready, config.modelName)
        StableDiffusionModelStatus.DirectoryNotConfigured -> Strings.get(lang, StringsKey.model_dir_not_configured)
        is StableDiffusionModelStatus.InvalidConfig -> Strings.get(lang, StringsKey.model_invalid_config, message)
        is StableDiffusionModelStatus.MissingFiles -> Strings.get(lang, StringsKey.model_missing_files, fileNames.joinToString())
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
                text = Strings.txt(StringsKey.context_retain_label, rounds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = Strings.txt(StringsKey.context_compress_hint, rounds * 2 + 10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
