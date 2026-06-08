package com.companion.chat.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.companion.chat.ui.theme.BrandOutlineLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onGenerateImage: () -> Unit,
    onVoiceInput: () -> Unit,
    selectedImages: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    isVoiceStarting: Boolean = false,
    isVoiceListening: Boolean,
    isVoiceAutoSending: Boolean = false,
    isGenerating: Boolean = false,
    isImageGenerating: Boolean = false,
    isVoiceSpeaking: Boolean = false,
    canVoiceOutput: Boolean = false,
    onVoiceOutput: () -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 0.dp,
        color = Color.White,
        border = BorderStroke(1.dp, BrandOutlineLight),
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedImages.isNotEmpty()) {
                    SelectedImagePreviewRow(
                        selectedImages = selectedImages,
                        onRemoveImage = onRemoveImage
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    ChatToolIconButton(
                        onClick = onPickImage,
                        icon = Icons.Default.AddPhotoAlternate,
                        contentDescription = "上传图片"
                    )
                    ChatToolIconButton(
                        onClick = onGenerateImage,
                        enabled = !isImageGenerating,
                        icon = Icons.Default.AutoAwesome,
                        contentDescription = if (isImageGenerating) "图片生成中" else "根据输入生成图片",
                        active = isImageGenerating
                    )
                    Spacer(Modifier.width(2.dp))
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 112.dp)
                            .padding(horizontal = 4.dp, vertical = 11.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Box {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = inputPlaceholder(
                                            isVoiceStarting = isVoiceStarting,
                                            isVoiceListening = isVoiceListening,
                                            isVoiceAutoSending = isVoiceAutoSending
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    ChatToolIconButton(
                        onClick = {
                            if (isVoiceSpeaking) {
                                onStopSpeaking()
                            } else {
                                onVoiceOutput()
                            }
                        },
                        enabled = isVoiceSpeaking || canVoiceOutput,
                        icon = if (isVoiceSpeaking) {
                            Icons.Default.Stop
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (isVoiceSpeaking) "停止播放" else "朗读最近回复",
                        active = isVoiceSpeaking
                    )
                    Spacer(Modifier.width(4.dp))
                    if (inputText.isNotBlank() || selectedImages.isNotEmpty()) {
                        FilledIconButton(
                            onClick = onSend,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送"
                            )
                        }
                    } else {
                        VoicePrimaryButton(
                            isVoiceStarting = isVoiceStarting,
                            isVoiceListening = isVoiceListening,
                            isVoiceAutoSending = isVoiceAutoSending,
                            isGenerating = isGenerating,
                            onVoiceInput = onVoiceInput
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedImagePreviewRow(
    selectedImages: List<Uri>,
    onRemoveImage: (Uri) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        selectedImages.forEach { uri ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "选中的图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                FilledIconButton(
                    onClick = { onRemoveImage(uri) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除图片",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatToolIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (active) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun inputPlaceholder(
    isVoiceStarting: Boolean,
    isVoiceListening: Boolean,
    isVoiceAutoSending: Boolean
): String {
    return when {
        isVoiceStarting -> "正在启动语音识别..."
        isVoiceListening -> "正在听..."
        isVoiceAutoSending -> "正在发送语音..."
        else -> "输入消息..."
    }
}

@Composable
private fun VoicePrimaryButton(
    isVoiceStarting: Boolean,
    isVoiceListening: Boolean,
    isVoiceAutoSending: Boolean,
    isGenerating: Boolean,
    onVoiceInput: () -> Unit
) {
    val active = isVoiceStarting || isVoiceListening

    FilledIconButton(
        onClick = onVoiceInput,
        modifier = Modifier.size(44.dp),
        enabled = !isVoiceAutoSending && !isGenerating,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (active) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = when {
                active -> "停止语音输入"
                isVoiceAutoSending -> "正在发送语音"
                isGenerating -> "正在生成回复"
                else -> "开始语音输入"
            }
        )
    }
}
