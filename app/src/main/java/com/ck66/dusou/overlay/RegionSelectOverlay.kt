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
 * 区域选择悬浮层：全屏半透明遮罩 + 拖拽矩形选区。
 * 用户拖拽选定后自动保存坐标到 ScreenCaptureManager，并关闭自身。
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

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isDragging = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    endX = event.x
                    endY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    val left = minOf(startX, endX).toInt().coerceAtLeast(0)
                    val top = minOf(startY, endY).toInt().coerceAtLeast(0)
                    val right = maxOf(startX, endX).toInt().coerceAtMost(width)
                    val bottom = maxOf(startY, endY).toInt().coerceAtMost(height)
                    val w = right - left
                    val h = bottom - top
                    if (w > 20 && h > 20) {
                        onRegionSelected?.invoke(left, top, w, h)
                    } else {
                        invalidate()
                    }
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (isDragging || (endX != startX && endY != startY)) {
            val rect = Rect(
                minOf(startX, endX).toInt(),
                minOf(startY, endY).toInt(),
                maxOf(startX, endX).toInt(),
                maxOf(startY, endY).toInt()
            )
            // Clear the selected region
            canvas.drawRect(rect, clearPaint)
            // Draw border
            canvas.drawRect(rect, borderPaint)
            // Draw corner markers
            val cornerSize = 16f
            canvas.drawRect(rect.left.toFloat() - 4, rect.top.toFloat() - 4, rect.left.toFloat() + cornerSize, rect.top.toFloat() + cornerSize, cornerPaint)
            canvas.drawRect(rect.right.toFloat() - cornerSize, rect.top.toFloat() - 4, rect.right.toFloat() + 4, rect.top.toFloat() + cornerSize, cornerPaint)
            canvas.drawRect(rect.left.toFloat() - 4, rect.bottom.toFloat() - cornerSize, rect.left.toFloat() + cornerSize, rect.bottom.toFloat() + 4, cornerPaint)
            canvas.drawRect(rect.right.toFloat() - cornerSize, rect.bottom.toFloat() - cornerSize, rect.right.toFloat() + 4, rect.bottom.toFloat() + 4, cornerPaint)
        }
    }
}
