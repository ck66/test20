package com.ck66.dusou.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class PaddleOcrEngine(context: Context) : OcrEngine {

    @Volatile
    private var ocr: PaddleOCR? = null
    @Volatile
    private var initError: Throwable? = null
    private val initLock = Any()
    private val appContext = context.applicationContext
    private val recognitionInProgress = AtomicInteger(0)
    private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        initScope.launch {
            try {
                OpenCVUtils.init(appContext)
                val engine = PaddleOCR.create(
                    context = appContext,
                    config = PaddleOCRConfig(
                        detThresh = 0.3f,
                        detBoxThresh = 0.6f,
                    ),
                    engineConfig = EngineConfig(),
                    detModelAssetPath = "models/det/inference.onnx",
                    recModelAssetPath = "models/rec/inference.onnx",
                    recConfigAssetPath = "models/rec/inference.yml",
                )
                synchronized(initLock) {
                    ocr = engine
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                synchronized(initLock) {
                    initError = e
                }
            }
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val engine = waitForInit()
        recognitionInProgress.incrementAndGet()
        try {
            // 直接使用 Bitmap 进行识别，避免 JPEG 压缩/解压的质量损失和延迟
            val result = withContext(Dispatchers.IO) {
                engine.recognize(bitmap)
            }

            return OcrResult(
                text = result.results.joinToString("\n") { it.text },
                confidence = if (result.results.isNotEmpty()) {
                    result.results.map { it.confidence }.average().toFloat()
                } else 0f,
                blocks = result.results.map { item ->
                    val rect = if (item.box.points.size == 4) {
                        val pts = item.box.points
                        val left = pts.minOf { it.x }.toInt().coerceAtLeast(0)
                        val top = pts.minOf { it.y }.toInt().coerceAtLeast(0)
                        val right = pts.maxOf { it.x }.toInt()
                        val bottom = pts.maxOf { it.y }.toInt()
                        Rect(left, top, right, bottom)
                    } else null

                    OcrBlock(
                        text = item.text,
                        confidence = item.confidence,
                        rect = rect
                    )
                }
            )
        } finally {
            recognitionInProgress.decrementAndGet()
        }
    }

    /**
     * 轮询等待 OCR 引擎初始化完成（非阻塞，使用协程 delay）
     * 超过 30 秒未完成则抛出超时异常
     */
    private suspend fun waitForInit(): PaddleOCR {
        val maxAttempts = 300 // 30 seconds (300 * 100ms)
        var attempts = 0
        while (attempts < maxAttempts) {
            synchronized(initLock) {
                ocr?.let { return it }
                initError?.let { throw IllegalStateException("OCR 引擎初始化失败", it) }
            }
            delay(100)
            attempts++
        }
        throw IllegalStateException("OCR 引擎初始化超时（${maxAttempts * 100 / 1000}秒）")
    }

    override suspend fun release() {
        // 等待所有正在进行中的识别任务完成
        var waited = 0
        while (recognitionInProgress.get() > 0 && waited < 100) {
            delay(50)
            waited++
        }
        if (recognitionInProgress.get() > 0) {
            throw IllegalStateException("仍有 ${recognitionInProgress.get()} 个识别任务进行中，无法安全释放")
        }
        // 在锁外释放：recognitionInProgress=0 保证无并发识别
        // synchronized 仅用于与 waitForInit() 的读写互斥
        val engine = synchronized(initLock) {
            val ref = ocr
            ocr = null
            ref
        }
        engine?.release()
        initScope.cancel()
    }
}
