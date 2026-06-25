package com.ck66.dusou.ocr

import android.content.Context
import android.graphics.Bitmap

/**
 * PaddleOCR 引擎占位实现。
 * TODO: 集成 PaddleOCR AAR 后替换 MlKitOcrEngine。
 *       PaddleOCR v6 离线识别、不依赖网络，适合读屏搜题场景。
 */
class PaddleOcrEngine(context: Context) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        throw NotImplementedError("PaddleOCR AAR not yet integrated")
    }

    override fun release() {
        // No resources to release yet
    }
}
