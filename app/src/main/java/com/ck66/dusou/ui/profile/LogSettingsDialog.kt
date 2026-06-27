package com.ck66.dusou.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ck66.dusou.util.FileLogger

/**
 * 日志记录设置弹窗。
 * - 总开关：控制日志是否写入文件
 * - 高级：展开后可选择记录的具体日志分类
 */
@Composable
fun LogSettingsDialog(
    onDismiss: () -> Unit
) {
    var masterEnabled by remember { mutableStateOf(FileLogger.isEnabled) }
    var showAdvanced by remember { mutableStateOf(false) }
    // 每个分类的开关状态
    val categoryStates = remember {
        FileLogger.ALL_CATEGORIES.associateWith { mutableStateOf(FileLogger.isCategoryEnabled(it)) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("日志记录") },
        text = {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 总开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用日志记录",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "关闭后不写入任何日志到 Download 目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = { enabled ->
                            masterEnabled = enabled
                            FileLogger.isEnabled = enabled
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 高级设置
                TextButton(
                    onClick = { showAdvanced = !showAdvanced }
                ) {
                    Text(
                        text = if (showAdvanced) "收起高级设置" else "高级设置",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "选择要记录的日志类型：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        categoryStates.forEach { (category, state) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = categoryLabel(category),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = categoryDesc(category),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Switch(
                                    checked = state.value,
                                    onCheckedChange = { enabled ->
                                        state.value = enabled
                                        FileLogger.setCategoryEnabled(category, enabled)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun categoryLabel(category: String): String = when (category) {
    "FloatingBall" -> "悬浮球"
    "ScreenCapture" -> "屏幕捕获"
    "ScreenSearchVM" -> "搜索过程"
    "TextMatcher" -> "文本匹配"
    "OCR" -> "OCR 识别"
    "Search" -> "搜索查询"
    "Match" -> "匹配结果"
    "QuestionRepository" -> "题库仓库"
    else -> category
}

private fun categoryDesc(category: String): String = when (category) {
    "FloatingBall" -> "点击 · 拖拽"
    "ScreenCapture" -> "截屏状态"
    "ScreenSearchVM" -> "OCR+匹配"
    "TextMatcher" -> "FTS · 精排"
    "OCR" -> "引擎 · 文字"
    "Search" -> "关键词查询"
    "Match" -> "相似度分数"
    "QuestionRepository" -> "FTS索引 · SQL"
    else -> ""
}
