package com.ck66.dusou.ui.bank

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 导入进度对话框
 *
 * 根据 ImportState 展示不同状态：
 * - Importing: 旋转进度指示器 + "正在导入..."
 * - Success: 成功图标 + "导入成功：XXX题库 (N道题)"
 * - Failed: 失败图标 + 错误信息 + 关闭按钮
 */
@Composable
fun ImportDialog(
    importState: ImportState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (importState) {
        is ImportState.Importing -> {
            ImportingDialog(modifier = modifier)
        }

        is ImportState.Success -> {
            SuccessDialog(
                bankName = importState.bankName,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }

        is ImportState.Failed -> {
            FailedDialog(
                error = importState.error,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }

        is ImportState.Idle -> {
            // 不展示任何对话框
        }
    }
}

/**
 * 导入进行中的对话框
 */
@Composable
private fun ImportingDialog(
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { /* 导入中不可关闭 */ },
        modifier = modifier,
        title = {
            Text(
                text = "正在导入...",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在解析题库文件，请稍候",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { /* 无按钮 */ }
    )
}

/**
 * 导入成功的对话框
 */
@Composable
private fun SuccessDialog(
    bankName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "导入成功",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✅",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "导入成功：$bankName",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

/**
 * 导入失败的对话框
 */
@Composable
private fun FailedDialog(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "导入失败",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "❌",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
