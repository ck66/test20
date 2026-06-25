package com.ck66.dusou.ocr

import android.content.Context

object OcrEngineProvider {
    private var engine: OcrEngine? = null

    fun init(context: Context) {
        engine = MlKitOcrEngine(context.applicationContext)
    }

    fun get(): OcrEngine {
        return engine ?: throw IllegalStateException("OcrEngineProvider not initialized. Call init() first.")
    }

    fun set(engine: OcrEngine) {
        this.engine = engine
    }
}
