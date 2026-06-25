package com.companion.chat.ui.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.RoleCard
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandSecondary
import com.companion.chat.ui.theme.BrandPrimaryContainer
import com.companion.chat.ui.theme.BrandSecondaryContainer
import com.companion.chat.ui.theme.BrandOutline
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandOnSurfaceVariant
import com.companion.chat.ui.theme.BrandSurfaceContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.companion.chat.locale.AppLanguage
import com.companion.chat.locale.LocalLanguage
import com.companion.chat.locale.Strings
import com.companion.chat.locale.StringsKey

// ── Memory-specific color palette ──
private val MemoryFactBg = Color(0xFFE8F0FB)
private val MemoryFactText = Color(0xFF3A7BC8)
private val MemoryEventBg = Color(0xFFFFF0E0)
private val MemoryEventText = Color(0xFFC06A20)
private val MemoryTimeBg = Color(0xFFE0F5F0)
private val MemoryTimeText = Color(0xFF1E7A6E)
private val MemoryLongBg = Color(0xFFE3F5EC)
private val MemoryLongText = Color(0xFF2E7D52)
private val MemoryShortBg = Color(0xFFFFF4E0)
private val MemoryShortText = Color(0xFFB8860B)
private val MemoryFactStrip = Color(0xFF4A90D9)
private val MemoryEventStrip = Color(0xFFE8873A)
private val MemoryTimeStrip = Color(0xFF2A9D8F)
private val MemoryOtherStrip = Color(0xFF79747E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    modifier: Modifier = Modifier,
    memoryViewModel: MemoryViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by memoryViewModel.uiState.collectAsStateWithLifecycle()
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var draftContent by remember { mutableStateOf("") }
    var draftCategory by remember { mutableStateOf("fact") }
    var deletingMemory by remember { mutableStateOf<Memory?>(null) }

    DisposableEffect(lifecycleOwner, memoryViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                memoryViewModel.loadMemories()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openEditor(memory: Memory?) {
        editingMemory = memory
        draftContent = memory?.content.orEmpty()
        draftCategory = memory?.category ?: "fact"
        showEditor = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = Strings.txt(StringsKey.memory_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = Strings.txt(StringsKey.memory_add)
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Search + Filter Button ──
            var memorySearchQuery by remember { mutableStateOf("") }
            var showFilterDialog by remember { mutableStateOf(false) }
            var strengthFilter by remember { mutableStateOf("all") }  // "all", "strong", "weak"
            var selectedRoleCardId by remember { mutableStateOf<Long?>(null) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = memorySearchQuery,
                    onValueChange = { memorySearchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(Strings.txt(StringsKey.memory_search_hint)) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = BrandOutlineVariant
                    )
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(
                            if (uiState.filter != MemoryFilter.ALL || strengthFilter != "all" || selectedRoleCardId != null)
                                BrandPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable { showFilterDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = Strings.txt(StringsKey.memory_filter),
                        tint = if (uiState.filter != MemoryFilter.ALL || strengthFilter != "all" || selectedRoleCardId != null)
                            BrandPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Show active filter summary
            val hasFilter = uiState.filter != MemoryFilter.ALL || strengthFilter != "all" || selectedRoleCardId != null
            if (hasFilter) {
                val filterParts = mutableListOf<String>()
                if (uiState.filter != MemoryFilter.ALL) filterParts.add(filterLabel(uiState.filter, LocalLanguage.current))
                if (strengthFilter != "all") filterParts.add("S:" + strengthFilter)
                if (selectedRoleCardId != null) {
                    val roleName = uiState.roleCards.find { it.id == selectedRoleCardId }?.name ?: Strings.txt(StringsKey.memory_unknown_role)
                    filterParts.add(Strings.txt(StringsKey.memory_role_prefix, roleName))
                }
                Text(
                    text = Strings.txt(StringsKey.memory_filter_label, filterParts.joinToString(" · ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Filter bottom sheet dialog with tabs
            if (showFilterDialog) {
                var tempCategoryFilter by remember { mutableStateOf(uiState.filter) }
                var tempStrengthFilter by remember { mutableStateOf(strengthFilter) }
                var tempRoleCardId by remember { mutableStateOf(selectedRoleCardId) }
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf(Strings.txt(StringsKey.memory_tab_category), Strings.txt(StringsKey.memory_tab_layer), Strings.txt(StringsKey.memory_tab_role))

                Dialog(
                    onDismissRequest = { showFilterDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x59000000))
                            .clickable { showFilterDialog = false },
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // ── Header ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Strings.txt(StringsKey.memory_filter_title),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(BrandSurfaceContainer)
                                            .clickable { showFilterDialog = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            Strings.txt(StringsKey.close),
                                            tint = Color(0xFF49454F),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // ── Tabs ──
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    tabs.forEachIndexed { index, tab ->
                                        val isSelected = selectedTab == index
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedTab = index },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = tab,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) BrandPrimary else BrandOutline
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(if (isSelected) 32.dp else 0.dp)
                                                    .height(2.5.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (isSelected) BrandPrimary else Color.Transparent)
                                            )
                                        }
                                    }
                                }

                                // Divider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFE8E4EC))
                                )

                                // ── Tab Content ──
                                when (selectedTab) {
                                    0 -> {
                                        // Category tab - horizontal scrollable chips
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp)
                                        ) {
                                            items(MemoryFilter.entries.size) { index ->
                                                val filter = MemoryFilter.entries[index]
                                                val isSelected = tempCategoryFilter == filter
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { tempCategoryFilter = filter },
                                                    label = { Text(filterLabel(filter, LocalLanguage.current)) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = BrandPrimary,
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.White,
                                                        labelColor = BrandOnSurfaceVariant
                                                    ),
                                                    border = if (isSelected) null
                                                        else FilterChipDefaults.filterChipBorder(
                                                            borderColor = BrandOutlineVariant, enabled = true, selected = false
                                                        )
                                                )
                                            }
                                        }
                                    }
                                    1 -> {
                                        // Strength tab - horizontal scrollable chips
                                        val lang0 = LocalLanguage.current
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp)
                                        ) {
                                            val strengthOptions = listOf("all" to Strings.get(lang0, StringsKey.memory_layer_all), "weak" to "Weak (<0.4)", "strong" to "Strong (>=0.4)")
                                            items(strengthOptions.size) { index ->
                                                val (value, label) = strengthOptions[index]
                                                val isSelected = tempStrengthFilter == value
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { tempStrengthFilter = value },
                                                    label = { Text(label) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = BrandPrimary,
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.White,
                                                        labelColor = BrandOnSurfaceVariant
                                                    ),
                                                    border = if (isSelected) null
                                                        else FilterChipDefaults.filterChipBorder(
                                                            borderColor = BrandOutlineVariant, enabled = true, selected = false
                                                        )
                                                )
                                            }
                                        }
                                    }
                                    2 -> {
                                        // Role tab - horizontal scrollable chips
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp)
                                        ) {
                                            item {
                                                FilterChip(
                                                    selected = tempRoleCardId == null,
                                                    onClick = { tempRoleCardId = null },
                                                    label = { Text(Strings.txt(StringsKey.memory_all_roles)) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = BrandPrimary,
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.White,
                                                        labelColor = BrandOnSurfaceVariant
                                                    ),
                                                    border = if (tempRoleCardId == null) null
                                                        else FilterChipDefaults.filterChipBorder(
                                                            borderColor = BrandOutlineVariant, enabled = true, selected = false
                                                        )
                                                )
                                            }
                                            items(uiState.roleCards.size) { index ->
                                                val roleCard = uiState.roleCards[index]
                                                val isSelected = tempRoleCardId == roleCard.id
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { tempRoleCardId = roleCard.id },
                                                    label = { Text(roleCard.name) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = BrandPrimary,
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.White,
                                                        labelColor = BrandOnSurfaceVariant
                                                    ),
                                                    border = if (isSelected) null
                                                        else FilterChipDefaults.filterChipBorder(
                                                            borderColor = BrandOutlineVariant, enabled = true, selected = false
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }

                                // ── Confirm Button ──
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clickable {
                                                memoryViewModel.setFilter(tempCategoryFilter)
                                                strengthFilter = tempStrengthFilter
                                                selectedRoleCardId = tempRoleCardId
                                                memoryViewModel.setRoleCardFilter(tempRoleCardId)
                                                showFilterDialog = false
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = BrandPrimary
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = Strings.txt(StringsKey.memory_filter_confirm),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    EmptyState(
                        title = Strings.txt(StringsKey.memory_loading_title),
                        message = Strings.txt(StringsKey.memory_loading_msg)
                    )
                }

                uiState.memories.isEmpty() -> {
                    EmptyState(
                        title = Strings.txt(StringsKey.memory_empty_title),
                        message = Strings.txt(StringsKey.memory_empty_msg)
                    )
                }

                else -> {
                    val displayMemories = uiState.memories.filter { memory ->
                        val matchesSearch = memorySearchQuery.isBlank() ||
                            memory.content.contains(memorySearchQuery, ignoreCase = true)
                        val matchesStrength = strengthFilter == "all" || strengthFilter == "strong"
                        matchesSearch && matchesStrength
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = displayMemories,
                            key = { _, m -> m.id }
                        ) { index, memory ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(memory.id) {
                                delay(50L + index * 70L)
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(animationSpec = tween(400, easing = EaseOut)) +
                                        slideInVertically(animationSpec = tween(400, easing = EaseOut)) { it / 4 }
                            ) {
                                MemoryCard(
                                    memory = memory,
                                    onEdit = { openEditor(memory) },
                                    onDelete = { deletingMemory = memory },
                                    onPromote = { memoryViewModel.strengthenMemory(memory.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        var draftStrength by remember { mutableStateOf("" /* layer deprecated, use strength from memory */) }
        var draftCharacter by remember { mutableStateOf(editingMemory?.source ?: "") }
        MemoryEditorDialog(
            title = if (editingMemory == null) Strings.txt(StringsKey.memory_add) else Strings.txt(StringsKey.memory_edit),
            content = draftContent,
            category = draftCategory,
            /* layer removed */
            characterName = draftCharacter,
            onContentChange = { draftContent = it },
            onCategoryChange = { draftCategory = it },
            onLayerChange /* kept for compat */ = { draftStrength = it },
            onCharacterNameChange = { draftCharacter = it },
            onDismiss = { showEditor = false },
            availableRoleCards = uiState.roleCards,
            onConfirm = {
                if (editingMemory == null) {
                    memoryViewModel.addMemory(draftContent, draftCategory)
                } else {
                    memoryViewModel.updateMemory(
                        memoryId = editingMemory!!.id,
                        content = draftContent,
                        category = draftCategory,
                        /* layer removed */
                    )
                }
                showEditor = false
            }
        )
    }

    deletingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text(Strings.txt(StringsKey.memory_delete_title)) },
            text = { Text(Strings.txt(StringsKey.memory_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryViewModel.deleteMemory(memory)
                        deletingMemory = null
                    }
                ) {
                    Text(Strings.txt(StringsKey.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }) {
                    Text(Strings.txt(StringsKey.cancel))
                }
            }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPromote: () -> Unit
) {
    val stripColor = when (memory.category) {
        "fact" -> MemoryFactStrip
        "preference" -> BrandPrimary
        "event" -> MemoryEventStrip
        "relation", "relationship" -> BrandSecondary
        "time" -> MemoryTimeStrip
        else -> MemoryOtherStrip
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row {
            // 4dp left color strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(stripColor)
            )
            Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (catBg, catText) = when (memory.category) {
                    "fact" -> MemoryFactBg to MemoryFactText
                    "preference" -> BrandPrimaryContainer to BrandPrimary
                    "event" -> MemoryEventBg to MemoryEventText
                    "relation", "relationship" -> BrandSecondaryContainer to BrandSecondary
                    "time" -> MemoryTimeBg to MemoryTimeText
                    else -> Color(0xFFF0EDF3) to BrandOutline
                }
                MemoryTag(
                    text = categoryLabel(memory.category, LocalLanguage.current),
                    bgColor = catBg,
                    textColor = catText
                )
                val (strengthBg, strengthText) = if (memory.strength >= 0.4) {
                    MemoryLongBg to MemoryLongText
                } else {
                    MemoryShortBg to MemoryShortText
                }
                MemoryTag(
                    text = "S=${"%.1f".format(memory.strength)}",
                    bgColor = strengthBg,
                    textColor = strengthText
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Strings.txt(StringsKey.memory_updated_at, formatTime(memory.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = Strings.txt(StringsKey.memory_edit_action))
                }
                Spacer(modifier = Modifier.size(8.dp))
                FilledTonalIconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = Strings.txt(StringsKey.memory_delete_action))
                }
                if (memory.strength < 0.4) {
                    Spacer(modifier = Modifier.size(8.dp))
                    FilledTonalIconButton(onClick = onPromote) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = Strings.txt(StringsKey.memory_promote_action))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MemoryTag(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditorDialog(
    title: String,
    content: String,
    category: String,
    @Suppress("UNUSED_PARAMETER") _layer: String = "0.6",
    characterName: String,
    onContentChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onLayerChange: (String) -> Unit,
    onCharacterNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    availableRoleCards: List<RoleCard> = emptyList()
) {
    val categories = listOf("fact", "preference", "event", "relation", "time", "other")
    val layers = listOf("short_term", "long_term")
    val tabs = listOf(Strings.txt(StringsKey.memory_tab_content), Strings.txt(StringsKey.memory_tab_category), Strings.txt(StringsKey.memory_tab_layer), Strings.txt(StringsKey.memory_tab_role))
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x59000000))
                .clickable { onDismiss() },
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ── Header ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(BrandSurfaceContainer)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                Strings.txt(StringsKey.close),
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ── Tabs ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) BrandPrimary else BrandOutline
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 32.dp else 0.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) BrandPrimary else Color.Transparent)
                                )
                            }
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE8E4EC))
                    )

                    // ── Tab Content ──
                    when (selectedTab) {
                        0 -> {
                            // Content tab
                            OutlinedTextField(
                                value = content,
                                onValueChange = onContentChange,
                                label = { Text(Strings.txt(StringsKey.memory_field_content)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                minLines = 4
                            )
                        }
                        1 -> {
                            // Category tab - horizontal scrollable chips
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                items(categories.size) { index ->
                                    val cat = categories[index]
                                    val isSelected = category == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onCategoryChange(cat) },
                                        label = { Text(categoryLabel(cat, LocalLanguage.current)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = BrandSurfaceContainer,
                                            labelColor = BrandOutline
                                        )
                                    )
                                }
                            }
                        }
                        2 -> {
                            // Character tab
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                // Global memory option
                                FilterChip(
                                    selected = characterName.isBlank(),
                                    onClick = { onCharacterNameChange("") },
                                    label = { Text(Strings.txt(StringsKey.memory_global)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = BrandSurfaceContainer,
                                        labelColor = BrandOutline
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Role cards
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(availableRoleCards.size) { index ->
                                        val role = availableRoleCards[index]
                                        val isSelected = characterName == role.name
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onCharacterNameChange(role.name) },
                                            label = { Text(role.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BrandPrimary,
                                                selectedLabelColor = Color.White,
                                                containerColor = BrandSurfaceContainer,
                                                labelColor = BrandOutline
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                    // ── Action Buttons ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.txt(StringsKey.cancel))
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = content.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.txt(StringsKey.save))
                        }
                    }
                }
            }
        }
    }

