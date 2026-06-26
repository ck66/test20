package com.ck66.dusou.ocr

import android.content.Context

object OcrEngineProvider {
    @Volatile
    private var engine: OcrEngine? = null
    private val initLock = Any()

    fun init(context: Context) {
        synchronized(initLock) {
            if (engine != null) return
            engine = PaddleOcrEngine(context.applicationContext)
        }
    }

    fun get(): OcrEngine {
        return engine ?: throw IllegalStateException("OcrEngineProvider not initialized. Call init() first.")
    }

    fun set(engine: OcrEngine) {
        synchronized(initLock) {
            this.engine = engine
        }
    }
}
