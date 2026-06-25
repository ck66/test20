package com.ck66.dusou

import android.app.Application

class DusouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DusouApplication
            private set
    }
}
