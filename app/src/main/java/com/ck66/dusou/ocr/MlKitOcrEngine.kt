package com.ck66.dusou.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine(context: Context) : OcrEngine {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        OcrBlock(
                            text = block.text,
                            confidence = block.confidence ?: 0f,
                            rect = block.boundingBox?.let { box ->
                                Rect(box.left, box.top, box.right, box.bottom)
                            }
                        )
                    }

                    val result = OcrResult(
                        text = visionText.text,
                        confidence = blocks.map { it.confidence }.average()
                            .toFloat()
                            .coerceIn(0f, 1f),
                        blocks = blocks
                    )

                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    override fun release() {
        recognizer.close()
    }
}
