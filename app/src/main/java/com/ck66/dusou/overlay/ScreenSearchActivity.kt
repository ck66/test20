package com.ck66.dusou.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.ck66.dusou.capture.ScreenCaptureService
import com.ck66.dusou.matcher.TextMatcher
import com.ck66.dusou.ocr.OcrEngineProvider
import com.ck66.dusou.ui.theme.DusouTheme
import kotlinx.coroutines.launch

class ScreenSearchActivity : ComponentActivity() {

    private val captureManager get() = ScreenCaptureManager.instance
    private lateinit var viewModel: ScreenSearchViewModel
    private var resultWindow: OverlayResultWindow? = null

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

        viewModel = ScreenSearchViewModel(
            ocrEngine = OcrEngineProvider.get(),
            textMatcher = TextMatcher()
        )

        resultWindow = OverlayResultWindow(this)

        observeViewModelState()

        setContent {
            DusouTheme {
                PermissionGuideScreen(
                    onGrant = { requestMediaProjection() },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
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

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = manager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    private fun onMediaProjectionGranted(resultCode: Int, data: Intent) {
        captureManager.startCapture(this, resultCode, data)

        val serviceIntent = Intent(this, ScreenCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        FloatingBallManager.onBallClicked = {
            performScreenSearch()
        }
        FloatingBallManager.show(this)

        finish()
    }

    private fun performScreenSearch() {
        val bitmap = captureManager.captureScreen()
        if (bitmap != null) {
            viewModel.searchFromScreenCapture(bitmap)
        } else {
            Toast.makeText(applicationContext, "截屏失败，请重试", Toast.LENGTH_SHORT).show()
        }
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
        // 不在 onDestroy 中停止 captureManager
        // MediaProjection 和 VirtualDisplay 由 ScreenCaptureManager（单例）跨 Activity 生命周期持有
        // 否则会导致 ScreenCaptureService（FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION）因 MediaProjection 被释放而闪退
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
