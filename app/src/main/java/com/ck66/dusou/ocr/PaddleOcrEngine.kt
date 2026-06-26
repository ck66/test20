package com.ck66.dusou.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    init {
        // 在后台线程中初始化 PaddleOCR，避免主线程阻塞
        Thread({
            try {
                OpenCVUtils.init(appContext)
                // PaddleOCR.create 是 suspend 函数，但我们在非 Android 主线程中
                // 使用 runBlocking 是安全的，因为这是在后台工作线程中
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
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
                }
            } catch (e: Throwable) {
                // CancellationException 是协程取消信号，必须重新抛出
                if (e is CancellationException) throw e
                synchronized(initLock) {
                    initError = e
                }
            }
        }, "PaddleOCR-Init").start()
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
        ocr?.release()
        ocr = null
    }
}
