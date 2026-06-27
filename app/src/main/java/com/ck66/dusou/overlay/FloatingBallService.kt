package com.ck66.dusou.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import com.ck66.dusou.MainActivity
import com.ck66.dusou.R
import com.ck66.dusou.capture.ScreenCaptureService
import com.ck66.dusou.ui.theme.md_theme_light_primary

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBall: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    companion object {
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIFICATION_ID = 2001
        private const val BALL_SIZE_DP = 56

        var clickCallback: (() -> Unit)? = null

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        showFloatingBall()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clickCallback = null
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
                    clickCallback?.invoke()
                }
                return true
            }
        }
        return false
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
