package com.ck66.dusou.ui.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/**
 * 题库页面主 UI 组件（无状态版本，通过参数传入所有数据和回调）
 */
@Composable
fun QuestionBankContent(
    uiState: BankUiState,
    onImportClick: () -> Unit,
    onDeleteBank: (Long) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismissImport: () -> Unit,
    onStartPractice: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 本地 UI 状态：搜索模式、搜索词、待删除题库
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var bankToDelete by remember { mutableStateOf<QuestionBank?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is BankUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is BankUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = onImportClick,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is BankUiState.Success -> {
                if (isSearchActive) {
                    SearchContent(
                        query = searchQuery,
                        results = state.searchResults,
                        isSearching = state.isSearching,
                        onQueryChange = { q ->
                            searchQuery = q
                            onSearch(q)
                        },
                        onClearSearch = {
                            searchQuery = ""
                            isSearchActive = false
                            onClearSearch()
                        }
                    )
                } else {
                    BankListContent(
                        banks = state.banks,
                        onStartPractice = onStartPractice,
                        onSearchClick = { isSearchActive = true },
                        onDeleteBank = { bankToDelete = it },
                        onImportClick = onImportClick
                    )
                }

                // 导入进度对话框
                if (state.importState !is ImportState.Idle) {
                    ImportDialog(
                        importState = state.importState,
                        onDismiss = onDismissImport
                    )
                }
            }
        }

        // 删除确认对话框（独立于 BankUiState 管理）
        bankToDelete?.let { bank ->
            DeleteConfirmDialog(
                bank = bank,
                onConfirm = {
                    onDeleteBank(bank.id)
                    bankToDelete = null
                },
                onDismiss = { bankToDelete = null }
            )
        }
    }
}

/**
 * 错误状态展示
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * 搜索模式下的内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    results: List<Question>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { },
            active = true,
            onActiveChange = { if (!it) onClearSearch() },
            placeholder = { Text("搜索题目...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索"
                )
            },
            trailingIcon = {
                TextButton(onClick = onClearSearch) {
                    Text("取消")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 搜索建议（空实现）
        }

        // 搜索结果区域
        when {
            isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "输入关键词搜索题目",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配的题目",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { question ->
                        SearchResultItem(question = question)
                    }
                }
            }
        }
    }
}

/**
 * 题库列表内容
 */
@Composable
private fun BankListContent(
    banks: List<QuestionBank>,
    onStartPractice: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onDeleteBank: (QuestionBank) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banks.isEmpty()) {
        EmptyState(
            onImportClick = onImportClick,
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(banks, key = { it.id }) { bank ->
                BankCard(
                    bank = bank,
                    onPractice = { onStartPractice(bank.id) },
                    onSearch = onSearchClick,
                    onDelete = { onDeleteBank(bank) }
                )
            }
        }
    }
}

/**
 * 空状态：暂无题库
 */
@Composable
private fun EmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无题库",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击下方按钮导入题库",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImportClick) {
            Text("导入题库")
        }
    }
}

/**
 * 题库卡片组件
 */
@Composable
fun BankCard(
    bank: QuestionBank,
    onPractice: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 中间信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bank.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${bank.questionCount} 道题目 · ${dateFormat.format(Date(bank.importTime))} 导入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右侧操作按钮
            IconButton(onClick = onPractice) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "练习",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 搜索结果条目
 */
@Composable
private fun SearchResultItem(
    question: Question,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = question.stem.take(50) + if (question.stem.length > 50) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "答案: ${question.answer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 删除确认对话框
 */
@Composable
private fun DeleteConfirmDialog(
    bank: QuestionBank,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Text("确定要删除题库「${bank.name}」吗？\n\n该题库中的 ${bank.questionCount} 道题目也将被一并删除，此操作不可撤销。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
