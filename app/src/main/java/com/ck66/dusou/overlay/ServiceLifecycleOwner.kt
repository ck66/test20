package com.ck66.dusou.overlay

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 为系统窗口中的 ComposeView 提供 LifecycleOwner 和 SavedStateRegistryOwner。
 *
 * 背景：FloatingBallService / OverlayResultWindow 中的 ComposeView 通过
 * WindowManager.addView() 直接添加到系统窗口，脱离 Activity View 树，
 * Compose 运行时找不到 ViewTreeLifecycleOwner 会抛出 IllegalStateException。
 */
class ServiceLifecycleOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun performStart(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun performStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
}
