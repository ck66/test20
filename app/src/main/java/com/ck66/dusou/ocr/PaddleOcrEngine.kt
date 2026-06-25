package com.ck66.dusou.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

class PaddleOcrEngine(context: Context) : OcrEngine {

    private val ocr: PaddleOCR by lazy {
        OpenCVUtils.init(context)
        runBlocking {
            PaddleOCR.create(
                context = context,
                config = PaddleOCRConfig(
                    detThresh = 0.3f,
                    detBoxThresh = 0.6f,
                ),
                engineConfig = EngineConfig(),
                detModelAssetPath = "models/det/inference.onnx",
                recModelAssetPath = "models/rec/inference.onnx",
                recConfigAssetPath = "models/rec/inference.yml",
            )
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val imageBytes = stream.toByteArray()

        val result = ocr.recognize(imageBytes)

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
    }

    override suspend fun release() {
        ocr.release()
    }
}
