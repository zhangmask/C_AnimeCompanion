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
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.engine.InferenceState
import com.companion.chat.data.image.ImageGenerationState
import com.companion.chat.data.model.MessageQuote
import com.companion.chat.data.model.MessageRole
import com.companion.chat.ui.chat.components.ChatInputBar
import com.companion.chat.ui.chat.components.ConversationDrawerSheet
import com.companion.chat.ui.chat.components.MessageBubble
import com.companion.chat.ui.chat.components.FloatingActionBar
import com.companion.chat.ui.chat.components.QuoteEditDialog
import com.companion.chat.ui.chat.components.QUOTE_TEXT_LIMIT
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
    onRoleCardClick: (Long) -> Unit = {},
    onUserAvatarClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSelectingText by remember { mutableStateOf(false) }
    // 记录每条消息气泡在窗口中的坐标，供悬浮工具栏 Popup 跟随定位
    val messagePositions = remember { mutableStateOf<Map<String, IntOffset>>(emptyMap()) }
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
        sessionId = uiState.currentSessionId,
        isGenerating = uiState.isGenerating || (uiState.imageGenerationState is ImageGenerationState.Generating)
    )

    // 外层 Box：把 Scaffold + 悬浮工具栏 Overlay + 对话抽屉包成兄弟，
    // Overlay 在 Scaffold 之后声明→绘制在 Scaffold 之上（最表面），不被任何消息气泡盖住
    Box(modifier = Modifier.fillMaxSize()) {
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
                val lastUserIndex = uiState.messages.indexOfLast { it.role == MessageRole.USER && !it.isStreaming }

                // 正常顺序：最旧在顶部，最新在底部
                val orderedMessages = remember(uiState.messages) {
                    uiState.messages.filter { !it.isSuggestion }
                }

                // 引用定位：highlightedMessageId 变化时滚动到被引用消息
                LaunchedEffect(uiState.highlightedMessageId, orderedMessages) {
                    val highlightId = uiState.highlightedMessageId
                    if (highlightId != null) {
                        val idx = orderedMessages.indexOfFirst { it.id == highlightId }
                        if (idx >= 0) {
                            listState.animateScrollToItem(idx)
                        }
                    }
                }

                // 去掉 reverseLayout：让上下滑 fling 阻尼对称（不再上滑一顿一顿、下滑一飞到底）
                // 用 Box 包裹 LazyColumn，在右下角叠加"返回底部"按钮
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = listState,
                    userScrollEnabled = !isSelectingText,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isImageGenerating = uiState.imageGenerationState is ImageGenerationState.Generating

                    // 只在存在 assistant 回复且该回复在最新 user 消息之后，才显示继续对话/生图按钮
                    val showQuickActions = lastAssistantIndex >= 0 &&
                        lastAssistantIndex > lastUserIndex &&
                        !uiState.isGenerating &&
                        !isImageGenerating &&
                        uiState.selectingMessageId == null

                    // Messages（正常顺序：最旧在顶部，最新在底部）
                    itemsIndexed(
                        items = orderedMessages,
                        key = { _, msg -> msg.id }
                    ) { index, message ->
                            MessageBubble(
                                message = message,
                                assistantAvatarUri = uiState.assistantAvatarUri.ifBlank { null },
                                userAvatarUri = userProfile.avatarUri.ifBlank { null },
                                onAssistantAvatarClick = {
                                    uiState.sessions.firstOrNull { it.id == uiState.currentSessionId }
                                        ?.roleCardId?.let { onRoleCardClick(it) }
                                },
                                onUserAvatarClick = onUserAvatarClick,
                                onEnterSelectMode = { msgId ->
                                    viewModel.enterSelectMode(msgId)
                                    isSelectingText = true
                                },
                                onSelectionChanged = { isSelectingText = it },
                                isHighlighted = uiState.highlightedMessageId == message.id,
                                isSelectable = uiState.selectingMessageId == message.id,
                                onPositioned = { pos ->
                                    messagePositions.value = messagePositions.value + (message.id to pos)
                                }
                            )
                    }

                    // Typing indicator（最新消息下方）
                    if ((uiState.isGenerating || isImageGenerating) && uiState.messages.none { it.isStreaming }) {
                        item(key = "typing") {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(brush = AvatarGradientPurple, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                TypingIndicator()
                            }
                        }
                    }

                    // Quick action buttons（最底部）
                    // 选择消息/删除确认时隐藏；且必须有在最新用户消息之后的 assistant 回复才显示
                    if (showQuickActions) {
                        item(key = "quick_actions") {
                            QuickActionsRow(
                                onContinueChat = viewModel::sendContinueMessage,
                                onGenerateImage = { viewModel.generateCurrentSceneImage() }
                            )
                        }
                    }
                }

                    // 返回底部按钮：当用户向上滚动查看历史时显示，点击一键回到最新消息
                    val showScrollToBottom by remember {
                        derivedStateOf {
                            // 正常布局：lastVisibleItemIndex < 最后一个 item 表示用户滚离了底部
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val total = listState.layoutInfo.totalItemsCount
                            lastVisible < total - 1
                        }
                    }
                    if (showScrollToBottom) {
                        FloatingActionButton(
                            onClick = {
                                // 回到最新消息：滚动到最后一个 item
                                coroutineScope.launch {
                                    listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = Strings.txt(StringsKey.scroll_to_bottom)
                            )
                        }
                    }
                } // Box 闭合
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
                onGenerateImage = { viewModel.generateChatSceneImage(uiState.inputText.trim()) },
                onSuggestReply = {
                    viewModel.generateSuggestion()
                },
                onVoiceInput = viewModel::toggleVoiceListening,
                selectedImages = uiState.selectedImages,
                onRemoveImage = viewModel::removeImage,
                quote = uiState.quote,
                onClearQuote = viewModel::clearQuote,
                onLocateQuote = viewModel::locateQuotedMessage,
                inputHint = uiState.inputHint,
                isVoiceStarting = uiState.isVoiceStarting,
                isVoiceListening = uiState.isVoiceListening,
                isVoiceAutoSending = uiState.isVoiceAutoSending,
                isGenerating = uiState.isGenerating,
                isImageGenerating = uiState.imageGenerationState is ImageGenerationState.Generating,
                isSuggesting = uiState.isSuggesting,
                isVoiceSpeaking = uiState.isVoiceSpeaking,
                canVoiceOutput = true, // 调试：始终允许语音输出
                onVoiceOutput = viewModel::speakLatestAssistantMessage,
                onStopSpeaking = viewModel::stopSpeaking
            )
        }
    }

    // ===== 全屏 Overlay 层：悬浮工具栏 + 引用编辑对话框 + 删除确认对话框 =====
    // 放在 Scaffold 之后，确保绘制在所有 LazyColumn 兄弟气泡之上（最顶层），不被任何消息气泡盖住
    val selectingMessage = uiState.messages.firstOrNull { it.id == uiState.selectingMessageId }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 退出选择模式时重置局部对话框状态
    LaunchedEffect(uiState.selectingMessageId) {
        if (uiState.selectingMessageId == null) {
            showDeleteDialog = false
        }
    }

    selectingMessage?.let { message ->
        // 悬浮工具栏：用 Popup 提升到系统窗口层，绝对最顶层（最表面）
        // 位置跟随被选中消息气泡（显示在气泡正上方），点击外部自动关闭
        val showBar = uiState.selectingMessageId != null && !showDeleteDialog
        if (showBar) {
            // 消息气泡在窗口中的坐标（由 MessageBubble.onPositioned 上报）
            val msgPos = messagePositions.value[message.id] ?: IntOffset(0, 0)
            // 工具栏显示在气泡上方（y 减去工具栏高度约 56dp），水平居中需要气泡中心 x
            // Popup 的 offset 是相对于 alignment 锚点的偏移；用 Alignment.TopStart + offset 精确定位
            val barOffsetY = msgPos.y - with(LocalDensity.current) { 56.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(msgPos.x, barOffsetY),
                onDismissRequest = {
                    // 点击工具栏外部时系统触发：退出选择模式
                    viewModel.exitSelectMode()
                    isSelectingText = false
                },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                FloatingActionBar(
                    onDelete = { showDeleteDialog = true },
                    onCopy = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.content))
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        viewModel.exitSelectMode()
                        isSelectingText = false
                    },
                    onQuote = {
                        // 直接引用整条消息内容，无需弹窗确认
                        viewModel.setMessageQuote(
                            message.id,
                            MessageQuote(
                                sourceRole = message.role,
                                text = message.content.take(QUOTE_TEXT_LIMIT)
                            )
                        )
                        viewModel.exitSelectMode()
                        isSelectingText = false
                    },
                    onSpeak = {
                        if (uiState.isVoiceSpeaking) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.speakMessage(message.id, message.content)
                        }
                    },
                    isSpeaking = uiState.isVoiceSpeaking
                )
            }
        }
    }

    // 删除确认对话框（Overlay 层，始终在最顶层）
    if (showDeleteDialog && selectingMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                viewModel.exitSelectMode()
                isSelectingText = false
            },
            title = { Text(Strings.txt(StringsKey.delete)) },
            text = { Text(Strings.txt(StringsKey.chat_delete_message_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteMessage(selectingMessage.id)
                    viewModel.exitSelectMode()
                    isSelectingText = false
                }) {
                    Text(Strings.txt(StringsKey.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Strings.txt(StringsKey.cancel))
                }
            }
        )
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
                           voiceProfileUri, voiceMode, voiceDisplayName, tags ->
                    showRoleEditor = false
                    viewModel.createRoleCardAndStartChat(
                        name, description, avatar, persona, speakingStyle, background,
                        rules, taboos, openingMessage, exampleDialogue, avatarImageUri,
                        galleryImageUris, imageStylePrompt,
                        voiceProfileUri, voiceMode, voiceDisplayName, tags
                    )
                }
            )
        }
    }
    } // 外层 Box 闭合：Scaffold + Overlay + 对话抽屉都在此 Box 内，Overlay 后声明→绘制在最上层
}

