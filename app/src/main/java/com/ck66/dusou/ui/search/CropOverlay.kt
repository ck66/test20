package com.ck66.dusou.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DragDir {
    NONE, MOVE,
    RESIZE_TOP_LEFT, RESIZE_TOP, RESIZE_TOP_RIGHT,
    RESIZE_LEFT, RESIZE_RIGHT,
    RESIZE_BOTTOM_LEFT, RESIZE_BOTTOM, RESIZE_BOTTOM_RIGHT
}

/**
 * 裁剪选区状态（屏幕坐标）。
 */
data class CropRectState(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * 拍照搜题裁剪选区组件。
 *
 * 选区坐标由外部持有（状态提升），通过 cropRectState/onCropRectChange 双向绑定。
 */
@Composable
fun CropOverlay(
    imageWidth: Int,
    imageHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    cropRectState: CropRectState,
    onCropRectChange: (CropRectState) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // 计算图片在 Fit 模式下的实际显示矩形
    val displayRect = calculateDisplayRectInternal(canvasWidth, canvasHeight, imageWidth, imageHeight)

    val cornerSize = with(density) { 40.dp.toPx() }
    val edgeSize = with(density) { 20.dp.toPx() }
    val minW = displayRect.width * 0.2f
    val minH = displayRect.height * 0.2f

    var dragDir by remember { mutableStateOf(DragDir.NONE) }
    var dragRect by remember { mutableStateOf<CropRectState?>(null) }

    val maskColor = Color.Black.copy(alpha = 0.45f)
    val borderColor = Color(0xFF4285F4)
    val cornerColor = Color.White
    val hintColor = Color.White

    val rectLeft = cropRectState.left
    val rectTop = cropRectState.top
    val rectRight = cropRectState.right
    val rectBottom = cropRectState.bottom

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayRect, minW, minH, cornerSize, edgeSize) {  // ★ 去掉 cropRectState，避免拖拽中断
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragRect = cropRectState  // 记录拖拽起始坐标
                            dragDir = detectDragDirFn(offset.x, offset.y,
                                cropRectState.left, cropRectState.top,
                                cropRectState.right, cropRectState.bottom,
                                cornerSize, edgeSize)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = dragRect ?: return@detectDragGestures
                            val (nl, nt, nr, nb) = applyDragFn(dragDir, dragAmount.x, dragAmount.y,
                                current.left, current.top, current.right, current.bottom,
                                displayRect, minW, minH)
                            val newRect = CropRectState(nl, nt, nr, nb)
                            dragRect = newRect
                            onCropRectChange(newRect)
                        },
                        onDragEnd = {
                            dragDir = DragDir.NONE
                            dragRect = null
                        }
                    )
                }
        ) {
            // 1. 全屏半透明遮罩（四个矩形围绕选区）
            drawRect(maskColor, Offset.Zero, Size(size.width, rectTop))
            drawRect(maskColor, Offset(0f, rectBottom), Size(size.width, size.height - rectBottom))
            drawRect(maskColor, Offset(0f, rectTop), Size(rectLeft, rectBottom - rectTop))
            drawRect(maskColor, Offset(rectRight, rectTop), Size(size.width - rectRight, rectBottom - rectTop))

            // 2. 蓝色边框
            drawRect(
                color = borderColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectRight - rectLeft, rectBottom - rectTop),
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. 白色四角手柄
            val hs = 16f
            drawRect(cornerColor, Offset(rectLeft - 2, rectTop - 2), Size(hs, hs))
            drawRect(cornerColor, Offset(rectRight - hs + 2, rectTop - 2), Size(hs, hs))
            drawRect(cornerColor, Offset(rectLeft - 2, rectBottom - hs + 2), Size(hs, hs))
            drawRect(cornerColor, Offset(rectRight - hs + 2, rectBottom - hs + 2), Size(hs, hs))

            // 4. 提示文字
            val tt = textMeasurer.measure("拖拽调整识别区域", TextStyle(color = hintColor, fontSize = 14.sp))
            val ty = rectTop - tt.size.height - 12f
            if (ty > 0) drawText(tt, topLeft = Offset((rectLeft + rectRight) / 2 - tt.size.width / 2, ty))
        }
    }
}

