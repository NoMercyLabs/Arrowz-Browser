/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.media

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Owns the view WebView hands over when a page goes fullscreen.
 *
 * WebView's contract is small: here is a view containing the video, and here is
 * a callback for when the user leaves. Displaying it, hiding the system bars,
 * keeping the screen awake and getting back out again are all ours.
 */
class FullscreenController(
    private val activity: Activity,
    private val onChanged: (Boolean) -> Unit,
) {

    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

    val isActive: Boolean get() = customView != null

    fun enter(view: View, viewCallback: WebChromeClient.CustomViewCallback) {
        // A second request while already fullscreen would strand the first view
        // in the hierarchy with nothing able to remove it.
        if (customView != null) {
            viewCallback.onCustomViewHidden()
            return
        }

        customView = view
        callback = viewCallback

        container().addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Without this the television dims and sleeps partway through a film,
        // which reads as the app having crashed.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive(true)
        onChanged(true)
    }

    fun exit() {
        val view: View = customView ?: return

        container().removeView(view)
        customView = null

        // Telling the page is not optional. Without it the page still believes
        // it is fullscreen, its own controls stay in the wrong state, and the
        // next fullscreen request is ignored.
        callback?.onCustomViewHidden()
        callback = null

        // Cleared rather than left set. A browser that stops a television ever
        // sleeping is worse than one that dims during a video.
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive(false)
        onChanged(false)
    }

    /**
     * Re-applied rather than set once, because the system shows the bars again
     * on its own after certain interactions and a video that grows letterboxing
     * halfway through looks broken.
     */
    fun reapplyImmersiveIfActive() {
        if (isActive) applyImmersive(true)
    }

    private fun applyImmersive(immersive: Boolean) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, !immersive)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun container(): ViewGroup =
        activity.window.decorView as ViewGroup
}
