/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.withFrameMillis
import com.nomercylabs.browser.browser.UrlOrSearch
import com.nomercylabs.browser.browser.UserAgents
import com.nomercylabs.browser.browser.PageState
import com.nomercylabs.browser.browser.WebViewHost
import androidx.compose.ui.Alignment
import com.nomercylabs.browser.chrome.NavBar
import com.nomercylabs.browser.cursor.CursorOverlay
import com.nomercylabs.browser.cursor.CursorPosition
import com.nomercylabs.browser.cursor.CursorState
import com.nomercylabs.browser.cursor.EdgeScroller
import com.nomercylabs.browser.cursor.TouchSynthesizer
import com.nomercylabs.browser.input.BrowserState
import com.nomercylabs.browser.input.Command
import com.nomercylabs.browser.input.KeyDispatcher
import com.nomercylabs.browser.input.KeyGestureTracker
import com.nomercylabs.browser.input.KeyPhase
import com.nomercylabs.browser.input.RemoteKey
import com.nomercylabs.browser.ui.TvTheme

class MainActivity : ComponentActivity() {

    private lateinit var host: WebViewHost
    private lateinit var webView: WebView
    private val cursor = CursorState()
    private val gestures = KeyGestureTracker()
    private var chromeOpen: Boolean by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        host = WebViewHost(webView)
        host.configure(
            userAgent = UserAgents.tenFoot(this, BuildConfig.VERSION_NAME),
            isDarkTheme = isSystemDark(),
        )
        host.load(HOME_URL)

        setContent {
            TvTheme {
                BrowserScreen(
                    webView = webView,
                    cursor = cursor,
                    page = host.state,
                    chromeOpen = chromeOpen,
                    onNavigate = { typed -> navigate(typed) },
                    onBack = { host.goBack() },
                    onReload = { host.reload() },
                    onHome = { host.load(HOME_URL) },
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity declares configChanges so playback survives, which means
        // the theme switch arrives here rather than through a recreate.
        host.applyTheme(isSystemDark())
    }

    /**
     * Leaving the app with a direction still held would leave the pointer
     * travelling when it comes back, because no key-up ever arrives.
     */
    override fun onPause() {
        super.onPause()
        cursor.releaseAll()
        gestures.clear()
    }

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun browserState(): BrowserState = BrowserState(
        canGoBack = host.state.canGoBack,
        isPageAtTop = host.state.isAtTop,
        isCursorAtTopEdge = cursor.y <= EdgeScroller.EDGE_BAND_PX,
        isChromeOpen = chromeOpen,
    )

    /**
     * Opening the chrome must release the pointer. A direction held at the
     * moment the bar appears never receives its key-up through our path, so the
     * cursor would keep travelling behind the chrome.
     */
    private fun showChrome(open: Boolean) {
        chromeOpen = open
        if (open) {
            cursor.releaseAll()
            gestures.clear()
        }
    }

    /**
     * All key input is taken here rather than in onKeyDown.
     *
     * A focused WebView consumes every directional key for its own focus
     * walking, so the activity callbacks never run and the browser looks like
     * it is ignoring the remote. dispatchKeyEvent sits above the view
     * hierarchy, which is the only place a browser-wide input layer can work.
     *
     * The cost is that this path offers no long-press callback and no tracking
     * flags, so KeyGestureTracker computes both.
     */
    // androidx.core marks its own ComponentActivity.dispatchKeyEvent as
    // RestrictTo, so overriding it correctly still trips lint. Overriding and
    // delegating to super is the supported way to intercept keys above the view
    // hierarchy, and there is no alternative entry point that sees them first.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key: RemoteKey = remoteKeyOf(event.keyCode) ?: return super.dispatchKeyEvent(event)

        val phase: KeyPhase? = when (event.action) {
            KeyEvent.ACTION_DOWN -> gestures.onDown(key, event.eventTime, event.repeatCount)
            KeyEvent.ACTION_UP -> gestures.onUp(key)
            else -> null
        }

        // A direction's release must always stop the pointer, even when the
        // press produced something else, or the cursor keeps travelling.
        if (phase == null) {
            if (event.action == KeyEvent.ACTION_UP && key in DIRECTIONS) {
                route(Command.StopMove(key))
            }
            return true
        }

        return handle(KeyDispatcher.dispatch(key, phase, browserState()))
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

        is Command.StartMove -> { cursor.press(command.key, SystemClock.uptimeMillis()); true }
        is Command.StopMove -> { cursor.release(command.key); true }
        Command.Activate -> { TouchSynthesizer.tap(webView, cursor.position()); true }

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

        // The nav bar arrives in slice 8. Consumed rather than passed through,
        // so UP at the top of a page does not also drive the pointer upward
        // into an edge scroll it was never meant to trigger.
        Command.RevealNavBar -> { showChrome(true); true }
        Command.CloseChrome -> { showChrome(false); true }

        // Reachable only from states later slices introduce.
        Command.ExitFullscreen, Command.ToggleInputMode -> false
    }

    /**
     * Resolves what was typed and acts on it. A blocked scheme closes the bar
     * without navigating rather than silently doing nothing, so the refusal is
     * at least visible as the bar dismissing.
     */
    private fun navigate(typed: String) {
        when (val destination = UrlOrSearch.resolve(typed, UrlOrSearch.DUCKDUCKGO)) {
            is UrlOrSearch.Destination.Url -> {
                host.load(destination.url)
                showChrome(false)
            }
            is UrlOrSearch.Destination.Search -> {
                host.load(UrlOrSearch.searchUrl(destination.query, UrlOrSearch.DUCKDUCKGO))
                showChrome(false)
            }
            UrlOrSearch.Destination.Blocked, UrlOrSearch.Destination.Nothing -> showChrome(false)
        }
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
        val DIRECTIONS: Set<RemoteKey> =
            setOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right)
    }
}

