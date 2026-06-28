package com.ck66.dusou.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ck66.dusou.util.OcrFilterPreferences

@Composable
fun OcrFilterSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var filterEnabled by remember { mutableStateOf(OcrFilterPreferences.isFilterEnabled(context)) }
    var userFilters by remember { mutableStateOf(OcrFilterPreferences.getUserFilters(context)) }
    var disabledDefaults by remember { mutableStateOf(OcrFilterPreferences.getDisabledDefaults(context)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var filterToDelete by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
        title = { Text("识别过滤词") },
        text = {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .height(480.dp)
            ) {
                // 总开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用过滤", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "过滤掉 OCR 识别到的非题目内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = filterEnabled,
                        onCheckedChange = {
                            filterEnabled = it
                            OcrFilterPreferences.setFilterEnabled(context, it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    // 系统默认过滤词
                    item {
                        Text(
                            "系统默认",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(OcrFilterPreferences.DEFAULT_FILTERS) { filter ->
                        val displayName = OcrFilterPreferences.getDisplayName(filter)
                        val isEnabled = filter !in disabledDefaults

                        FilterItem(
                            name = displayName,
                            enabled = isEnabled,
                            isDefault = true,
                            onToggle = {
                                val newDisabled = if (isEnabled) {
                                    disabledDefaults + filter
                                } else {
                                    disabledDefaults - filter
                                }
                                disabledDefaults = newDisabled
                                OcrFilterPreferences.setDisabledDefaults(context, newDisabled.toList())
                            },
                            onDelete = null
                        )
                    }

                    // 用户自定义过滤词
                    if (userFilters.isNotEmpty()) {
                        item {
                            Text(
                                "用户自定义",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(userFilters) { filter ->
                            FilterItem(
                                name = filter,
                                enabled = true,
                                isDefault = false,
                                onToggle = null,
                                onDelete = {
                                    filterToDelete = filter
                                }
                            )
                        }
                    }
                }

                // 添加按钮
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加过滤词")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )

    // 添加过滤词弹窗
    if (showAddDialog) {
        AddFilterDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newFilter ->
                if (newFilter.isNotBlank() && newFilter !in userFilters) {
                    val newFilters = userFilters + newFilter
                    userFilters = newFilters
                    OcrFilterPreferences.setUserFilters(context, newFilters)
                }
                showAddDialog = false
            }
        )
    }

    // 删除确认弹窗
    filterToDelete?.let { filter ->
        AlertDialog(
            onDismissRequest = { filterToDelete = null },
            title = { Text("删除过滤词") },
            text = { Text("确定删除「$filter」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newFilters = userFilters - filter
                        userFilters = newFilters
                        OcrFilterPreferences.setUserFilters(context, newFilters)
                        filterToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { filterToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FilterItem(
    name: String,
    enabled: Boolean,
    isDefault: Boolean,
    onToggle: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        if (isDefault) {
            onToggle?.let { toggle ->
                Switch(
                    checked = enabled,
                    onCheckedChange = { toggle() },
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            onDelete?.let { delete ->
                IconButton(onClick = delete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加过滤词") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("输入要过滤的文字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
