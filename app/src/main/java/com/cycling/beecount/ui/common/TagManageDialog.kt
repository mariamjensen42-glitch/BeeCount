package com.cycling.beecount.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.cycling.beecount.domain.model.TAG_COLOR_PALETTE
import com.cycling.beecount.domain.model.Tag

/**
 * 标签管理对话框（账本页与设置页共用，ADR 0007）。
 * 点色点循环切换板色（改色全局生效）；名称输入后点「改名」提交；删除只移除标签、账目保留。
 */
@Composable
fun TagManageDialog(
    tags: List<Tag>,
    onClose: () -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("管理标签") },
        text = {
            Column {
                Text(
                    "点色点切换颜色（改色全局生效）；名称输入完点「改名」；删除只移除标签，账目保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (tags.isEmpty()) {
                    Text(
                        "还没有标签，去确认卡片上新建一个吧",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    tags.forEach { tag ->
                        TagManageRow(tag, onRename, onUpdateColor, onDelete)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("完成") }
        },
    )
}

@Composable
private fun TagManageRow(
    tag: Tag,
    onRename: (id: Long, name: String) -> Unit,
    onUpdateColor: (id: Long, color: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var nameText by remember(tag.id) { mutableStateOf(tag.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(tag.color))
                .clickable {
                    val index = TAG_COLOR_PALETTE.indexOf(tag.color).let { if (it < 0) 0 else it }
                    val next = TAG_COLOR_PALETTE[(index + 1) % TAG_COLOR_PALETTE.size]
                    onUpdateColor(tag.id, next)
                },
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = {
                val name = nameText.trim()
                if (name.isNotEmpty() && name != tag.name) {
                    onRename(tag.id, name)
                }
            },
        ) { Text("改名") }
        TextButton(onClick = { onDelete(tag.id) }) { Text("删除") }
    }
}
