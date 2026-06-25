package com.ck66.dusou

import android.app.Application
import com.ck66.dusou.data.repository.QuestionRepositoryProvider

class DusouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        QuestionRepositoryProvider.init(this)
    }

    companion object {
        lateinit var instance: DusouApplication
            private set
    }
}
