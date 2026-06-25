package com.companion.chat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.image.ImageGenerationState
import com.companion.chat.data.model.MessageRole
import com.companion.chat.ui.chat.components.ChatInputBar
import com.companion.chat.ui.chat.components.ConversationDrawerSheet
import com.companion.chat.ui.chat.components.MessageBubble
import com.companion.chat.ui.chat.components.RoleCardEditorSheet
import com.companion.chat.ui.chat.components.TypingIndicator
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.AvatarGradientPurple
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSurfaceContainer
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandOnSurfaceVariant
import com.companion.chat.ui.theme.BrandOutlineLight
import com.companion.chat.ui.theme.BrandSuccess
import kotlinx.coroutines.delay
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(),
    bottomBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    onRoleCardClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val userProfileRepository = remember(context) {
        (context.applicationContext as com.companion.chat.CompanionChatApplication).appContainer.userProfileRepository
    }
    val userProfile by userProfileRepository.profileFlow.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.addImage(it) }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onVoicePermissionGranted()
        } else {
            viewModel.onVoicePermissionDenied()
        }
    }

    LaunchedEffect(uiState.showVoicePermissionDialog) {
        if (uiState.showVoicePermissionDialog) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.onVoicePermissionGranted()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(uiState.voiceInputError) {
        if (uiState.voiceInputError.isNotBlank()) {
            snackbarHostState.showSnackbar(uiState.voiceInputError)
            viewModel.clearVoiceInputError()
        }
    }

    LaunchedEffect(uiState.imageGenerationError) {
        if (uiState.imageGenerationError.isNotBlank()) {
            snackbarHostState.showSnackbar(uiState.imageGenerationError)
        }
    }

    // Refresh system prompt when screen resumes (e.g., returning from settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemPromptOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听 TTS 回退事件，显示 Toast
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatUiEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ScrollToLatestMessageEffect(
        listState = listState,
        messages = uiState.messages,
        isGenerating = uiState.isGenerating || (uiState.imageGenerationState is ImageGenerationState.Generating)
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.toggleSessionDrawer() }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = Strings.txt(StringsKey.drawer_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseInOutCubic),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = Strings.txt(StringsKey.tab_chat),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val engineLabel = when (val s = uiState.engineState) {
                            is InferenceState.Idle -> Strings.txt(StringsKey.chat_status_disconnected)
                            is InferenceState.Initializing -> Strings.txt(StringsKey.chat_status_loading)
                            is InferenceState.Ready -> Strings.txt(StringsKey.chat_status_ready)
                            is InferenceState.Generating -> Strings.txt(StringsKey.chat_status_generating)
                            is InferenceState.Error -> Strings.txt(StringsKey.chat_status_error)
                        }
                        val statusColor = when (uiState.engineState) {
                            is InferenceState.Ready -> BrandSuccess
                            is InferenceState.Error -> MaterialTheme.colorScheme.error
                            is InferenceState.Generating -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .alpha(
                                        if (uiState.engineState is InferenceState.Ready) pulseAlpha else 1f
                                    )
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = engineLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(
                    if (isImeVisible) Modifier.imePadding()
                    else Modifier.padding(bottom = bottomBarHeight)
                )
        ) {
            // 消息列表
            if (uiState.currentSessionId.isBlank() && uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Strings.txt(StringsKey.chat_empty_hint),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Compression status indicator
                if (uiState.isCompressingContext) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = uiState.compressionMessage.ifBlank { Strings.txt(StringsKey.chat_compressing) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                val lastAssistantIndex = uiState.messages.indexOfLast { it.role == MessageRole.ASSISTANT && !it.isStreaming }

                // reverseLayout = true: 消息从底部向上排列，最新消息自然在底部
                // 过滤掉建议消息
                val reversedMessages = remember(uiState.messages) {
                    uiState.messages.filter { !it.isSuggestion }.reversed()
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // reverseLayout: 低索引 = 视口底部
                    // 所以 typing indicator 和 quick actions 放在 itemsIndexed 前面
                    val isImageGenerating = uiState.imageGenerationState is ImageGenerationState.Generating

                    // Quick action buttons (bottom-most when visible)
                    if (lastAssistantIndex >= 0 && !uiState.isGenerating && !isImageGenerating) {
                        item(key = "quick_actions") {
                            QuickActionsRow(
                                onContinueChat = viewModel::sendContinueMessage,
                                onGenerateImage = { viewModel.generateCurrentSceneImage() }
                            )
                        }
                    }

                    // Typing indicator (below latest message)
                    if ((uiState.isGenerating || isImageGenerating) && uiState.messages.none { it.isStreaming }) {
                        item(key = "typing") {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // AI avatar
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(
                                            brush = AvatarGradientPurple,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                TypingIndicator()
                            }
                        }
                    }

                    // Messages (reversed: index 0 = latest message, near bottom)
                    itemsIndexed(
                        items = reversedMessages,
                        key = { _, msg -> msg.id }
                    ) { index, message ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(message.id) {
                            delay(50L + index * 30L) // stagger
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(350, easing = EaseOut)) +
                                    slideInVertically(animationSpec = tween(350, easing = EaseOut)) { it / 4 }
                        ) {
                            MessageBubble(
                                message = message,
                                assistantAvatarUri = uiState.assistantAvatarUri.ifBlank { null },
                                userAvatarUri = userProfile.avatarUri.ifBlank { null },
                                onAssistantAvatarClick = {
                                    uiState.sessions.firstOrNull { it.id == uiState.currentSessionId }
                                        ?.roleCardId?.let { onRoleCardClick(it) }
                                }
                            )
                        }
                    }
                }
            }

            // 输入栏
            ChatInputBar(
                inputText = uiState.inputText,
                onInputChange = viewModel::updateInputText,
                onSend = viewModel::sendMessage,
                onPickImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onGenerateImage = {
                    viewModel.generateChatSceneImage(uiState.inputText.trim())
                },
                onSuggestReply = {
                    viewModel.generateSuggestion()
                },
                onVoiceInput = viewModel::toggleVoiceListening,
                selectedImages = uiState.selectedImages,
                onRemoveImage = viewModel::removeImage,
                inputHint = uiState.inputHint,
                isVoiceStarting = uiState.isVoiceStarting,
                isVoiceListening = uiState.isVoiceListening,
                isVoiceAutoSending = uiState.isVoiceAutoSending,
                isGenerating = uiState.isGenerating,
                isImageGenerating = uiState.imageGenerationState is ImageGenerationState.Generating,
                isSuggesting = uiState.isSuggesting,
                isVoiceSpeaking = uiState.isVoiceSpeaking,
                canVoiceOutput = uiState.hasSpeakableAssistantMessage,
                onVoiceOutput = viewModel::speakLatestAssistantMessage,
                onStopSpeaking = viewModel::stopSpeaking
            )
        }
    }

    // 对话列表：覆盖全页面而非抽屉叠层，避免输入框被抬高
    if (uiState.showSessionDrawer) {
        var showRoleEditor by remember { mutableStateOf(false) }

        ConversationDrawerSheet(
            sessions = uiState.sessions,
            currentSessionId = uiState.currentSessionId,
            searchQuery = uiState.sessionSearchQuery,
            onSearchQueryChange = viewModel::updateSessionSearchQuery,
            onNewConversation = viewModel::createNewSession,
            onSessionClick = { sessionId -> viewModel.switchToSession(sessionId) },
            onDismiss = viewModel::closeSessionDrawer,
            roleCards = uiState.availableRoleCards,
            onSelectRole = { roleId -> viewModel.launchStartRoleConversation(roleId) },
            onCreateNewRole = { showRoleEditor = true },
            dateFilter = uiState.dateFilter,
            onDateFilterChange = viewModel::setDateFilter,
            editingSessionId = uiState.editingSessionId,
            editingTitle = uiState.editingTitle,
            onStartEditing = viewModel::startEditingTitle,
            onDeleteSession = viewModel::deleteSession,
            onEditingTitleChange = viewModel::updateEditingTitle,
            onConfirmEditing = viewModel::confirmEditingTitle,
            onCancelEditing = viewModel::cancelEditingTitle,
            onRoleCardClick = onRoleCardClick
        )

        if (showRoleEditor) {
            RoleCardEditorSheet(
                onDismiss = { showRoleEditor = false },
                onSave = { name, description, avatar, persona, speakingStyle, background,
                           rules, taboos, openingMessage, exampleDialogue, avatarImageUri,
                           galleryImageUris, imageStylePrompt,
                           voiceProfileUri, voiceMode, voiceDisplayName ->
                    showRoleEditor = false
                    viewModel.createRoleCardAndStartChat(
                        name, description, avatar, persona, speakingStyle, background,
                        rules, taboos, openingMessage, exampleDialogue, avatarImageUri,
                        galleryImageUris, imageStylePrompt,
                        voiceProfileUri, voiceMode, voiceDisplayName
                    )
                }
            )
        }
    }
}

