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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                        text = "记忆管理",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增记忆"
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
            var layerFilter by remember { mutableStateOf("all") }  // "all", "short_term", "long_term"

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
                    placeholder = { Text("搜索记忆内容...") },
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
                            if (uiState.filter != MemoryFilter.ALL || layerFilter != "all")
                                BrandPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable { showFilterDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "筛选",
                        tint = if (uiState.filter != MemoryFilter.ALL || layerFilter != "all")
                            BrandPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Show active filter summary
            val hasFilter = uiState.filter != MemoryFilter.ALL || layerFilter != "all"
            if (hasFilter) {
                val filterParts = mutableListOf<String>()
                if (uiState.filter != MemoryFilter.ALL) filterParts.add(filterLabel(uiState.filter))
                if (layerFilter != "all") filterParts.add(layerLabel(layerFilter))
                Text(
                    text = "筛选: ${filterParts.joinToString(" · ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Filter popup dialog
            if (showFilterDialog) {
                var tempCategoryFilter by remember { mutableStateOf(uiState.filter) }
                var tempLayerFilter by remember { mutableStateOf(layerFilter) }

                AlertDialog(
                    onDismissRequest = { showFilterDialog = false },
                    title = { Text("筛选记忆") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Category section
                            Text("分类", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MemoryFilter.entries.forEach { filter ->
                                    val isSelected = tempCategoryFilter == filter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { tempCategoryFilter = filter },
                                        label = { Text(filterLabel(filter)) },
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White,
                                            labelColor = BrandOnSurfaceVariant
                                        ),
                                        border = if (isSelected) null
                                            else androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                                borderColor = BrandOutlineVariant, enabled = true, selected = false
                                            )
                                    )
                                }
                            }

                            // Layer section
                            Text("层级", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("all" to "全部", "short_term" to "短期", "long_term" to "长期").forEach { (value, label) ->
                                    val isSelected = tempLayerFilter == value
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { tempLayerFilter = value },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f),
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White,
                                            labelColor = BrandOnSurfaceVariant
                                        ),
                                        border = if (isSelected) null
                                            else androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                                borderColor = BrandOutlineVariant, enabled = true, selected = false
                                            )
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            memoryViewModel.setFilter(tempCategoryFilter)
                            layerFilter = tempLayerFilter
                            showFilterDialog = false
                        }) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFilterDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            when {
                uiState.isLoading -> {
                    EmptyState(
                        title = "正在加载记忆",
                        message = "请稍候..."
                    )
                }

                uiState.memories.isEmpty() -> {
                    EmptyState(
                        title = "还没有记忆",
                        message = "在对话里说“记住...”，或点右上角新增一条。"
                    )
                }

                else -> {
                    val displayMemories = uiState.memories.filter { memory ->
                        val matchesSearch = memorySearchQuery.isBlank() ||
                            memory.content.contains(memorySearchQuery, ignoreCase = true)
                        val matchesLayer = layerFilter == "all" || memory.layer == layerFilter
                        matchesSearch && matchesLayer
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
                                    onPromote = { memoryViewModel.promoteMemory(memory.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        var draftLayer by remember { mutableStateOf(editingMemory?.layer ?: "short_term") }
        var draftCharacter by remember { mutableStateOf(editingMemory?.source ?: "") }
        MemoryEditorDialog(
            title = if (editingMemory == null) "新增记忆" else "编辑记忆",
            content = draftContent,
            category = draftCategory,
            layer = draftLayer,
            characterName = draftCharacter,
            onContentChange = { draftContent = it },
            onCategoryChange = { draftCategory = it },
            onLayerChange = { draftLayer = it },
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
                        category = draftCategory
                    )
                }
                showEditor = false
            }
        )
    }

    deletingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text("删除记忆") },
            text = { Text("确认删除这条记忆吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryViewModel.deleteMemory(memory)
                        deletingMemory = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }) {
                    Text("取消")
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
                    text = categoryLabel(memory.category),
                    bgColor = catBg,
                    textColor = catText
                )
                val (layerBg, layerText) = if (memory.layer == "long_term") {
                    MemoryLongBg to MemoryLongText
                } else {
                    MemoryShortBg to MemoryShortText
                }
                MemoryTag(
                    text = layerLabel(memory.layer),
                    bgColor = layerBg,
                    textColor = layerText
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "更新时间：${formatTime(memory.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑记忆")
                }
                Spacer(modifier = Modifier.size(8.dp))
                FilledTonalIconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除记忆")
                }
                if (memory.layer == "short_term") {
                    Spacer(modifier = Modifier.size(8.dp))
                    FilledTonalIconButton(onClick = onPromote) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "提升为长期记忆")
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
    layer: String,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Content text field
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    label = { Text("记忆内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // Category dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = categoryLabel(category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(item)) },
                                onClick = {
                                    onCategoryChange(item)
                                    categoryExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                // Layer dropdown
                var layerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = layerExpanded,
                    onExpandedChange = { layerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = layerLabel(layer),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("层级") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = layerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = layerExpanded,
                        onDismissRequest = { layerExpanded = false }
                    ) {
                        layers.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(layerLabel(item)) },
                                onClick = {
                                    onLayerChange(item)
                                    layerExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                // Character dropdown (optional, for character-specific memories)
                var characterExpanded by remember { mutableStateOf(false) }
                var characterSearchQuery by remember { mutableStateOf(characterName) }
                val filteredRoles = remember(characterSearchQuery) {
                    if (characterSearchQuery.isBlank()) {
                        availableRoleCards
                    } else {
                        availableRoleCards.filter { 
                            it.name.contains(characterSearchQuery, ignoreCase = true) 
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = characterExpanded,
                    onExpandedChange = { characterExpanded = it }
                ) {
                    OutlinedTextField(
                        value = characterSearchQuery,
                        onValueChange = { 
                            characterSearchQuery = it
                            onCharacterNameChange(it)
                        },
                        label = { Text("关联角色（可选）") },
                        placeholder = { Text("留空表示全局记忆") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = characterExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = characterExpanded,
                        onDismissRequest = { characterExpanded = false }
                    ) {
                        // "Global" option
                        DropdownMenuItem(
                            text = { Text("全局记忆（不关联角色）", color = if (characterName.isBlank()) BrandPrimary else Color.Unspecified) },
                            onClick = {
                                characterSearchQuery = ""
                                onCharacterNameChange("")
                                characterExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                        // Role card options
                        filteredRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name) },
                                onClick = {
                                    characterSearchQuery = role.name
                                    onCharacterNameChange(role.name)
                                    characterExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = content.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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

private fun filterLabel(filter: MemoryFilter): String {
    return when (filter) {
        MemoryFilter.ALL -> "全部"
        MemoryFilter.FACT -> "事实"
        MemoryFilter.PREFERENCE -> "偏好"
        MemoryFilter.EVENT -> "事件"
        MemoryFilter.RELATION -> "关系"
        MemoryFilter.TIME -> "时间"
        MemoryFilter.OTHER -> "其他"
    }
}

private fun categoryLabel(category: String): String {
    return when (category) {
        "fact" -> "事实"
        "preference" -> "偏好"
        "event" -> "事件"
        "relation", "relationship" -> "关系"
        "time" -> "时间"
        "other" -> "其他"
        else -> category
    }
}

private fun layerLabel(layer: String): String {
    return when (layer) {
        "short_term" -> "短期"
        "long_term" -> "长期"
        else -> layer
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
