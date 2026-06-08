package com.companion.chat.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.companion.chat.data.model.ChatMessage
import com.companion.chat.data.model.MessageRole
import com.companion.chat.ui.theme.UserBubbleColor
import com.companion.chat.ui.theme.AssistantBubbleColor
import com.companion.chat.ui.theme.UserBubbleText
import com.companion.chat.ui.theme.AssistantBubbleText
import com.companion.chat.ui.chat.components.TypingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    var showFullScreenImage by remember { mutableStateOf<android.net.Uri?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AvatarIcon(isUser = false)
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth(0.82f),
                horizontalAlignment = Alignment.Start
            ) {
                BubbleContent(
                    message = message,
                    isUser = false,
                    onImageClick = { showFullScreenImage = it }
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
                    onImageClick = { showFullScreenImage = it }
                )
            }
            AvatarIcon(isUser = true)
        }
    }

    showFullScreenImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { showFullScreenImage = null },
            confirmButton = {
                TextButton(onClick = { showFullScreenImage = null }) {
                    Text("关闭")
                }
            },
            text = {
                AsyncImage(
                    model = uri,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Composable
private fun AvatarIcon(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
            contentDescription = if (isUser) "用户" else "AI",
            tint = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(17.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleContent(
    message: ChatMessage,
    isUser: Boolean,
    onImageClick: (android.net.Uri) -> Unit
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
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (message.images.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (message.content.isNotEmpty()) Modifier.padding(bottom = 8.dp) else Modifier
                ) {
                    message.images.forEach { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "消息图片",
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
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
