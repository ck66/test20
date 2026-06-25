package com.ck66.dusou.overlay

import android.content.Context

object FloatingBallManager {

    private var showing = false

    var onBallClicked: (() -> Unit)? = null

    fun show(context: Context) {
        if (showing) return
        FloatingBallService.clickCallback = onBallClicked
        FloatingBallService.start(context.applicationContext)
        showing = true
    }

    fun hide(context: Context) {
        if (!showing) return
        FloatingBallService.stop(context.applicationContext)
        FloatingBallService.clickCallback = null
        showing = false
    }

    fun isShowing(): Boolean = showing
}