@Composable
private fun EmptyState(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun filterLabel(filter: MemoryFilter, lang: AppLanguage): String {
    val k = when (filter) {
        MemoryFilter.ALL -> StringsKey.memory_cat_all
        MemoryFilter.FACT -> StringsKey.memory_cat_fact
        MemoryFilter.PREFERENCE -> StringsKey.memory_cat_preference
        MemoryFilter.EVENT -> StringsKey.memory_cat_event
        MemoryFilter.BEHAVIOR -> StringsKey.memory_cat_other
        MemoryFilter.KNOWLEDGE -> StringsKey.memory_cat_other
        MemoryFilter.SKILL -> StringsKey.memory_cat_other
        MemoryFilter.RELATION -> StringsKey.memory_cat_relation
        MemoryFilter.TIME -> StringsKey.memory_cat_time
        MemoryFilter.OTHER -> StringsKey.memory_cat_other
    }
    return Strings.get(lang, k)
}

private fun categoryLabel(category: String, lang: AppLanguage): String {
    val k = when (category) {
        "fact" -> StringsKey.memory_cat_fact
        "preference" -> StringsKey.memory_cat_preference
        "event" -> StringsKey.memory_cat_event
        "relation", "relationship" -> StringsKey.memory_cat_relation
        "time" -> StringsKey.memory_cat_time
        "other" -> StringsKey.memory_cat_other
        else -> return category
    }
    return Strings.get(lang, k)
}

/* layerLabel deprecated - use strengthLabel */
@Deprecated("Use strength instead of layer") private fun layerLabel(layer: String, lang: AppLanguage): String { return layer }

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