@Composable
private fun ScrollToLatestMessageEffect(
    listState: LazyListState,
    messages: List<com.companion.chat.data.model.ChatMessage>,
    isGenerating: Boolean = false
) {
    val lastMsgCount = remember { mutableIntStateOf(-1) }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            lastMsgCount.intValue = 0
            return@LaunchedEffect
        }

        val prevCount = lastMsgCount.intValue
        lastMsgCount.intValue = messages.size

        // Scroll to bottom when conversation switches (big count change) or new message arrives
        if (prevCount < 0 || messages.size != prevCount) {
            // With reverseLayout, the last item of reversedMessages (oldest) is at the top;
            // scrollToItem on it ensures the newest items stay at the bottom of the viewport
            val reversedLastIndex = messages.size - 1
            snapshotFlow { listState.layoutInfo }
                .first { layout: androidx.compose.foundation.lazy.LazyListLayoutInfo ->
                    layout.totalItemsCount > 0
                }
            delay(50L)
            listState.scrollToItem(reversedLastIndex)
        }
    }
}

/**
 * Quick action buttons displayed below the latest AI message.
 * Provides "继续聊聊" and "生成此刻图片" shortcuts.
 */
@Composable
private fun QuickActionsRow(
    onContinueChat: () -> Unit,
    onGenerateImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 4.dp, bottom = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onContinueChat,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = Strings.txt(StringsKey.chat_quick_continue),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = BrandPrimaryContainer,
                labelColor = BrandPrimary,
                leadingIconContentColor = BrandPrimary
            ),
            border = null
        )

        AssistChip(
            onClick = onGenerateImage,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = Strings.txt(StringsKey.chat_quick_generate_img),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = BrandOnSurfaceVariant,
                leadingIconContentColor = BrandOnSurfaceVariant
            ),
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = BrandOutlineVariant
            )
        )
    }
}
