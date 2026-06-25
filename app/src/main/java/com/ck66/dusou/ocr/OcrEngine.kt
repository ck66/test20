package com.ck66.dusou.ocr

import android.graphics.Bitmap
import android.graphics.Rect

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
    suspend fun release()
}

data class OcrResult(
    val text: String,
    val confidence: Float,
    val blocks: List<OcrBlock> = emptyList()
)

data class OcrBlock(
    val text: String,
    val confidence: Float,
    val rect: Rect?
)
