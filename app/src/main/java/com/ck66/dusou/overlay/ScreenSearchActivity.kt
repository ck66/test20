package com.ck66.dusou.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ck66.dusou.capture.ScreenCaptureService
import com.ck66.dusou.ui.theme.DusouTheme

class ScreenSearchActivity : ComponentActivity() {

    private val captureManager get() = ScreenCaptureManager.instance

    private val mediaProjectionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val resultCode = result.resultCode
            if (resultCode == Activity.RESULT_OK && data != null) {
                onMediaProjectionGranted(resultCode, data)
            } else {
                Toast.makeText(this, "需要授予屏幕录制权限才能使用读屏搜题", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }

        setContent {
            DusouTheme {
                PermissionGuideScreen(
                    onGrant = { requestMediaProjection() },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = manager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun onMediaProjectionGranted(resultCode: Int, data: Intent) {
        // ① 先启动 ScreenCaptureService 前台服务（Android 14+ 要求
        //    FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 必须在 getMediaProjection() 之前启动）
        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // ② 延迟启动 captureManager，确保 Service 的 startForeground() 执行完毕
        //    避免 Android 14+ 抛出 SecurityException: Media projections require a
        //    foreground service of type mediaProjection
        Handler(Looper.getMainLooper()).postDelayed({
            captureManager.startCapture(this, resultCode, data)
            FloatingBallManager.show(this)
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }, 200)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        Toast.makeText(this, "请授予悬浮窗权限后重试", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun canDrawOverlays(context: android.content.Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }
}

@Composable
private fun PermissionGuideScreen(
    onGrant: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume clicks on background */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "读屏搜题",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "请授予屏幕录制权限以启动读屏搜题\n\n授权后，悬浮球将出现在屏幕上，点击即可截屏搜题",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("授予权限", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消", fontSize = 16.sp)
            }
        }
    }
}
