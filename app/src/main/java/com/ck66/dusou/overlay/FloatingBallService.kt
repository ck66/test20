package com.ck66.dusou.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ck66.dusou.MainActivity
import com.ck66.dusou.R
import com.ck66.dusou.capture.ScreenCaptureService
import com.ck66.dusou.matcher.TextMatcher
import com.ck66.dusou.ocr.OcrEngineProvider
import com.ck66.dusou.ui.theme.md_theme_light_primary
import com.ck66.dusou.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBall: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var downTime = 0L
    private var lastClickTime = 0L
    private var pendingSingleClick: Runnable? = null
    private val clickHandler = Handler(Looper.getMainLooper())
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    private var searchViewModel: ScreenSearchViewModel? = null
    private var resultWindow: OverlayResultWindow? = null
    private var searchScope: CoroutineScope? = null
    private var regionOverlay: RegionSelectOverlay? = null

    companion object {
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIFICATION_ID = 2001
        private const val BALL_SIZE_DP = 56

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // 初始化搜索组件并收集结果
        // ViewModel 和 state 收集绑定到 Service 生命周期而非 Activity
        searchViewModel = ScreenSearchViewModel(
            ocrEngine = OcrEngineProvider.get(),
            textMatcher = TextMatcher(context = applicationContext)
        )
        resultWindow = OverlayResultWindow(applicationContext)
        searchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        searchScope?.launch {
            searchViewModel?.state?.collect { state ->
                when (state) {
                    is ScreenSearchState.Result -> {
                        resultWindow?.show(state.match)
                    }
                    is ScreenSearchState.NotFound -> {
                        resultWindow?.dismiss()
                        Toast.makeText(applicationContext, "未找到匹配题目", Toast.LENGTH_SHORT).show()
                    }
                    is ScreenSearchState.Error -> {
                        resultWindow?.dismiss()
                        Toast.makeText(applicationContext, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        showFloatingBall()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        searchScope?.cancel()
        searchScope = null
        searchViewModel?.destroy()
        searchViewModel = null
        resultWindow?.dismiss()
        resultWindow = null

        try {
            removeFloatingBall()
        } finally {
            ScreenCaptureManager.instance.stopCapture()
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮球常驻通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("读屏搜题已启动")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showFloatingBall() {
        val ballSize = (BALL_SIZE_DP * resources.displayMetrics.density).toInt()

        // 创建 LifecycleOwner 注入到 ComposeView，否则在系统窗口（WindowManager）中
        // Compose 找不到 ViewTreeLifecycleOwner 会崩溃
        val lifecycleOwner = ServiceLifecycleOwner().also {
            serviceLifecycleOwner = it
        }
        lifecycleOwner.performStart(null)

        val ballView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                Box(
                    modifier = Modifier
                        .size(BALL_SIZE_DP.dp)
                        .clip(CircleShape)
                        .background(md_theme_light_primary)
                ) {
                    // Invisible touch layer for drag/click handling via AndroidView
                    AndroidView(
                        factory = { ctx ->
                            View(ctx).apply {
                                setOnTouchListener { _, event ->
                                    handleBallTouch(event)
                                }
                            }
                        },
                        modifier = Modifier.size(BALL_SIZE_DP.dp)
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        layoutParams = params
        floatingBall = ballView
        windowManager.addView(ballView, params)
    }

    private fun handleBallTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()

                if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) {
                    isDragging = true
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    floatingBall?.let { windowManager.updateViewLayout(it, params) }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val holdDuration = System.currentTimeMillis() - downTime
                    FileLogger.i("FloatingBall", "Ball clicked, hold=${holdDuration}ms, isDragging=$isDragging")
                    if (holdDuration >= 1000) {
                        FileLogger.i("FloatingBall", "Long press → hide")
                        FloatingBallManager.hide(applicationContext)
                    } else {
                        // 双击检测：300ms 内连续两次点击 → 双击 → 区域选择
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300 && pendingSingleClick != null) {
                            // 第二次点击在 300ms 内 → 双击
                            clickHandler.removeCallbacks(pendingSingleClick!!)
                            pendingSingleClick = null
                            FileLogger.i("FloatingBall", "Double click → region select")
                            onDoubleClick()
                        } else {
                            // 第一次点击 → 延迟等待判断是否双击
                            pendingSingleClick?.let { clickHandler.removeCallbacks(it) }
                            val runnable = Runnable {
                                pendingSingleClick = null
                                performScreenSearch()
                            }
                            pendingSingleClick = runnable
                            clickHandler.postDelayed(runnable, 300)
                        }
                        lastClickTime = now
                    }
                }
                return true
            }
        }
        return false
    }

    private fun performScreenSearch() {
        val capture = ScreenCaptureManager.instance
        val isCapturing = capture.isCapturing()
        val fullBitmap = capture.captureScreen()
        FileLogger.i("FloatingBall", "performScreenSearch: isCapturing=$isCapturing, bitmap=${fullBitmap != null}, size=${fullBitmap?.width}x${fullBitmap?.height}")

        if (fullBitmap != null) {
            // 如果有保存的截图区域，裁剪只保留题目部分
            val searchBitmap = if (capture.hasCropRect()) {
                val rect = capture.getCropRect()
                FileLogger.i("FloatingBall", "CropRect: ${rect.x},${rect.y} ${rect.w}x${rect.h}, bitmap=${fullBitmap.width}x${fullBitmap.height}")
                try {
                    android.graphics.Bitmap.createBitmap(fullBitmap, rect.x, rect.y, rect.w, rect.h).also {
                        FileLogger.i("FloatingBall", "Cropped region: ${rect.x},${rect.y} ${rect.w}x${rect.h}, result=${it.width}x${it.height}")
                    }
                } catch (e: Exception) {
                    FileLogger.e("FloatingBall", "Crop failed, using full bitmap", e)
                    fullBitmap
                }
            } else {
                FileLogger.i("FloatingBall", "No crop rect, using full bitmap")
                fullBitmap
            }
            searchViewModel?.searchFromScreenCapture(searchBitmap)
        } else {
            FileLogger.w("FloatingBall", "captureScreen returned null")
            Toast.makeText(applicationContext, "截屏失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onDoubleClick() {
        regionOverlay?.dismiss()
        regionOverlay = RegionSelectOverlay(applicationContext)
        regionOverlay!!.show()
    }

    private fun removeFloatingBall() {
        floatingBall?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        floatingBall = null
        serviceLifecycleOwner?.performStop()
        serviceLifecycleOwner = null
    }
}