@Composable
private fun BrowserScreen(
    webView: WebView,
    cursor: CursorState,
    page: PageState,
    chromeOpen: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val widthPx: Int = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val heightPx: Int = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    var position: CursorPosition by remember { mutableStateOf(CursorPosition(0f, 0f)) }
    var edgeHeldSinceMillis: Long by remember { mutableStateOf(0L) }

    LaunchedEffect(widthPx, heightPx) {
        cursor.centreIn(widthPx, heightPx)
        position = cursor.position()

        var previousFrameMillis: Long = 0L
        while (true) {
            withFrameMillis { frameMillis ->
                val frameDelta: Long =
                    if (previousFrameMillis == 0L) 0L else frameMillis - previousFrameMillis
                previousFrameMillis = frameMillis

                if (cursor.isMoving) {
                    cursor.advance(frameMillis, widthPx, heightPx)
                    position = cursor.position()

                    val scroll = EdgeScroller.scrollFor(
                        position = position,
                        width = widthPx,
                        height = heightPx,
                        heldMillis = if (edgeHeldSinceMillis == 0L) 0L else frameMillis - edgeHeldSinceMillis,
                        frameMillis = frameDelta,
                    )
                    if (scroll.dx != 0 || scroll.dy != 0) {
                        if (edgeHeldSinceMillis == 0L) edgeHeldSinceMillis = frameMillis
                        webView.scrollBy(scroll.dx, scroll.dy)
                    } else {
                        edgeHeldSinceMillis = 0L
                    }
                } else {
                    edgeHeldSinceMillis = 0L
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        // Hidden while chrome is open, so it is never ambiguous on screen which
        // of the two focus systems the D-pad is driving.
        CursorOverlay(position = position, visible = !chromeOpen)

        if (chromeOpen) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                NavBar(
                    currentUrl = page.url,
                    canGoBack = page.canGoBack,
                    progress = page.progress,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onReload = onReload,
                    onHome = onHome,
                )
            }
        }
    }
}