@Composable
private fun ScrollToLatestMessageEffect(
    listState: LazyListState,
    messages: List<com.companion.chat.data.model.ChatMessage>,
    sessionId: String,
    isGenerating: Boolean = false
) {
    val lastMsgCount = remember { mutableIntStateOf(-1) }
    val lastSessionId = remember { mutableStateOf("") }

    // 会话切换时重置滚动状态，强制滚动到最新消息
    LaunchedEffect(sessionId) {
        if (sessionId.isNotBlank() && sessionId != lastSessionId.value) {
            lastSessionId.value = sessionId
            lastMsgCount.intValue = -1  // 强制下次触发滚动
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            lastMsgCount.intValue = 0
            return@LaunchedEffect
        }

        val prevCount = lastMsgCount.intValue
        lastMsgCount.intValue = messages.size

        // Scroll to bottom when conversation switches (prevCount<0) or new message arrives
        if (prevCount < 0 || messages.size != prevCount) {
            // 正常布局：最新消息在最后，滚动到最后一个 item
            snapshotFlow { listState.layoutInfo }
                .first { layout: androidx.compose.foundation.lazy.LazyListLayoutInfo ->
                    layout.totalItemsCount > 0
                }
            delay(50L)
            listState.scrollToItem(messages.size - 1)
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
