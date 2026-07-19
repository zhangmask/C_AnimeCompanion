package com.companion.chat.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.companion.chat.data.model.ConversationSession
import com.companion.chat.ui.chat.DateFilter
import com.companion.chat.ui.theme.AvatarGradients
import com.companion.chat.ui.theme.BrandOutline
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSuccess
import com.companion.chat.ui.theme.BrandSurfaceContainer
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ConversationDrawerSheet(
    sessions: List<ConversationSession>,
    currentSessionId: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewConversation: () -> Unit,
    onSessionClick: (String) -> Unit,
    onDismiss: () -> Unit,
    roleCards: List<RoleCard> = emptyList(),
    onSelectRole: (Long) -> Unit = {},
    onCreateNewRole: () -> Unit = {},
    dateFilter: DateFilter = DateFilter.ALL,
    onDateFilterChange: (DateFilter) -> Unit = {},
    editingSessionId: String = "",
    editingTitle: String = "",
    onStartEditing: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onEditingTitleChange: (String) -> Unit = {},
    onConfirmEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
    onRoleCardClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // DIAG: log at drawer entry
    LaunchedEffect(Unit) {
        android.util.Log.e("DrawerDiag", "Drawer ENTERED sessions=" + sessions.size)
    }

    val roleCardMap = remember(roleCards) { roleCards.associateBy { it.id } }

    val filteredSessions = remember(sessions, searchQuery, dateFilter, roleCardMap) {
        val searchFiltered = if (searchQuery.isBlank()) sessions
        else sessions.filter { session ->
            session.title.contains(searchQuery, ignoreCase = true) ||
            session.messages.any { msg -> msg.content.contains(searchQuery, ignoreCase = true) } ||
            session.roleCardId?.let { roleId ->
                roleCardMap[roleId]?.name?.contains(searchQuery, ignoreCase = true)
            } == true
        }
        filterByDate(searchFiltered, dateFilter)
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = screenWidth // 覆盖整个页面宽度

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Layer 1: Semi-transparent overlay (click to dismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x59000000))
                .clickable { onDismiss() }
        )

        // Layer 2: Drawer panel — single Column: header + list(weight 1f) + footer (footer occupies reserved space, never overlapped)
        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .drawBehind {
                drawLine(
                    color = Color(0xFFD8D4CC),
                    start = Offset(size.width - 1f, 0f),
                    end = Offset(size.width - 1f, size.height),
                    strokeWidth = 1f
                )
                val shadowWidth = 12.dp.toPx()
                val shadowBrush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0x1A000000)),
                    startX = size.width - shadowWidth,
                    endX = size.width
                )
                drawRect(
                    brush = shadowBrush,
                    topLeft = Offset(size.width - shadowWidth, 0f),
                    size = Size(shadowWidth, size.height)
                )
            }
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Strings.txt(StringsKey.drawer_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BrandSurfaceContainer)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = Strings.txt(StringsKey.close),
                        tint = Color(0xFF49454F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Search + Filter Button ──
            var showFilterSheet by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(Strings.txt(StringsKey.drawer_search_hint), fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = BrandOutlineVariant
                    )
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dateFilter != DateFilter.ALL) BrandPrimaryContainer else BrandSurfaceContainer)
                        .clickable { showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = Strings.txt(StringsKey.filter),
                        tint = if (dateFilter != DateFilter.ALL) BrandPrimary else Color(0xFF49454F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Show current filter label if not "all"
            if (dateFilter != DateFilter.ALL) {
                val filterLabel = when (dateFilter) {
                    DateFilter.TODAY -> Strings.txt(StringsKey.drawer_filter_today)
                    DateFilter.YESTERDAY -> Strings.txt(StringsKey.drawer_filter_yesterday)
                    DateFilter.WEEK -> Strings.txt(StringsKey.drawer_filter_week)
                    DateFilter.MONTH -> Strings.txt(StringsKey.drawer_filter_month)
                    else -> ""
                }
                Text(
                    text = Strings.txt(StringsKey.drawer_filter_label, filterLabel),
                    fontSize = 11.sp,
                    color = BrandPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                )
            }

            // Filter bottom sheet popup
            if (showFilterSheet) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showFilterSheet = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x59000000))
                            .clickable { showFilterSheet = false },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = false) { },
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            color = Color.White
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Strings.txt(StringsKey.drawer_filter_title),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { showFilterSheet = false }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, Strings.txt(StringsKey.close), tint = Color(0xFF49454F), modifier = Modifier.size(20.dp))
                                    }
                                }
                                Text(text = Strings.txt(StringsKey.drawer_filter_time_section), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49454F))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DateFilter.entries.forEach { filter ->
                                        val label = when (filter) {
                                            DateFilter.ALL -> Strings.txt(StringsKey.drawer_filter_all)
                                            DateFilter.TODAY -> Strings.txt(StringsKey.drawer_filter_today)
                                            DateFilter.YESTERDAY -> Strings.txt(StringsKey.drawer_filter_yesterday)
                                            DateFilter.WEEK -> Strings.txt(StringsKey.drawer_filter_week)
                                            DateFilter.MONTH -> Strings.txt(StringsKey.drawer_filter_month)
                                        }
                                        val isSelected = dateFilter == filter
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onDateFilterChange(filter) },
                                            label = { Text(label, fontSize = 13.sp) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BrandPrimary,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color.White,
                                                labelColor = Color(0xFF49454F)
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = if (isSelected) Color.Transparent else BrandOutlineVariant,
                                                selectedBorderColor = Color.Transparent,
                                                borderWidth = 1.dp,
                                                selectedBorderWidth = 1.dp,
                                                enabled = true,
                                                selected = isSelected
                                            )
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clickable { showFilterSheet = false },
                                    shape = RoundedCornerShape(12.dp),
                                    color = BrandPrimary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(Strings.txt(StringsKey.confirm), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Conversation List (weight 1f: occupies remaining space between header and footer) ──
            var actionsVisibleIndex by remember { mutableIntStateOf(-1) }

            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> Strings.txt(StringsKey.drawer_empty_no_match)
                            dateFilter != DateFilter.ALL -> Strings.txt(StringsKey.drawer_empty_period)
                            else -> Strings.txt(StringsKey.drawer_empty_none)
                        },
                        fontSize = 14.sp,
                        color = BrandOutline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = filteredSessions,
                        key = { _, session -> session.id }
                    ) { index, session ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(120L + index * 60L)
                            visible = true

                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            val roleAvatarUri = session.roleCardId?.let { roleId ->
                                roleCardMap[roleId]?.avatarImageUri
                            }.orEmpty()

                            SessionItem(
                                session = session,
                                index = index,
                                isActive = session.id == currentSessionId,
                                isEditing = session.id == editingSessionId,
                                editingTitle = editingTitle,
                                showActions = actionsVisibleIndex == index,
                                roleCardAvatarUri = roleAvatarUri,
                                onRoleCardClick = {
                                    session.roleCardId?.let { roleId ->
                                        onSessionClick(session.id)
                                        onRoleCardClick(roleId)
                                    }
                                },
                                onClick = {
                                    actionsVisibleIndex = -1
                                    onSessionClick(session.id)
                                },
                                onLongPress = {
                                    actionsVisibleIndex =
                                        if (actionsVisibleIndex == index) -1 else index
                                },
                                onStartEditing = { onStartEditing(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                                onEditingTitleChange = onEditingTitleChange,
                                onConfirmEditing = {
                                    actionsVisibleIndex = -1
                                    onConfirmEditing()
                                },
                                onCancelEditing = {
                                    actionsVisibleIndex = -1
                                    onCancelEditing()
                                }
                            )
                        }
                    }
                    // Footer as LAST LazyColumn item — always at bottom of list, never pushed off-screen
                    item {
                        var showCharacterPicker by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { showCharacterPicker = true },
                                shape = RoundedCornerShape(16.dp),
                                color = BrandPrimary,
                                shadowElevation = 4.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = Strings.txt(StringsKey.drawer_new_chat),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        if (showCharacterPicker) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showCharacterPicker = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color(0x59000000)).clickable { showCharacterPicker = false },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = false) { },
                                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                                        color = Color.White
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(Strings.txt(StringsKey.drawer_choose_character), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { showCharacterPicker = false }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.Close, Strings.txt(StringsKey.close), tint = Color(0xFF49454F), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().height(64.dp).clickable { showCharacterPicker = false; onCreateNewRole() },
                                                shape = RoundedCornerShape(12.dp), color = BrandPrimaryContainer
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(BrandPrimary), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(Strings.txt(StringsKey.drawer_create_new_character), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BrandPrimary)
                                                        Text(Strings.txt(StringsKey.drawer_new_character_hint), fontSize = 12.sp, color = Color(0xFF49454F))
                                                    }
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE8E4EC)))
                                                Text(Strings.txt(StringsKey.drawer_existing_characters), fontSize = 11.sp, color = Color(0xFF79747E))
                                                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFFE8E4EC)))
                                            }
                                            if (roleCards.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                                    Text(Strings.txt(StringsKey.drawer_no_character_hint), fontSize = 13.sp, color = Color(0xFF79747E))
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    roleCards.take(6).forEachIndexed { index, role ->
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth().clickable { showCharacterPicker = false; onSelectRole(role.id) },
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = if (role.isActive) BrandPrimaryContainer else Color.White,
                                                            border = if (role.isActive) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E4EC))
                                                        ) {
                                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AvatarGradients[index % 5]), contentAlignment = Alignment.Center) {
                                                                    Text(role.name.first().toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                }
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                        Text(role.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C1B1F))
                                                                        if (role.isActive) {
                                                                            Surface(shape = RoundedCornerShape(9999.dp), color = BrandPrimary) {
                                                                                Text(Strings.txt(StringsKey.drawer_active_tag), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                                                            }
                                                                        }
                                                                    }
                                                                    Text(role.description.ifBlank { role.persona.take(30) }, fontSize = 12.sp, color = Color(0xFF79747E), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth().height(44.dp).clickable { showCharacterPicker = false; onNewConversation() },
                                                shape = RoundedCornerShape(12.dp), color = Color.White,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E4EC))
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(Strings.txt(StringsKey.drawer_blank_chat), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF49454F))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            
        }
    }
}

