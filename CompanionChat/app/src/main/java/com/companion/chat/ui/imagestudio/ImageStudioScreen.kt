package com.companion.chat.ui.imagestudio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.AppViewModelFactory
import com.companion.chat.ui.imagestudio.components.ImageMessageCard
import com.companion.chat.ui.imagestudio.components.ImageStudioInputBar
import com.companion.chat.ui.imagestudio.components.RoleGalleryBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    roleId: Long,
    onBack: () -> Unit,
    viewModel: ImageStudioViewModel = viewModel(factory = AppViewModelFactory(LocalContext.current.applicationContext as android.app.Application))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(roleId) {
        viewModel.loadRoleCard(roleId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ImageStudioEvent.Toast -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ImageStudioEvent.ScrollToBottom -> {
                    val target = uiState.messages.size
                    if (target > 0) {
                        listState.animateScrollToItem(target - 1)
                    }
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addUploadedImageToGallery(uri)
        }
    }

    val inputImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addSelectedImage(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.roleCard?.name ?: "图像工作室",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "图像工作室",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "输入描述来生成角色形象\n长按生成的图片可保存或引用修改",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.id }
                        ) { message ->
                            ImageMessageCard(
                                message = message,
                                onReference = { msg ->
                                    viewModel.setReference(msg)
                                },
                                onSaveToGallery = { uri ->
                                    viewModel.saveToGallery(uri)
                                },
                                onDelete = { id ->
                                    viewModel.deleteMessage(id)
                                },
                                onRetry = { id ->
                                    viewModel.retryMessage(id)
                                }
                            )
                        }
                    }
                }
            }

            RoleGalleryBar(
                galleryImages = uiState.galleryImages,
                onUploadClick = {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImageLongPress = { imageUri ->
                    viewModel.deleteGalleryImage(imageUri)
                }
            )

            ImageStudioInputBar(
                inputText = uiState.inputText,
                onInputChange = viewModel::updateInputText,
                onGenerate = viewModel::generateImage,
                onPickImage = {
                    inputImagePickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onVoiceInput = viewModel::toggleVoiceListening,
                selectedImages = uiState.selectedImages,
                onRemoveImage = viewModel::removeSelectedImage,
                isGenerating = uiState.isGenerating,
                isVoiceListening = uiState.isVoiceListening,
                referencePrompt = uiState.referenceMessage?.prompt,
                onClearReference = viewModel::clearReference
            )
        }
    }
}
