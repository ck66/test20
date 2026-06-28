package com.ck66.dusou.overlay

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.ck66.dusou.capture.ScreenCaptureService

object FloatingBallManager {

    fun show(context: Context) {
        if (isServiceRunning(context)) return
        FloatingBallService.start(context.applicationContext)
    }

    fun hide(context: Context) {
        if (!isServiceRunning(context)) return
        FloatingBallService.stop(context.applicationContext)
        ScreenCaptureManager.instance.stopCapture()
        context.applicationContext.stopService(Intent(context.applicationContext, ScreenCaptureService::class.java))
    }

    fun isShowing(context: Context): Boolean = isServiceRunning(context)

    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            ?.any { it.service.className == FloatingBallService::class.java.name } == true
    }
}
