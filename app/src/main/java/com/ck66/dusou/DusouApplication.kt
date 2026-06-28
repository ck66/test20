package com.ck66.dusou

import android.app.Application
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.ocr.OcrEngineProvider
import com.ck66.dusou.util.FileLogger

class DusouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)  // 必须放在第一行，确保后续初始化崩溃也能捕获
        instance = this
        FileLogger.init(this)
        QuestionRepositoryProvider.init(this)
        OcrEngineProvider.init(this)
    }

    companion object {
        lateinit var instance: DusouApplication
            private set
    }
}