@Composable
private fun SessionItem(
    session: ConversationSession,
    index: Int,
    isActive: Boolean,
    isEditing: Boolean,
    editingTitle: String,
    showActions: Boolean,
    roleCardAvatarUri: String = "",
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onStartEditing: () -> Unit,
    onDelete: () -> Unit,
    onEditingTitleChange: (String) -> Unit,
    onConfirmEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onRoleCardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scaleVal = if (isPressed) 0.98f else 1f

    val bgColor = if (isActive) BrandPrimaryContainer else Color.White

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .scale(scaleVal)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = bgColor
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Avatar: 46dp circle with role image or initial letter ──
            Box(
                modifier = Modifier
                    .clickable(
                        enabled = session.roleCardId != null,
                        onClickLabel = "View role detail"
                    ) { onRoleCardClick() },
                contentAlignment = Alignment.Center
            ) {
                if (roleCardAvatarUri.isNotBlank()) {
                    AsyncImage(
                        model = roleCardAvatarUri,
                        contentDescription = session.title,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val gradient = AvatarGradients[index % 5]
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(gradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.title.first().toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                // Online dot: only for the active session
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .background(BrandSuccess)
                    )
                }
            }

            // ── Info Column or Edit Field ──
            if (isEditing) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = onEditingTitleChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirmEditing() }),
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )

                IconButton(onClick = onConfirmEditing, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = Strings.txt(StringsKey.confirm),
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onCancelEditing, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = Strings.txt(StringsKey.cancel),
                        tint = Color(0xFFB3261E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = { onClick() },
                                onLongPress = { onLongPress() }
                            )
                        }
                ) {
                    Text(
                        text = session.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1B1F),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val lang = LocalLanguage.current
                    val msgSuffix = Strings.txt(StringsKey.drawer_msg_count_suffix)
                    val subtitle = remember(session, lang) {
                        val msgCount = session.messages.count { it.role.name != "SYSTEM" }
                        val timeStr = formatSessionTime(session.createdAt, lang)
                        "$msgCount $msgSuffix · $timeStr"
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = BrandOutline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Edit / Delete actions (visible when long-pressed) with smooth animation
                AnimatedVisibility(
                    visible = showActions,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 })
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = onStartEditing,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = Strings.txt(StringsKey.drawer_edit_title),
                                tint = BrandOutline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = Strings.txt(StringsKey.drawer_delete_session),
                                tint = Color(0xFFB3261E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private fun filterByDate(
    sessions: List<ConversationSession>,
    filter: DateFilter
): List<ConversationSession> {
    if (filter == DateFilter.ALL) return sessions

    val startOfFilter = when (filter) {
        DateFilter.ALL -> 0L
        DateFilter.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        DateFilter.YESTERDAY -> Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        DateFilter.WEEK -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        DateFilter.MONTH -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val startOfNextDay = if (filter == DateFilter.YESTERDAY) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else null

    return sessions.filter { session ->
        if (filter == DateFilter.YESTERDAY) {
            session.createdAt in startOfFilter until startOfNextDay!!
        } else {
            session.createdAt >= startOfFilter
        }
    }
}

private fun formatSessionTime(timestamp: Long, lang: AppLanguage): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> Strings.get(lang, StringsKey.drawer_time_just_now)
        minutes < 60 -> "$minutes${Strings.get(lang, StringsKey.drawer_time_min_ago)}"
        hours < 24 -> "$hours${Strings.get(lang, StringsKey.drawer_time_hour_ago)}"
        days < 7 -> "$days${Strings.get(lang, StringsKey.drawer_time_day_ago)}"
        else -> {
            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
