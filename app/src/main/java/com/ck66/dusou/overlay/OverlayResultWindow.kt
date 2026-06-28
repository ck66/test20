package com.ck66.dusou.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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

    // 拖动状态
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragInitialTouchX = 0f
    private var dragInitialTouchY = 0f
    private var isDragging = false

    companion object {
        private const val PREFS_NAME = "overlay_position_prefs"
        private const val KEY_POS_X = "overlay_pos_x"
        private const val KEY_POS_Y = "overlay_pos_y"
        private const val POS_UNSET = Int.MIN_VALUE

        private fun savePosition(context: Context, x: Int, y: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_POS_X, x)
                .putInt(KEY_POS_Y, y)
                .apply()
        }

        private fun loadPosition(context: Context): Pair<Int, Int>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val x = prefs.getInt(KEY_POS_X, POS_UNSET)
            val y = prefs.getInt(KEY_POS_Y, POS_UNSET)
            return if (x != POS_UNSET && y != POS_UNSET) Pair(x, y) else null
        }
    }

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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            // 恢复上次位置，没有则默认居中偏上
            val savedPos = loadPosition(context)
            if (savedPos != null) {
                x = savedPos.first
                y = savedPos.second
            } else {
                val dm = context.resources.displayMetrics
                x = (dm.widthPixels - 360 * dm.density.toInt()) / 2
                y = (dm.heightPixels * 0.3).toInt()
            }
        }

        // 设置拖动监听
        setupDragListener(view, params)

        overlayView = view
        layoutParams = params
        windowManager?.addView(view, params)
        isShowing = true
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = params.x
                    dragInitialY = params.y
                    dragInitialTouchX = event.rawX
                    dragInitialTouchY = event.rawY
                    isDragging = false
                    false  // 不消费，让事件传给 Compose 内部按钮
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - dragInitialTouchX).toInt()
                    val deltaY = (event.rawY - dragInitialTouchY).toInt()
                    if (kotlin.math.abs(deltaX) > 15 || kotlin.math.abs(deltaY) > 15) {
                        isDragging = true
                        val newX = dragInitialX + deltaX
                        val newY = dragInitialY + deltaY
                        val dm = context.resources.displayMetrics
                        params.x = newX.coerceIn(-dm.widthPixels / 2, dm.widthPixels / 2)
                        params.y = newY.coerceIn(0, dm.heightPixels - 100)
                        windowManager?.updateViewLayout(view, params)
                        true  // 消费事件，阻止传给按钮
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        savePosition(context, params.x, params.y)
                        true  // 消费事件，不触发按钮点击
                    } else {
                        false  // 不是拖动，让按钮正常响应
                    }
                }
                else -> false
            }
        }
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

    // 带透明度的文字颜色，让弹窗文字也能透出下方内容
    val textAlpha = 0.7f
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
    val errorColor = MaterialTheme.colorScheme.error.copy(alpha = textAlpha)

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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
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
                        color = onSurfaceColor,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 13.sp, color = primaryColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Similarity
            Text(
                text = "相似度 ${"%.0f".format(matchResult.similarity * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (matchResult.matched) primaryColor else errorColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (question != null) {
                // 题干
                Text(
                    text = question.stem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor,
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
                                color = if (isCorrectAnswer) primaryColor else onSurfaceColor,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCorrectAnswer) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCorrectAnswer) primaryColor else onSurfaceColor,
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
                        color = onSurfaceVariantColor
                    )
                    Text(
                        text = question.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }

                // Analysis (collapsible)
                if (!question.analysis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { analysisExpanded = !analysisExpanded }) {
                        Text(
                            if (analysisExpanded) "收起解析" else "展开解析",
                            fontSize = 13.sp,
                            color = primaryColor
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
                                color = onSurfaceVariantColor
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
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    ) {
                        Text("复制答案", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 14.sp, color = primaryColor)
                    }
                }
            } else {
                Text(
                    text = "未找到匹配题目",
                    style = MaterialTheme.typography.bodyLarge,
                    color = errorColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = primaryColor)
                }
            }
        }
    }
}
