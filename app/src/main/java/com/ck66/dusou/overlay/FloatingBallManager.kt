package com.ck66.dusou.overlay

import android.content.Context
import android.content.Intent
import com.ck66.dusou.capture.ScreenCaptureService

object FloatingBallManager {

    private var showing = false

    fun show(context: Context) {
        if (showing) return
        FloatingBallService.start(context.applicationContext)
        showing = true
    }

    fun hide(context: Context) {
        if (!showing) return
        FloatingBallService.stop(context.applicationContext)
        ScreenCaptureManager.instance.stopCapture()
        context.applicationContext.stopService(Intent(context.applicationContext, ScreenCaptureService::class.java))
        showing = false
    }

    fun isShowing(): Boolean = showing
}
