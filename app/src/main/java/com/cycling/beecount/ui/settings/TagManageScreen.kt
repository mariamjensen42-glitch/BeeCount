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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.cycling.beecount.domain.model.TAG_COLOR_PALETTE
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.ui.theme.BeeCountShapes
import com.cycling.beecount.ui.theme.Dimens
import com.cycling.beecount.ui.theme.Spacing
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PencilSquare
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Plus
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Trash

/**
 * 标签管理独立页：全屏页面替代底部弹层（避免 bottomsheet 滚动抖动）。
 * 卡色列表行：点色点或右侧「编辑」进入改名/改色；点垃圾桶删除（仅移除标签，账目保留）。
 */
@Composable
fun TagManageRoute(
    viewModel: ManageTagsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    TagManageScreen(
        tags = tags,
        onBack = onBack,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onUpdateColor = viewModel::updateColor,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagManageScreen(
    tags: List<Tag>,
    onBack: () -> Unit,
    onCreate: (name: String) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理标签") },
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
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            AddTagBar(newName, { newName = it }, {
                val name = newName.trim()
                if (name.isNotEmpty()) onCreate(name)
                newName = ""
            })

            Spacer(Modifier.height(8.dp))

            if (tags.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        "还没有标签",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在确认卡片上给账目打上标签后，会出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(BeeCountShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        TagRow(
                            tag = tag,
                            onRename = onRename,
                            onUpdateColor = onUpdateColor,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

/** 顶部新建标签栏 */
@Composable
private fun AddTagBar(newName: String, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newName,
            onValueChange = onChange,
            placeholder = { Text("新标签名") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(Heroicons.Outline.Plus, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** 单条标签卡色行 */
@Composable
private fun TagRow(
    tag: Tag,
    onRename: (id: Long, name: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(BeeCountShapes.extraSmall)
            .clickable { editOpen = true }
            .padding(horizontal = Spacing.mdSm, vertical = Spacing.mdSm),
    ) {
        // 色点：点击循环换色
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(tag.color))
                .clickable {
                    val index = TAG_COLOR_PALETTE.indexOf(tag.color).let { if (it < 0) 0 else it }
                    val next = TAG_COLOR_PALETTE[(index + 1) % TAG_COLOR_PALETTE.size]
                    onUpdateColor(tag.id, next)
                },
        )
        Spacer(Modifier.width(14.dp))
        Text(
            tag.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { editOpen = true }, modifier = Modifier.size(34.dp)) {
            Icon(Heroicons.Outline.PencilSquare, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { deleteOpen = true }, modifier = Modifier.size(34.dp)) {
            Icon(Heroicons.Outline.Trash, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }

    if (editOpen) {
        EditTagDialog(
            tag = tag,
            onDismiss = { editOpen = false },
            onRename = { t, name -> onRename(t.id, name) },
            onUpdateColor = { c -> onUpdateColor(tag.id, c) },
        )
        // 改色入口统一在编辑对话框内；独立色点点击已直接改色，二者不冲突
    }
    if (deleteOpen) {
        DeleteTagDialog(tag = tag, onConfirm = { onDelete(tag.id) }) { deleteOpen = false }
    }
}

/** 编辑标签对话框：改名 + 色板 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onRename: (Tag, String) -> Unit,
    onUpdateColor: (Long) -> Unit,
) {
    var name by remember(tag.name) { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("颜色", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TAG_COLOR_PALETTE.forEach { c ->
                        val selected = c == tag.color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { onUpdateColor(c) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty() && n != tag.name) onRename(tag, n)
                    onDismiss()
                },
            ) { Text("保存") }
        },
    )
}

/** 删除标签确认 */
@Composable
private fun DeleteTagDialog(tag: Tag, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除标签「${tag.name}」？") },
        text = {
            Text(
                "已带该标签的账目不会被删除，仅移除这个标签。",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text("删除") }
        },
    )
}
