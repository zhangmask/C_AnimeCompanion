package com.companion.chat.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.companion.chat.data.model.MessageQuote
import com.companion.chat.data.model.MessageRole
import com.companion.chat.ui.theme.BrandOutlineLight
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onGenerateImage: () -> Unit,
    onSuggestReply: () -> Unit = {},
    onVoiceInput: () -> Unit,
    selectedImages: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    quote: MessageQuote? = null,
    onClearQuote: () -> Unit = {},
    onLocateQuote: () -> Unit = {},
    inputHint: String = Strings.txt(StringsKey.hint_input_msg),
    isVoiceStarting: Boolean = false,
    isVoiceListening: Boolean,
    isVoiceAutoSending: Boolean = false,
    isGenerating: Boolean = false,
    isImageGenerating: Boolean = false,
    isSuggesting: Boolean = false,
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

                if (quote != null) {
                    QuotePreviewBar(quote = quote, onClear = onClearQuote, onLocate = onLocateQuote)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    ChatToolIconButton(
                        onClick = onPickImage,
                        icon = Icons.Default.AddPhotoAlternate,
                        contentDescription = Strings.txt(StringsKey.input_pick_image)
                    )
                    ChatToolIconButton(
                        onClick = onGenerateImage,
                        enabled = !isImageGenerating && !isGenerating,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = Strings.txt(StringsKey.input_generate_image),
                        active = isImageGenerating
                    )
                    Spacer(Modifier.width(2.dp))
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        enabled = !isSuggesting && !isImageGenerating,
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
                                        text = inputHint,
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
                        contentDescription = if (isVoiceSpeaking) Strings.txt(StringsKey.input_stop_reading) else Strings.txt(StringsKey.input_read_aloud),
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
                                contentDescription = Strings.txt(StringsKey.input_send)
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
                    contentDescription = Strings.txt(StringsKey.drawer_search_hint),
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
                        contentDescription = Strings.txt(StringsKey.close),
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
                active -> Strings.txt(StringsKey.input_stop)
                isVoiceAutoSending -> Strings.txt(StringsKey.input_voice)
                isGenerating -> Strings.txt(StringsKey.chat_status_generating)
                else -> Strings.txt(StringsKey.input_voice)
            }
        )
    }
}

/** 输入框上方引用预览条：来源标签 + 引文摘要 + 取消按钮；点击来源行定位到原消息 */
@Composable
private fun QuotePreviewBar(quote: MessageQuote, onClear: () -> Unit, onLocate: () -> Unit = {}) {
    val sourceLabel = if (quote.sourceRole == MessageRole.USER) {
        Strings.txt(StringsKey.quote_from_user)
    } else {
        Strings.txt(StringsKey.quote_from_assistant)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧竖条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onLocate)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = Strings.txt(StringsKey.quote_locate),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 取消引用按钮
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = Strings.txt(StringsKey.quote_clear),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
