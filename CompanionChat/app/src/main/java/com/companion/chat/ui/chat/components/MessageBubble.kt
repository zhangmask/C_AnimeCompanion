package com.companion.chat.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageQuote
import com.companion.chat.data.model.MessageRole
import com.companion.chat.ui.theme.UserBubbleColor
import com.companion.chat.ui.theme.AssistantBubbleColor
import com.companion.chat.ui.theme.UserBubbleText
import com.companion.chat.ui.theme.AssistantBubbleText
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息气泡。长按行为：
 * 1. 回调 onEnterSelectMode(message.id)，由 ChatScreen 在全屏 Overlay 层绘制悬浮工具栏
 *    （Overlay 层确保悬浮工具栏不被 LazyColumn 兄弟气泡盖住，始终在最顶层）
 * 2. 进入 SelectionContainer 选择模式，用户可框选任意文字
 * 3. 悬浮工具栏的 删除/引用/播放 三个按钮 + 引用编辑对话框 + 删除确认对话框
 *    全部在 ChatScreen Overlay 层实现，由 ViewModel.selectingMessageId 驱动
 * 4. 引用把选中文字（或整条内容）回传给 ViewModel，进入输入框上方的引用预览
 * 5. 播放调用 ViewModel.speakMessage 播放该条文字
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    assistantAvatarUri: String? = null,
    userAvatarUri: String? = null,
    onAssistantAvatarClick: () -> Unit = {},
    onUserAvatarClick: () -> Unit = {},
    onEnterSelectMode: (String) -> Unit = {},
    onSelectionChanged: (Boolean) -> Unit = {},
    isHighlighted: Boolean = false,
    isSelectable: Boolean = false,
    onPositioned: (IntOffset) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    var showFullScreenImage by remember { mutableStateOf<android.net.Uri?>(null) }

    Box(modifier = modifier
        .fillMaxWidth()
        .onGloballyPositioned { coords ->
            // 上报气泡在窗口中的坐标，供 ChatScreen Popup 定位悬浮工具栏
            val pos = coords.positionInWindow()
            onPositioned(IntOffset(pos.x.roundToInt(), pos.y.roundToInt()))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .then(
                    if (isHighlighted) Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ) else Modifier
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                AvatarIcon(isUser = false, avatarUri = assistantAvatarUri, onClick = onAssistantAvatarClick.takeIf { it != {} })
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .fillMaxWidth(0.82f),
                    horizontalAlignment = Alignment.Start
                ) {
                    BubbleContent(
                        message = message,
                        isUser = false,
                        isSelectable = isSelectable,
                        onImageClick = { showFullScreenImage = it },
                        onEnterSelectMode = { onEnterSelectMode(message.id) },
                        onExitSelectMode = { onSelectionChanged(false) }
                    )
                }
            }

            if (isUser) {
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth(0.82f),
                    horizontalAlignment = Alignment.End
                ) {
                    BubbleContent(
                        message = message,
                        isUser = true,
                        isSelectable = isSelectable,
                        onImageClick = { showFullScreenImage = it },
                        onEnterSelectMode = { onEnterSelectMode(message.id) },
                        onExitSelectMode = { onSelectionChanged(false) }
                    )
                }
                AvatarIcon(isUser = true, avatarUri = userAvatarUri, onClick = onUserAvatarClick.takeIf { it != {} })
            }
        }
    }

    showFullScreenImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { showFullScreenImage = null },
            confirmButton = {
                TextButton(onClick = { showFullScreenImage = null }) {
                    Text(Strings.txt(StringsKey.close))
                }
            },
            text = {
                AsyncImage(
                    model = uri,
                    contentDescription = Strings.txt(StringsKey.msg_image_loading),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

/** 引用编辑对话框：用户可在此移动/调整框选的文字片段，确认后作为引用 */
@Composable
fun QuoteEditDialog(
    sourceRole: MessageRole,
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedText by remember { mutableStateOf(initialText) }
    val sourceLabel = if (sourceRole == MessageRole.USER) {
        Strings.txt(StringsKey.quote_from_user)
    } else {
        Strings.txt(StringsKey.quote_from_assistant)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.txt(StringsKey.quote_edit_title)) },
        text = {
            Column {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = Strings.txt(StringsKey.quote_edit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(10.dp))
                BasicTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = editedText.isNotBlank(),
                onClick = { onConfirm(editedText.trim()) }
            ) {
                Text(Strings.txt(StringsKey.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.txt(StringsKey.cancel))
            }
        }
    )
}

/** 微信风格上浮悬浮工具栏：删除 / 引用 / 播放（播放中显示暂停） */
@Composable
fun FloatingActionBar(
    onDelete: () -> Unit,
    onQuote: () -> Unit,
    onSpeak: () -> Unit,
    onCopy: () -> Unit = {},
    isSpeaking: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionItem(
                icon = Icons.AutoMirrored.Filled.Reply,
                label = Strings.txt(StringsKey.msg_action_quote),
                onClick = onQuote
            )
            FloatingActionItem(
                icon = Icons.Default.ContentCopy,
                label = Strings.txt(StringsKey.msg_copy),
                onClick = onCopy
            )
            FloatingActionItem(
                icon = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = Strings.txt(
                    if (isSpeaking) StringsKey.msg_action_pause else StringsKey.msg_action_speak
                ),
                onClick = onSpeak
            )
            FloatingActionItem(
                icon = Icons.Default.Delete,
                label = Strings.txt(StringsKey.msg_action_delete),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun FloatingActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun AvatarIcon(isUser: Boolean, avatarUri: String? = null, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = if (isUser) Strings.txt(StringsKey.msg_avatar_me) else Strings.txt(StringsKey.msg_avatar_assistant),
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                contentDescription = if (isUser) Strings.txt(StringsKey.msg_avatar_me) else Strings.txt(StringsKey.msg_avatar_assistant),
                tint = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BubbleContent(
    message: ChatMessage,
    isUser: Boolean,
    isSelectable: Boolean = false,
    onImageClick: (android.net.Uri) -> Unit,
    onEnterSelectMode: () -> Unit = {},
    onExitSelectMode: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = if (isUser) 16.dp else 4.dp,
            topEnd = if (isUser) 4.dp else 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        ),
        color = if (isUser) UserBubbleColor
        else AssistantBubbleColor,
        tonalElevation = 0.dp,
        shadowElevation = if (isUser) 2.dp else 1.dp,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .then(
                // 长按进入文本选择模式：含文字 OR 含图片的消息都可长按
                // （纯图片消息 content 为空，但用户仍需删除/播放能力，故放宽条件）
                if (!isSelectable && (message.content.isNotEmpty() || message.images.isNotEmpty())) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { onEnterSelectMode() }
                    )
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // 引用预览（若本条消息携带引用）
            message.quote?.let { quote ->
                QuotePreviewInline(quote = quote, isUser = isUser)
                if (message.content.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                }
            }

            if (message.images.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (message.content.isNotEmpty()) Modifier.padding(bottom = 8.dp) else Modifier
                ) {
                    message.images.forEach { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = Strings.txt(StringsKey.msg_image_loading),
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(uri) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            val contentColor = if (isUser) {
                UserBubbleText
            } else {
                AssistantBubbleText
            }
            // Show typing dots animation when AI is streaming with no content yet
            if (!isUser && message.isStreaming && message.content.isEmpty()) {
                TypingIndicator()
            } else if (message.content.isNotEmpty()) {
                if (isSelectable) {
                    // 选择模式：激活 SelectionContainer，用户可框选文字
                    SelectionContainer {
                        if (isUser) {
                            Text(
                                text = message.content,
                                color = contentColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            MarkdownMessageText(
                                text = message.content,
                                color = contentColor
                            )
                        }
                    }
                } else {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        MarkdownMessageText(
                            text = message.content,
                            color = contentColor
                        )
                    }
                }
            }

            if (!message.isStreaming) {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isUser) UserBubbleText
                    else AssistantBubbleText).copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }

            // 选择模式下底部提示
            if (isSelectable) {
                Text(
                    text = Strings.txt(StringsKey.msg_select_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                        .clickable { onExitSelectMode() }
                )
            }
        }
    }
}

/** 气泡内引用预览：上方来源行 + 引文（带左侧竖条） */
@Composable
private fun QuotePreviewInline(quote: MessageQuote, isUser: Boolean) {
    val sourceLabel = if (quote.sourceRole == MessageRole.USER) {
        Strings.txt(StringsKey.quote_from_user)
    } else {
        Strings.txt(StringsKey.quote_from_assistant)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = (if (isUser) UserBubbleText else AssistantBubbleText).copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(start = 0.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .padding(0.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            (if (isUser) UserBubbleText else AssistantBubbleText).copy(alpha = 0.6f)
                        )
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isUser) UserBubbleText else AssistantBubbleText).copy(alpha = 0.7f)
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) UserBubbleText else AssistantBubbleText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

const val QUOTE_TEXT_LIMIT = 240

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
