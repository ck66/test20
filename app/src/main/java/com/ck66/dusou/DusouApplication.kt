package com.ck66.dusou

import android.app.Application
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.ocr.OcrEngineProvider

class DusouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install()  // 必须放在第一行，确保后续初始化崩溃也能捕获
        instance = this
        QuestionRepositoryProvider.init(this)
        OcrEngineProvider.init(this)
    }

    companion object {
        lateinit var instance: DusouApplication
            private set
    }
}