// ==================== 内部辅助函数 ====================

private fun calculateDisplayRectInternal(
    canvasWidth: Float, canvasHeight: Float, imageWidth: Int, imageHeight: Int
): Rect {
    val imageAspect = imageWidth.toFloat() / imageHeight
    val canvasAspect = canvasWidth / canvasHeight
    return if (imageAspect > canvasAspect) {
        val displayHeight = canvasWidth / imageAspect
        val topOffset = (canvasHeight - displayHeight) / 2
        Rect(0f, topOffset, canvasWidth, topOffset + displayHeight)
    } else {
        val displayWidth = canvasHeight * imageAspect
        val leftOffset = (canvasWidth - displayWidth) / 2
        Rect(leftOffset, 0f, leftOffset + displayWidth, canvasHeight)
    }
}

private fun detectDragDirFn(
    x: Float, y: Float, l: Float, t: Float, r: Float, b: Float, cs: Float, es: Float
): DragDir = when {
    x in (l - cs)..(l + cs) && y in (t - cs)..(t + cs) -> DragDir.RESIZE_TOP_LEFT
    x in (r - cs)..(r + cs) && y in (t - cs)..(t + cs) -> DragDir.RESIZE_TOP_RIGHT
    x in (l - cs)..(l + cs) && y in (b - cs)..(b + cs) -> DragDir.RESIZE_BOTTOM_LEFT
    x in (r - cs)..(r + cs) && y in (b - cs)..(b + cs) -> DragDir.RESIZE_BOTTOM_RIGHT
    x in l..r && y in (t - es)..(t + es) -> DragDir.RESIZE_TOP
    x in l..r && y in (b - es)..(b + es) -> DragDir.RESIZE_BOTTOM
    y in t..b && x in (l - es)..(l + es) -> DragDir.RESIZE_LEFT
    y in t..b && x in (r - es)..(r + es) -> DragDir.RESIZE_RIGHT
    x in l..r && y in t..b -> DragDir.MOVE
    else -> DragDir.NONE
}

private fun applyDragFn(
    dir: DragDir, dx: Float, dy: Float,
    l: Float, t: Float, r: Float, b: Float,
    bounds: Rect, minW: Float, minH: Float
): List<Float> {
    var nl = l; var nt = t; var nr = r; var nb = b
    when (dir) {
        DragDir.MOVE -> {
            val newLeft = (l + dx).coerceIn(bounds.left, bounds.right - (r - l))
            val newTop = (t + dy).coerceIn(bounds.top, bounds.bottom - (b - t))
            val deltaX = newLeft - l; val deltaY = newTop - t
            nl = newLeft; nt = newTop; nr += deltaX; nb += deltaY
        }
        DragDir.RESIZE_TOP_LEFT -> { nl = (l + dx).coerceIn(bounds.left, r - minW); nt = (t + dy).coerceIn(bounds.top, b - minH) }
        DragDir.RESIZE_TOP -> { nt = (t + dy).coerceIn(bounds.top, b - minH) }
        DragDir.RESIZE_TOP_RIGHT -> { nr = (r + dx).coerceIn(l + minW, bounds.right); nt = (t + dy).coerceIn(bounds.top, b - minH) }
        DragDir.RESIZE_LEFT -> { nl = (l + dx).coerceIn(bounds.left, r - minW) }
        DragDir.RESIZE_RIGHT -> { nr = (r + dx).coerceIn(l + minW, bounds.right) }
        DragDir.RESIZE_BOTTOM_LEFT -> { nl = (l + dx).coerceIn(bounds.left, r - minW); nb = (b + dy).coerceIn(t + minH, bounds.bottom) }
        DragDir.RESIZE_BOTTOM -> { nb = (b + dy).coerceIn(t + minH, bounds.bottom) }
        DragDir.RESIZE_BOTTOM_RIGHT -> { nr = (r + dx).coerceIn(l + minW, bounds.right); nb = (b + dy).coerceIn(t + minH, bounds.bottom) }
        DragDir.NONE -> {}
    }
    return listOf(nl, nt, nr, nb)
}
