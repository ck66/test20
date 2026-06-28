package com.ck66.dusou.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * 区域选择悬浮层：全屏半透明遮罩 + 可拖拽调整的区域框。
 * 
 * 交互：
 * - 预设区域框（上次保存的或默认 80%×60%）
 * - 拖动框内 → 移动位置
 * - 拖动四角 → 调整大小（保持对角不动）
 * - 拖动四边 → 调整宽度或高度
 * - 双击任意位置 → 保存并关闭
 */
class RegionSelectOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: RegionSelectView? = null

    fun show() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = RegionSelectView(context).apply {
            onRegionSelected = { x, y, w, h ->
                ScreenCaptureManager.instance.saveCropRect(x, y, w, h)
                Toast.makeText(context, "截图区域已保存", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
        overlayView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(view, params)
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        windowManager = null
    }
}

private class RegionSelectView(context: Context) : View(context) {

    var onRegionSelected: ((Int, Int, Int, Int) -> Unit)? = null

    init {
        // 关闭硬件加速，确保 PorterDuff.Mode.CLEAR 镂空效果在所有设备上一致工作
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ────────── 画笔 ──────────
    private val bgPaint = Paint().apply {
        color = Color.argb(128, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val clearPaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.FILL
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = Color.rgb(66, 133, 244)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14.dpToPx()
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // ────────── 区域框坐标（相对于 view）──────────
    private var rectLeft = 0f
    private var rectTop = 0f
    private var rectRight = 0f
    private var rectBottom = 0f

    // ────────── 拖拽状态 ──────────
    private enum class DragMode {
        NONE,
        MOVE,
        RESIZE_TOP_LEFT, RESIZE_TOP, RESIZE_TOP_RIGHT,
        RESIZE_LEFT, RESIZE_RIGHT,
        RESIZE_BOTTOM_LEFT, RESIZE_BOTTOM, RESIZE_BOTTOM_RIGHT
    }
    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ────────── 双击检测 ──────────
    private var lastClickTime = 0L

    // ────────── 触摸区域尺寸 ──────────
    private val cornerSize = 40.dpToPx()       // 四角触摸区域
    private val edgeSize = 20.dpToPx()         // 边缘触摸区域

    // 最小区域尺寸
    private val minRegionWidth = 100.dpToPx()
    private val minRegionHeight = 80.dpToPx()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 检查是否有已保存区域
        if (ScreenCaptureManager.instance.hasCropRect()) {
            val saved = ScreenCaptureManager.instance.getCropRect()
            // 转换坐标（减去状态栏偏移）
            val statusBarHeight = getStatusBarHeight()
            rectLeft = saved.x.toFloat()
            rectTop = (saved.y - statusBarHeight).toFloat()
            rectRight = rectLeft + saved.w
            rectBottom = rectTop + saved.h
        } else {
            // 默认区域：屏幕中心，80% 宽度 × 60% 高度
            val regionW = width * 0.8f
            val regionH = height * 0.6f
            rectLeft = (width - regionW) / 2
            rectTop = (height - regionH) / 2
            rectRight = rectLeft + regionW
            rectBottom = rectTop + regionH
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                // 判断触摸位置
                dragMode = detectDragMode(event.x, event.y)

                // 双击检测
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 300) {
                    // 双击 → 保存并关闭
                    saveRegionAndDismiss()
                    return true
                }
                lastClickTime = now

                return dragMode != DragMode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragMode != DragMode.NONE) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    applyDrag(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return false
    }

    private fun detectDragMode(x: Float, y: Float): DragMode {
        // 四个角优先
        if (x in rectLeft - cornerSize..rectLeft + cornerSize &&
            y in rectTop - cornerSize..rectTop + cornerSize) {
            return DragMode.RESIZE_TOP_LEFT
        }
        if (x in rectRight - cornerSize..rectRight + cornerSize &&
            y in rectTop - cornerSize..rectTop + cornerSize) {
            return DragMode.RESIZE_TOP_RIGHT
        }
        if (x in rectLeft - cornerSize..rectLeft + cornerSize &&
            y in rectBottom - cornerSize..rectBottom + cornerSize) {
            return DragMode.RESIZE_BOTTOM_LEFT
        }
        if (x in rectRight - cornerSize..rectRight + cornerSize &&
            y in rectBottom - cornerSize..rectBottom + cornerSize) {
            return DragMode.RESIZE_BOTTOM_RIGHT
        }

        // 四条边
        if (x in rectLeft..rectRight && y in rectTop - edgeSize..rectTop + edgeSize) {
            return DragMode.RESIZE_TOP
        }
        if (x in rectLeft..rectRight && y in rectBottom - edgeSize..rectBottom + edgeSize) {
            return DragMode.RESIZE_BOTTOM
        }
        if (y in rectTop..rectBottom && x in rectLeft - edgeSize..rectLeft + edgeSize) {
            return DragMode.RESIZE_LEFT
        }
        if (y in rectTop..rectBottom && x in rectRight - edgeSize..rectRight + edgeSize) {
            return DragMode.RESIZE_RIGHT
        }

        // 中心区域
        if (x in rectLeft..rectRight && y in rectTop..rectBottom) {
            return DragMode.MOVE
        }

        return DragMode.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        when (dragMode) {
            DragMode.MOVE -> {
                val newLeft = (rectLeft + dx).coerceIn(0f, (width - (rectRight - rectLeft)).toFloat())
                val newTop = (rectTop + dy).coerceIn(0f, (height - (rectBottom - rectTop)).toFloat())
                val offsetX = newLeft - rectLeft
                val offsetY = newTop - rectTop
                rectLeft = newLeft
                rectTop = newTop
                rectRight += offsetX
                rectBottom += offsetY
            }
            DragMode.RESIZE_TOP_LEFT -> {
                rectLeft = (rectLeft + dx).coerceIn(0f, rectRight - minRegionWidth)
                rectTop = (rectTop + dy).coerceIn(0f, rectBottom - minRegionHeight)
            }
            DragMode.RESIZE_TOP -> {
                rectTop = (rectTop + dy).coerceIn(0f, rectBottom - minRegionHeight)
            }
            DragMode.RESIZE_TOP_RIGHT -> {
                rectRight = (rectRight + dx).coerceIn(rectLeft + minRegionWidth, width.toFloat())
                rectTop = (rectTop + dy).coerceIn(0f, rectBottom - minRegionHeight)
            }
            DragMode.RESIZE_LEFT -> {
                rectLeft = (rectLeft + dx).coerceIn(0f, rectRight - minRegionWidth)
            }
            DragMode.RESIZE_RIGHT -> {
                rectRight = (rectRight + dx).coerceIn(rectLeft + minRegionWidth, width.toFloat())
            }
            DragMode.RESIZE_BOTTOM_LEFT -> {
                rectLeft = (rectLeft + dx).coerceIn(0f, rectRight - minRegionWidth)
                rectBottom = (rectBottom + dy).coerceIn(rectTop + minRegionHeight, height.toFloat())
            }
            DragMode.RESIZE_BOTTOM -> {
                rectBottom = (rectBottom + dy).coerceIn(rectTop + minRegionHeight, height.toFloat())
            }
            DragMode.RESIZE_BOTTOM_RIGHT -> {
                rectRight = (rectRight + dx).coerceIn(rectLeft + minRegionWidth, width.toFloat())
                rectBottom = (rectBottom + dy).coerceIn(rectTop + minRegionHeight, height.toFloat())
            }
            DragMode.NONE -> {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 全屏半透明遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. 清除选择区域（透明）
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, clearPaint)

        // 3. 边框
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, borderPaint)

        // 4. 四角手柄
        val cornerHandleSize = 20f
        canvas.drawRect(rectLeft - 4, rectTop - 4, rectLeft + cornerHandleSize, rectTop + cornerHandleSize, cornerPaint)
        canvas.drawRect(rectRight - cornerHandleSize, rectTop - 4, rectRight + 4, rectTop + cornerHandleSize, cornerPaint)
        canvas.drawRect(rectLeft - 4, rectBottom - cornerHandleSize, rectLeft + cornerHandleSize, rectBottom + 4, cornerPaint)
        canvas.drawRect(rectRight - cornerHandleSize, rectBottom - cornerHandleSize, rectRight + 4, rectBottom + 4, cornerPaint)

        // 5. 提示文字
        val hintText = "双击屏幕保存区域"
        val textX = (rectLeft + rectRight) / 2
        val textY = rectTop - 40f
        canvas.drawText(hintText, textX, textY, hintPaint)
    }

    private fun saveRegionAndDismiss() {
        val left = rectLeft.toInt().coerceAtLeast(0)
        val top = rectTop.toInt().coerceAtLeast(0)
        val w = (rectRight - rectLeft).toInt().coerceAtMost(width - left)
        val h = (rectBottom - rectTop).toInt().coerceAtMost(height - top)

        if (w > 20 && h > 20) {
            // 加上状态栏偏移
            val statusBarHeight = getStatusBarHeight()
            onRegionSelected?.invoke(left, top + statusBarHeight, w, h)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resources = context.resources
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun Int.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}
