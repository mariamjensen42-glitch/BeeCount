package com.cycling.beecount.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.CATEGORY_COLOR_PALETTE
import com.cycling.beecount.domain.model.CATEGORY_PATH_SEPARATOR
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.theme.BeeCountShapes
import com.cycling.beecount.ui.theme.ComponentDefaults
import com.cycling.beecount.ui.theme.Dimens
import com.cycling.beecount.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PencilSquare
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Plus
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PlusCircle
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Trash

/**
 * 类别管理独立页：全屏页面替代底部弹层（避免 bottomsheet 滚动抖动）。
 * 卡片化列表：一级分类支持长按拖拽排序；点击行操作图标进入编辑/删除/子分类。
 * 图标 Emoji、颜色、隐藏、二级子分类、删除归并均在编辑流程内完成。
 */
@Composable
fun CategoryManageRoute(
    viewModel: ManageCategoriesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    CategoryManageScreen(
        categories = categories,
        onBack = onBack,
        onCreate = viewModel::create,
        onCreateChild = viewModel::createChild,
        onRename = viewModel::rename,
        onDeleteWithMerge = viewModel::deleteWithMerge,
        onMoveParent = viewModel::moveParent,
        onUpdateIcon = viewModel::updateIcon,
        onUpdateColor = viewModel::updateColor,
        onUpdateSortOrder = viewModel::updateSortOrder,
        onUpdateHidden = viewModel::updateHidden,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onCreate: (name: String, type: EntryType) -> Unit,
    onCreateChild: (parentId: Long, name: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDeleteWithMerge: (id: Long, targetId: Long) -> Unit,
    onMoveParent: (id: Long, parentId: Long?) -> Unit,
    onUpdateIcon: (id: Long, icon: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onUpdateSortOrder: (id: Long, sortOrder: Int) -> Unit,
    onUpdateHidden: (id: Long, isHidden: Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理类别") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Heroicons.Outline.ArrowLeft, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            AddCategoryBar(onCreate)

            Spacer(Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
                    TypeSection(
                        title = if (type == EntryType.EXPENSE) "支出类别" else "收入类别",
                        groups = categories.filter { it.type == type },
                        modifier = Modifier.weight(1f),
                        onCreateChild = onCreateChild,
                        onRename = onRename,
                        onDeleteWithMerge = onDeleteWithMerge,
                        onMoveParent = onMoveParent,
                        onUpdateIcon = onUpdateIcon,
                        onUpdateColor = onUpdateColor,
                        onUpdateSortOrder = onUpdateSortOrder,
                        onUpdateHidden = onUpdateHidden,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 顶部新增栏：类型分段选择 + 名称输入 + 添加 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryBar(onCreate: (name: String, type: EntryType) -> Unit) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(EntryType.EXPENSE) }
    Box {
        Column {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEachIndexed { index, (t, label) ->
                    SegmentedButton(
                        selected = newType == t,
                        onClick = { newType = t },
                        shape = SegmentedButtonDefaults.itemShape(index, listOf(EntryType.EXPENSE, EntryType.INCOME).size),
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("新类别名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) onCreate(name, newType)
                        newName = ""
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(Heroicons.Outline.Plus, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

/** 一个类型的分类卡片列表 */
@Composable
private fun TypeSection(
    title: String,
    groups: List<Category>,
    modifier: Modifier = Modifier,
    onCreateChild: (parentId: Long, name: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDeleteWithMerge: (id: Long, targetId: Long) -> Unit,
    onMoveParent: (id: Long, parentId: Long?) -> Unit,
    onUpdateIcon: (id: Long, icon: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onUpdateSortOrder: (id: Long, sortOrder: Int) -> Unit,
    onUpdateHidden: (id: Long, isHidden: Boolean) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
    if (groups.isEmpty()) {
        Text(
            "暂无分类",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }

    // 一级分类（可拖拽排序），本地维护其拖拽顺序
    val parents = remember(groups) { groups.filter { it.parentId == null } }
    val orphans = groups.filter { it.parentId != null && groups.none { p -> p.id == it.parentId } }
    val idToParent = remember(parents) { parents.associateBy { it.id } }
    var order by remember(parents) { mutableStateOf(parents.map { it.id }) }
    // 外部列表变化（新增/改名等）时，若出现新父级则同步本地顺序
    if (parents.any { it.id !in order }) order = parents.map { it.id }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newOrder = order.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        order = newOrder
        newOrder.forEachIndexed { idx, id ->
            val cat = idToParent[id]
            if (cat != null && cat.sortOrder != idx + 1) onUpdateSortOrder(id, idx + 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BeeCountShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
    ) {
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth()) {
            items(order, key = { it }) { parentId ->
                val parent = idToParent[parentId] ?: return@items
                val children = groups.filter { it.parentId == parentId }
                ReorderableItem(reorderableState, key = parentId) { isDragging ->
                    CategoryCard(
                        category = parent,
                        children = children,
                        sameType = groups,
                        dragging = isDragging,
                        isChild = false,
                        handleModifier = Modifier.longPressDraggableHandle(),
                        onCreateChild = onCreateChild,
                        onRename = onRename,
                        onDeleteWithMerge = onDeleteWithMerge,
                        onMoveParent = onMoveParent,
                        onUpdateIcon = onUpdateIcon,
                        onUpdateColor = onUpdateColor,
                        onUpdateHidden = onUpdateHidden,
                    )
                }
            }
            orphans.forEach { child ->
                item(key = "orphan_${child.id}") {
                    CategoryCard(
                        category = child,
                        children = emptyList(),
                        sameType = groups,
                        dragging = false,
                        isChild = true,
                        onCreateChild = onCreateChild,
                        onRename = onRename,
                        onDeleteWithMerge = onDeleteWithMerge,
                        onMoveParent = onMoveParent,
                        onUpdateIcon = onUpdateIcon,
                        onUpdateColor = onUpdateColor,
                        onUpdateHidden = onUpdateHidden,
                    )
                }
            }
        }
    }
}

/** 单条分类卡片：一级分类带拖拽手柄；展示图标/色点/名称 + 编辑/删除，子分类跟随父级缩进 */
@Composable
private fun CategoryCard(
    category: Category,
    children: List<Category>,
    sameType: List<Category>,
    dragging: Boolean,
    isChild: Boolean,
    handleModifier: Modifier = Modifier,
    onCreateChild: (parentId: Long, name: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDeleteWithMerge: (id: Long, targetId: Long) -> Unit,
    onMoveParent: (id: Long, parentId: Long?) -> Unit,
    onUpdateIcon: (id: Long, icon: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onUpdateHidden: (id: Long, isHidden: Boolean) -> Unit,
) {
    var editOpen by remember(category.name) { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var addChildOpen by remember { mutableStateOf(false) }
    var childName by remember { mutableStateOf("") }
    var colorOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isChild) 20.dp else 0.dp, end = 4.dp)
                .padding(vertical = 2.dp)
                .clip(BeeCountShapes.extraSmall)
                .background(
                    when {
                        dragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        category.isHidden -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    },
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            if (!isChild) {
                Text(
                    "⋮⋮",
                    modifier = Modifier.then(handleModifier),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(10.dp))
            }
            // 图标：点击换 Emoji
            IconDisplay(category.icon, category.color, onClick = { editOpen = true })
            Spacer(Modifier.width(12.dp))
            // 名称
            Column(Modifier.weight(1f)) {
                Text(
                    if (category.isHidden) "${category.displayName}（已隐藏）" else category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 色点：点击换色
            ColorDot(category.color, onClick = { colorOpen = true })
            Spacer(Modifier.width(6.dp))
            // 一级分类：+子类
            if (!isChild) {
                IconButton(
                    onClick = { addChildOpen = true },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Heroicons.Outline.PlusCircle, contentDescription = "添加子分类", tint = MaterialTheme.colorScheme.primary)
                }
            }
            // 隐藏开关
            Switch(
                checked = !category.isHidden,
                onCheckedChange = { onUpdateHidden(category.id, !it) },
                modifier = Modifier.size(44.dp),
            )
            // 编辑 / 删除
            IconButton(onClick = { editOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Heroicons.Outline.PencilSquare, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { deleteOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(Heroicons.Outline.Trash, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }

        // 子分类列表（跟随父卡片，缩进）
        children.forEach { child ->
            CategoryCard(
                category = child,
                children = emptyList(),
                sameType = sameType,
                dragging = false,
                isChild = true,
                onCreateChild = onCreateChild,
                onRename = onRename,
                onDeleteWithMerge = onDeleteWithMerge,
                onMoveParent = onMoveParent,
                onUpdateIcon = onUpdateIcon,
                onUpdateColor = onUpdateColor,
                onUpdateHidden = onUpdateHidden,
            )
        }
    }

    if (addChildOpen) {
        AlertDialog(
            onDismissRequest = { addChildOpen = false },
            title = { Text("为「${category.displayName}」添加子分类") },
            text = {
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it },
                    placeholder = { Text("子分类名") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = childName.trim()
                        if (name.isNotEmpty()) {
                            onCreateChild(category.id, name)
                            childName = ""
                        }
                        addChildOpen = false
                    },
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addChildOpen = false }) { Text("取消") } },
        )
    }

    if (editOpen) {
        EditCategoryDialog(
            category = category,
            isChild = isChild,
            onDismiss = { editOpen = false },
            onRename = { name -> onRename(category.id, name) },
            onUpdateIcon = { icon -> onUpdateIcon(category.id, icon) },
            onUpdateColor = { c -> onUpdateColor(category.id, c) },
            onMoveParent = if (isChild) { { onMoveParent(category.id, null) } } else null,
        )
        // 编辑弹窗内已提供图标/颜色选择，此处关闭独立色点弹窗
        colorOpen = false
    }
    if (colorOpen) {
        ColorPickerDialog(category.color, { onUpdateColor(category.id, it); colorOpen = false }) { colorOpen = false }
    }
    if (deleteOpen) {
        DeleteMergeDialog(category = category, candidates = sameType, onConfirm = { t -> onDeleteWithMerge(category.id, t) }) { deleteOpen = false }
    }
}

/** 分类图标展示块 */
@Composable
private fun IconDisplay(icon: String, color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.RowAvatar)
            .clip(BeeCountShapes.extraSmall)
            .background(Color(color).copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon.ifEmpty { "🏷️" }, style = MaterialTheme.typography.titleMedium)
    }
}

/** 可点色块 */
@Composable
private fun ColorDot(color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(color))
            .clickable(onClick = onClick),
    )
}

/** 编辑分类对话框：名称 + 图标 + 颜色 + （子分类）上移为一级 */
@Composable
private fun EditCategoryDialog(
    category: Category,
    isChild: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateIcon: (String) -> Unit,
    onUpdateColor: (Long) -> Unit,
    onMoveParent: (() -> Unit)?,
) {
    var name by remember(category.name) { mutableStateOf(category.displayName) }
    var iconPicker by remember { mutableStateOf(false) }
    var colorPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑「${category.displayName}」") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("图标", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    IconDisplay(category.icon, category.color, onClick = { iconPicker = true })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("颜色", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(category.color))
                            .clickable { colorPicker = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("🎨") }
                }
                if (isChild) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { onMoveParent?.invoke() }, modifier = Modifier.align(Alignment.End)) {
                        Text("上移为一级分类")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty() && n != category.displayName) onRename(n)
                    onDismiss()
                },
            ) { Text("保存") }
        },
    )

    if (iconPicker) EmojiPickerDialog(category.icon, { onUpdateIcon(it); iconPicker = false }) { iconPicker = false }
    if (colorPicker) ColorPickerDialog(category.color, { onUpdateColor(it); colorPicker = false }) { colorPicker = false }
}

/** Emoji 选择弹窗 */
@Composable
private fun EmojiPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val emojis = remember {
        listOf(
            "🍜", "🍔", "☕", "🍺", "🍎", "🧋", "🍱", "🍰",
            "🚗", "🚌", "🚇", "✈️", "🚕", "⛽", "🚲", "🚂",
            "🛍️", "👕", "📱", "🎁", "💄", "👟", "🧥", "💻",
            "🏠", "💡", "🔧", "🛏️", "🌡️", "🧺", "🔑", "🧹",
            "🎮", "🎬", "🎵", "🎫", "⚽", "🎲", "🎤", "🎨",
            "🩺", "💊", "😷", "🏋️", "🧘", "🏥", "🦷", "🧬",
            "📚", "✏️", "🎓", "🧪", "📐", "🖊️", "📖", "🎯",
            "🧧", "💼", "💰", "📈", "🏆", "💵", "🪙", "💳",
            "📌", "❓", "⭐", "📦", "🧸", "🏷️", "🧭", "🗂️",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择图标") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                emojis.forEach { e ->
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (e == current) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 颜色选择弹窗 */
@Composable
private fun ColorPickerDialog(current: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORY_COLOR_PALETTE.forEach { c ->
                    val selected = c == current
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { onPick(c) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 删除确认：选择一个目标分类，历史账目将归并过去 */
@Composable
private fun DeleteMergeDialog(
    category: Category,
    candidates: List<Category>,
    onConfirm: (targetId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val pool = candidates.filter { it.id != category.id }
    var selected by remember { mutableStateOf<Long?>(pool.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除「${category.displayName}」？") },
        text = {
            Column {
                Text(
                    "该分类（及其子分类）的历史账目将被归并到目标分类。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (pool.isEmpty()) {
                    Text("同类型没有可归并的目标分类。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        pool.forEach { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { selected = c.id }.fillMaxWidth(),
                            ) {
                                RadioButton(selected = selected == c.id, onClick = { selected = c.id })
                                Text(c.displayName)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            val target = selected
            if (target != null) {
                TextButton(onClick = { onConfirm(target) }) { Text("删除并归并") }
            }
        },
    )
}
