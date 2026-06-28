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
import com.ck66.dusou.util.FileLogger
import java.nio.ByteBuffer

class ScreenCaptureManager private constructor() {

    companion object {
        val instance: ScreenCaptureManager by lazy { ScreenCaptureManager() }
    }

    /** 截屏状态变化监听器 */
    interface CaptureStateListener {
        /** 截屏 token 失效（如锁屏/旋转后），需要重新授权 */
        fun onCaptureFailed()
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var captureListener: CaptureStateListener? = null

    /** 必须使用 @Volatile，因为 ImageReader 回调在后台线程写入，captureScreen 可能在主线程读取 */
    @Volatile
    private var latestBitmap: Bitmap? = null

    @Volatile
    private var isCapturing = false

    /** 首帧就绪回调，解决截屏时 latestBitmap 为 null 的问题 */
    private var firstFrameCallback: (() -> Unit)? = null

    fun startCapture(context: Context, resultCode: Int, data: Intent) {
        val appContext = context.applicationContext
        val displayMetrics = DisplayMetrics()
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi

        FileLogger.i("ScreenCapture", "startCapture: screen=${screenWidth}x${screenHeight}, density=$screenDensity")

        // 验证屏幕尺寸有效性
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw IllegalStateException("屏幕尺寸无效: ${screenWidth}x${screenHeight}")
        }

        val manager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)

        // 注册 MediaProjection 回调，监听 token 失效
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // Token 失效（锁屏/旋转/用户撤销等），通知外部
                isCapturing = false
                captureListener?.onCaptureFailed()
            }
        }, null)

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
                        val isFirstFrame = latestBitmap == null
                        val oldBitmap = latestBitmap
                        latestBitmap = imageToBitmap(image)
                        oldBitmap?.recycle()  // 回收旧帧，避免 OOM
                        if (isFirstFrame) {
                            firstFrameCallback?.invoke()
                        }
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
        val full = latestBitmap
        FileLogger.i("ScreenCapture", "captureScreen: latestBitmap=${full != null}, isCapturing=$isCapturing")
        if (full == null) return null
        return full.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** 保存区域截图坐标（像素，基于屏幕分辨率） */
    fun saveCropRect(x: Int, y: Int, w: Int, h: Int) {
        cropX = x
        cropY = y
        cropW = w
        cropH = h
        hasCropRect = true
    }

    /** 清除已保存的区域 */
    fun clearCropRect() {
        hasCropRect = false
        cropX = 0
        cropY = 0
        cropW = 0
        cropH = 0
    }

    /** 是否有已保存的截图区域 */
    fun hasCropRect(): Boolean = hasCropRect

    /** 获取已保存的截图区域坐标 */
    fun getCropRect(): CropRect = CropRect(cropX, cropY, cropW, cropH)

    data class CropRect(val x: Int, val y: Int, val w: Int, val h: Int)

    private var cropX = 0
    private var cropY = 0
    private var cropW = 0
    private var cropH = 0
    private var hasCropRect = false

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

    fun setCaptureListener(listener: CaptureStateListener?) {
        captureListener = listener
    }

    fun setFirstFrameCallback(callback: (() -> Unit)?) {
        firstFrameCallback = callback
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        buffer.rewind() // 确保 buffer position 在起始位置
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val tempBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        tempBitmap.copyPixelsFromBuffer(buffer)

        val result = Bitmap.createBitmap(tempBitmap, 0, 0, image.width, image.height)
        if (result != tempBitmap) {
            tempBitmap.recycle()  // 回收中间 Bitmap，避免泄漏
        }
        return result
    }
}
