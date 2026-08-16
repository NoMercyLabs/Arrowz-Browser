/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nomercylabs.browser.browser.WebViewHost
import com.nomercylabs.browser.browser.UserAgents
import com.nomercylabs.browser.input.BrowserState
import com.nomercylabs.browser.input.Command
import com.nomercylabs.browser.input.KeyDispatcher
import com.nomercylabs.browser.input.KeyPhase
import com.nomercylabs.browser.input.RemoteKey
import com.nomercylabs.browser.ui.TvTheme

class MainActivity : ComponentActivity() {

    private lateinit var host: WebViewHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        host = WebViewHost(webView)
        host.configure(
            userAgent = UserAgents.tenFoot(this, BuildConfig.VERSION_NAME),
            isDarkTheme = isSystemDark(),
        )
        host.load(HOME_URL)

        setContent {
            TvTheme {
                BrowserSurface(webView)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity declares configChanges so playback survives, which means
        // the theme switch arrives here rather than through a recreate.
        host.applyTheme(isSystemDark())
    }

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun browserState(): BrowserState = BrowserState(
        canGoBack = host.state.canGoBack,
        isPageAtTop = host.state.isAtTop,
    )

    /**
     * BACK is tracked rather than handled here.
     *
     * startTracking is what makes onKeyLongPress fire and what lets the ACTION_UP
     * know whether the long press already consumed the press. This is also why
     * the app does not opt into predictive back: OnBackInvokedCallback delivers
     * an invocation with no duration, so a long press becomes indistinguishable
     * from a short one and the menu becomes unreachable.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            event.startTracking()
            return true
        }
        return remoteKeyOf(keyCode)?.let { key ->
            handle(KeyDispatcher.dispatch(key, KeyPhase.Down, browserState()))
        } ?: super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        val key: RemoteKey = remoteKeyOf(keyCode) ?: return super.onKeyLongPress(keyCode, event)
        return handle(KeyDispatcher.dispatch(key, KeyPhase.LongPress, browserState()))
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val key: RemoteKey = remoteKeyOf(keyCode) ?: return super.onKeyUp(keyCode, event)

        // Canceled means the long press already ran. Acting again would fire
        // both meanings from one press.
        if (event.isCanceled) return true

        return handle(KeyDispatcher.dispatch(key, KeyPhase.Up, browserState()))
    }

    /**
     * Returns whether the press was consumed. Claiming a key the app does not
     * act on is how a television stops responding to its own remote.
     */
    private fun handle(command: Command?): Boolean {
        // Debug builds only. Which of BACK's four meanings fired is otherwise
        // unobservable from outside: several of them look identical on screen.
        if (BuildConfig.DEBUG) Log.v(INPUT_TAG, "command=$command state=${browserState()}")
        return route(command)
    }

    private fun route(command: Command?): Boolean = when (command) {
        null -> false
        Command.GoBack -> { host.goBack(); true }
        Command.ExitApp -> { finish(); true }

        /**
         * The menu lands in slice 10. Until then this must still be CONSUMED,
         * because an unhandled long press is not cancelled by the framework and
         * the following key-up fires a second command: one hold produced both
         * OpenMenu and ExitApp, so holding BACK quit the browser.
         *
         * Consuming it without doing anything would make the key dead, so it
         * falls back to what a short BACK would have done. Slice 10 replaces
         * the fallback with the menu.
         */
        Command.OpenMenu -> route(
            KeyDispatcher.dispatch(RemoteKey.Back, KeyPhase.Up, browserState()),
        )

        // Slice 3 attaches the cursor. Until then these belong to the WebView,
        // whose own focus handling is the baseline the cursor has to beat.
        is Command.Move, Command.Activate -> false

        // Reachable only from states later slices introduce, so nothing can
        // produce them yet and passing them through claims no key.
        Command.RevealNavBar, Command.CloseChrome,
        Command.ExitFullscreen, Command.ToggleInputMode -> false
    }

    private fun remoteKeyOf(keyCode: Int): RemoteKey? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> RemoteKey.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteKey.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteKey.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteKey.Right
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> RemoteKey.Center
        KeyEvent.KEYCODE_BACK -> RemoteKey.Back
        else -> null
    }

    private companion object {
        const val HOME_URL: String = "https://duckduckgo.com/"
        const val INPUT_TAG: String = "NmInput"
    }
}

@Composable
private fun BrowserSurface(webView: WebView) {
    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
    )
}
