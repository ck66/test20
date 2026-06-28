package com.ck66.dusou.ui.search

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ck66.dusou.matcher.MatchResult
import java.io.OutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照搜题") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is SearchUiState.Idle,
                is SearchUiState.Recognizing,
                is SearchUiState.Matching -> {
                    CameraPreviewContent(
                        capturedBitmap = capturedBitmap,
                        isProcessing = uiState !is SearchUiState.Idle,
                        currentState = uiState,
                        onCapture = { bitmap -> viewModel.setCapturedBitmap(bitmap) },
                        onRetake = { viewModel.clearCapturedBitmap() },
                        modifier = Modifier.weight(1f)
                    )
                }

                is SearchUiState.Cropping -> {
                    val bmp = (uiState as SearchUiState.Cropping).bitmap
                    CropPreviewContent(
                        bitmap = bmp,
                        viewModel = viewModel,
                        isProcessing = false,
                        modifier = Modifier.weight(1f)
                    )
                }

                is SearchUiState.Result -> {
                    ResultContent(
                        match = (uiState as SearchUiState.Result).match,
                        onNewSearch = { viewModel.clearResult() },
                        modifier = Modifier.weight(1f)
                    )
                }

                is SearchUiState.NotFound -> {
                    NotFoundContent(
                        ocrText = (uiState as SearchUiState.NotFound).ocrText,
                        onRetake = { viewModel.clearCapturedBitmap(); viewModel.clearResult() },
                        modifier = Modifier.weight(1f)
                    )
                }

                is SearchUiState.Error -> {
                    ErrorContent(
                        message = (uiState as SearchUiState.Error).message,
                        onRetry = {
                            viewModel.clearResult()
                            capturedBitmap?.let { viewModel.searchFromBitmap(it) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    capturedBitmap: Bitmap?,
    isProcessing: Boolean,
    currentState: SearchUiState,
    onCapture: (Bitmap) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // 离开页面时解绑相机，防止泄漏
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    // Camera permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要相机权限才能拍照搜题", Toast.LENGTH_SHORT).show()
        }
    }

    // Request camera permission on first composition if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (capturedBitmap != null) {
            // Show captured photo preview
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "拍摄的照片",
                        modifier = Modifier.fillMaxSize()
                    )

                    // Guide lines overlay on preview
                    GuideLinesOverlay()
                }

                // Bottom actions
                BottomActionBar(
                    isProcessing = isProcessing,
                    currentState = currentState,
                    onCapture = { onCapture(capturedBitmap) },
                    onRetake = onRetake
                )
            }
        } else if (!hasCameraPermission) {
            // Show permission request UI
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "需要相机权限",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "请授予相机权限以使用拍照搜题功能",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                    ) {
                        Text("授予权限")
                    }
                }
            }
        } else {
            // Show live camera preview
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also { previewView = it }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                                .also { imageCapture = it }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    capture
                                )
                            } catch (_: Exception) {
                                // Camera binding may fail; handled by the error UI
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Guide lines overlay
                GuideLinesOverlay()

                // Center crosshair
                CameraCrosshair()
            }

            // Capture button at bottom center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                CaptureButton(
                    enabled = !isProcessing && imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@CaptureButton
                        val mainHandler = Handler(Looper.getMainLooper())
                        capture.takePicture(
                            Executors.newSingleThreadExecutor(),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                                    try {
                                        val bitmap = imageProxyToBitmap(imageProxy)
                                        if (bitmap != null) {
                                            onCapture(bitmap)
                                        } else {
                                            mainHandler.post {
                                                Toast.makeText(context, "拍照失败：无法处理图像", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        mainHandler.post {
                                            Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        imageProxy.close()
                                    }
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    mainHandler.post {
                                        Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun GuideLinesOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.3f)
        val strokeWidth = 1.dp.toPx()

        // Horizontal guidelines (rule of thirds)
        val hStep = size.height / 3
        drawLine(
            color = lineColor,
            start = Offset(0f, hStep),
            end = Offset(size.width, hStep),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, hStep * 2),
            end = Offset(size.width, hStep * 2),
            strokeWidth = strokeWidth
        )

        // Vertical guidelines (rule of thirds)
        val vStep = size.width / 3
        drawLine(
            color = lineColor,
            start = Offset(vStep, 0f),
            end = Offset(vStep, size.height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(vStep * 2, 0f),
            end = Offset(vStep * 2, size.height),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
private fun CameraCrosshair() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val armLength = size.width / 2
            val color = Color.White.copy(alpha = 0.5f)
            val strokeWidth = 2.dp.toPx()

            // Horizontal line
            drawLine(color, Offset(center.x - armLength, center.y), Offset(center.x + armLength, center.y), strokeWidth)
            // Vertical line
            drawLine(color, Offset(center.x, center.y - armLength), Offset(center.x, center.y + armLength), strokeWidth)
            // Circle
            drawCircle(color, radius = size.width / 4, center = center, style = Stroke(strokeWidth))
        }
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Camera,
            contentDescription = "拍照",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun BottomActionBar(
    isProcessing: Boolean,
    currentState: SearchUiState,
    onCapture: () -> Unit,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (currentState) {
                    is SearchUiState.Recognizing -> "正在识别文字..."
                    is SearchUiState.Matching -> "正在匹配题目..."
                    else -> "处理中..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("重拍")
                }

                Button(
                    onClick = onCapture,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("识别搜题")
                }
            }
        }
    }
}

@Composable
private fun ResultContent(
    match: MatchResult,
    onNewSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val question = match.question ?: return

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Similarity badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "匹配成功 · 相似度 ${"%.0f".format(match.similarity * 100)}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // OCR text preview
        if (match.ocrText.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "识别文本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.ocrText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Question stem
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "题目",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.stem,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Answer
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "答案",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.answer,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Analysis (if available)
        if (!question.analysis.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "解析",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = question.analysis ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // New search button
        Button(
            onClick = onNewSearch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("继续搜题")
        }
    }
}

@Composable
private fun NotFoundContent(
    ocrText: String,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "未找到匹配题目",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (ocrText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "识别到的文字：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ocrText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重新拍照")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "出错了",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    } // Column
} // ErrorContent

/**
 * 从 CameraX ImageProxy 转换为 Bitmap（内存中操作，避免文件 I/O）
 * 处理设备摄像头旋转，确保 OCR 识别方向正确
 */
private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): Bitmap? {
    val format = imageProxy.format
    val rawBitmap: Bitmap? = when (format) {
        android.graphics.ImageFormat.JPEG, android.graphics.ImageFormat.DEPTH_JPEG -> {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        android.graphics.ImageFormat.YUV_420_888 -> {
            val nv21Data = imageProxyToByteArray(imageProxy)
            if (nv21Data.isEmpty()) {
                null
            } else {
                val yuvImage = android.graphics.YuvImage(
                    nv21Data,
                    android.graphics.ImageFormat.NV21,
                    imageProxy.width,
                    imageProxy.height,
                    null
                )
                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 95, out)
                val jpegBytes = out.toByteArray()
                android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            }
        }
        else -> null
    }

    // 处理摄像头旋转：将图片旋转到正确方向
    rawBitmap ?: return null
    val rotation = imageProxy.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
            if (it != rawBitmap) rawBitmap.recycle()
        }
    } else {
        rawBitmap
    }
}

private fun imageProxyToByteArray(image: androidx.camera.core.ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    // 防御：部分设备的 CameraX 实现中 buffer 可能已被消费导致 remaining()==0
    if (ySize == 0) {
        return ByteArray(0)
    }

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    // NV21 格式: Y + V + U 交叠
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    return nv21
}

@Composable
private fun CropPreviewContent(
    bitmap: Bitmap,
    viewModel: SearchViewModel,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        // Photo preview
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "拍摄的照片",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Crop overlay
        if (canvasWidth > 0 && canvasHeight > 0) {
            CropOverlay(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                onCropConfirmed = { cropRect ->
                    viewModel.searchFromCroppedBitmap(bitmap, cropRect)
                },
                onSkip = {
                    viewModel.searchFromBitmapDirect(bitmap)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("处理中...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { viewModel.clearCapturedBitmap() }) {
                        Text("重拍")
                    }
                    OutlinedButton(onClick = { viewModel.searchFromBitmapDirect(bitmap) }) {
                        Text("跳过裁剪")
                    }
                    Button(onClick = { viewModel.searchFromBitmapDirect(bitmap) }) {
                        Text("识别搜题")
                    }
                }
            }
        }
    }
}
