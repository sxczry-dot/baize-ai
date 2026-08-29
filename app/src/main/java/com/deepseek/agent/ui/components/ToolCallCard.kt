package com.deepseek.agent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.deepseek.agent.domain.model.ToolCall
import com.deepseek.agent.domain.model.ToolCallStatus

/** 消息气泡里的工具调用卡片：显示工具名、状态、参数与结果（点按展开/收起）。 */
@Composable
fun ToolCallCard(tc: ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    val (icon, tint) = when (tc.status) {
        ToolCallStatus.Success -> Icons.Filled.CheckCircle to Color(0xFF2E9E5B)
        ToolCallStatus.Failed -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        ToolCallStatus.Rejected -> Icons.Filled.Error to Color(0xFFB8860B)
        ToolCallStatus.Running, ToolCallStatus.AwaitingConfirmation, ToolCallStatus.Confirmed ->
            Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.primary
        else -> toolIcon(tc.name) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = "${tc.name} · ${statusText(tc.status)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = tint
                )
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        text = tc.argumentsJson.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tc.result?.takeIf { it.isNotBlank() }?.let { result ->
                        Text(
                            text = result.take(1200),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "execute_shell" -> Icons.Filled.Terminal
    else -> Icons.Filled.Folder
}

private fun statusText(status: ToolCallStatus): String = when (status) {
    ToolCallStatus.Pending -> "等待中"
    ToolCallStatus.AwaitingConfirmation -> "等你确认"
    ToolCallStatus.Confirmed -> "已确认"
    ToolCallStatus.Rejected -> "已拒绝"
    ToolCallStatus.Running -> "执行中"
    ToolCallStatus.Success -> "完成"
    ToolCallStatus.Failed -> "失败"
}
