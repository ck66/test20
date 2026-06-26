package com.ck66.dusou.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.Surface
import java.nio.ByteBuffer

class ScreenCaptureManager private constructor() {

    companion object {
        val instance: ScreenCaptureManager by lazy { ScreenCaptureManager() }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    /** 必须使用 @Volatile，因为 ImageReader 回调在后台线程写入，captureScreen 可能在主线程读取 */
    @Volatile
    private var latestBitmap: Bitmap? = null

    @Volatile
    private var isCapturing = false

    fun startCapture(activity: Activity, resultCode: Int, data: Intent) {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi

        // 验证屏幕尺寸有效性
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw IllegalStateException("屏幕尺寸无效: ${screenWidth}x${screenHeight}")
        }

        val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888,
            2 // maxImages: 使用双缓冲减少内存占用
        ).apply {
            // 使用 null handler 让 ImageReader 在自有后台线程回调，避免主线程阻塞
            setOnImageAvailableListener({ reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        latestBitmap = imageToBitmap(image)
                    }
                } catch (_: Exception) {
                    // 忽略单帧异常，不中断屏幕捕获
                } finally {
                    image?.close()
                }
            }, null)
        }

        imageReader?.let { reader ->
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null, null
            )
        }

        isCapturing = true
    }

    fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        latestBitmap?.recycle()
        latestBitmap = null
    }

    fun captureScreen(): Bitmap? {
        val full = latestBitmap ?: return null
        return full.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun captureRegion(x: Int, y: Int, width: Int, height: Int): Bitmap? {
        val full = latestBitmap ?: return null
        val clippedX = x.coerceIn(0, full.width)
        val clippedY = y.coerceIn(0, full.height)
        val clippedW = width.coerceAtMost(full.width - clippedX)
        val clippedH = height.coerceAtMost(full.height - clippedY)
        if (clippedW <= 0 || clippedH <= 0) return null
        return Bitmap.createBitmap(full, clippedX, clippedY, clippedW, clippedH)
    }

    fun isCapturing(): Boolean = isCapturing

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        buffer.rewind() // 确保 buffer position 在起始位置
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
}
