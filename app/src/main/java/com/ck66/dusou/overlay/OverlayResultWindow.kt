package com.ck66.dusou.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ck66.dusou.matcher.MatchResult
import com.ck66.dusou.ui.practice.PracticeUtils
import com.ck66.dusou.ui.theme.DusouTheme

class OverlayResultWindow(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    fun show(matchResult: MatchResult) {
        if (isShowing) {
            updateComposeContent(matchResult)
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 创建 LifecycleOwner 注入到 ComposeView，否则在系统窗口中
        // Compose 找不到 ViewTreeLifecycleOwner 会崩溃
        val lifecycleOwner = ServiceLifecycleOwner().also {
            serviceLifecycleOwner = it
        }
        lifecycleOwner.performStart(null)

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                DusouTheme {
                    OverlayResultContent(
                        matchResult = matchResult,
                        onCopyAnswer = { text ->
                            clipboard.setPrimaryClip(ClipData.newPlainText("answer", text))
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // alpha 不设，背景半透明由 Compose Column 的 .background(alpha=0.8f) 控制
            // height 由 Compose 自适应（WRAP_CONTENT + wrapContentHeight + heightIn(max)）
        }

        overlayView = view
        layoutParams = params
        windowManager?.addView(view, params)
        isShowing = true
    }

    fun hide() {
        dismiss()
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        layoutParams = null
        windowManager = null
        isShowing = false
        serviceLifecycleOwner?.performStop()
        serviceLifecycleOwner = null
    }

    fun isShowing(): Boolean = isShowing

    private fun updateComposeContent(matchResult: MatchResult) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            overlayView?.setContent {
                DusouTheme {
                    OverlayResultContent(
                        matchResult = matchResult,
                        onCopyAnswer = { text ->
                            clipboard.setPrimaryClip(ClipData.newPlainText("answer", text))
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        } catch (e: Exception) {
            // 窗口可能已被系统移除，重置状态
            isShowing = false
        }
    }
}

@Composable
private fun OverlayResultContent(
    matchResult: MatchResult,
    onCopyAnswer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val question = matchResult.question
    var analysisExpanded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 })
    ) {
        // 外层加 wrapContentHeight，让弹窗高度随内容自适应
        // 加 heightIn(max) 限制最大高度（屏高的 70%），避免选项过多时弹窗超高
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .wrapContentHeight()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(16.dp)
        ) {
            // Title bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "搜索结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Similarity
            Text(
                text = "相似度 ${"%.0f".format(matchResult.similarity * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (matchResult.matched) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (question != null) {
                // 题干
                Text(
                    text = question.stem,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                // 选项（含判断题兼容）
                val options = PracticeUtils.parseOptions(question.options)
                if (options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 判断正确答案对应的选项索引
                    val displayType = when (question.type) {
                        "judge" -> "判断"
                        "single" -> "单选"
                        "multi" -> "多选"
                        "fill" -> "填空"
                        else -> question.type
                    }
                    val correctOptionIndex: Int? = when {
                        displayType == "判断" || displayType == "判断题" -> {
                            val ans = question.answer.trim()
                            when {
                                ans.equals("A", true) || ans == "正确" || ans == "对" -> 0
                                ans.equals("B", true) || ans == "错误" || ans == "错" -> 1
                                else -> null
                            }
                        }
                        displayType == "单选" || displayType == "单选题" ||
                        displayType == "多选" || displayType == "多选题" -> {
                            val firstLetter = question.answer.trim().firstOrNull { it.isLetter() }
                            if (firstLetter != null) {
                                PracticeUtils.optionLabels.indexOf(firstLetter.uppercase())
                                    .takeIf { it >= 0 }
                            } else null
                        }
                        else -> null
                    }

                    options.forEachIndexed { index, option ->
                        val label = PracticeUtils.optionLabels.getOrNull(index) ?: ""
                        val isCorrectAnswer = index == correctOptionIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "$label.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCorrectAnswer) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCorrectAnswer) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCorrectAnswer) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCorrectAnswer) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 答案
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "答案：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = question.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Analysis (collapsible)
                if (!question.analysis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { analysisExpanded = !analysisExpanded }) {
                        Text(
                            if (analysisExpanded) "收起解析" else "展开解析",
                            fontSize = 13.sp
                        )
                    }
                    if (analysisExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = question.analysis ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onCopyAnswer(question.answer) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    ) {
                        Text("复制答案", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 14.sp)
                    }
                }
            } else {
                Text(
                    text = "未找到匹配题目",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}
