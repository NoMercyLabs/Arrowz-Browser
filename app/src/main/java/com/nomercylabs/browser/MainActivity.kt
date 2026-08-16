/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.webkit.WebViewAssetLoader
import com.nomercylabs.browser.browser.PageState
import com.nomercylabs.browser.browser.WebViewHost
import androidx.compose.ui.Alignment
import com.nomercylabs.browser.chrome.NavBar
import com.nomercylabs.browser.chrome.TabList
import com.nomercylabs.browser.cursor.CursorOverlay
import com.nomercylabs.browser.cursor.CursorPosition
import com.nomercylabs.browser.cursor.CursorState
import com.nomercylabs.browser.cursor.EdgeScroller
import com.nomercylabs.browser.cursor.TouchSynthesizer
import com.nomercylabs.browser.input.BrowserState
import com.nomercylabs.browser.media.FullscreenController
import com.nomercylabs.browser.media.MediaSessionBridge
import com.nomercylabs.browser.input.Command
import com.nomercylabs.browser.input.KeyDispatcher
import com.nomercylabs.browser.input.KeyGestureTracker
import com.nomercylabs.browser.input.KeyPhase
import com.nomercylabs.browser.input.RemoteKey
import com.nomercylabs.browser.tabs.MemoryPressure
import com.nomercylabs.browser.tabs.Tab
import com.nomercylabs.browser.tabs.TabPage
import com.nomercylabs.browser.tabs.TabRegistry
import com.nomercylabs.browser.ui.TvTheme

/** Which chrome surface is over the page. Never two, and never both hidden and
 *  consuming input. */
private enum class ChromeSurface { None, NavBar, Tabs }

class MainActivity : ComponentActivity() {

    private lateinit var registry: TabRegistry

    /**
     * The page surface. Compose holds this one container for the app's life and
     * the active tab's WebView is swapped inside it.
     *
     * Handing the WebView itself to AndroidView instead means reparenting a view
     * that already has a parent every time a tab is switched, which throws; the
     * container makes attachment ours to sequence.
     */
    private lateinit var pageContainer: FrameLayout
    private val cursor = CursorState()
    private val gestures = KeyGestureTracker()
    private var chrome: ChromeSurface by mutableStateOf(ChromeSurface.None)

    /** Whether the address field has the system keyboard up. While it does, the
     *  IME owns every directional key, so BACK has to mean "close it". */
    private var editingText: Boolean by mutableStateOf(false)
    private var fullscreenActive: Boolean by mutableStateOf(false)

    /**
     * Mirrors the cursor's held state into composition so the frame loop can be
     * torn down when nothing is moving. Without it the loop requests a frame
     * every frame forever, Compose never goes idle, and the app holds the
     * device at full refresh doing nothing.
     */
    private var cursorMoving: Boolean by mutableStateOf(false)
    private lateinit var fullscreen: FullscreenController
    private lateinit var mediaSession: MediaSessionBridge

    private val host: WebViewHost? get() = registry.active?.page as? WebViewHost

    /** Stands in for the microsecond between a renderer dying and its rebuild,
     *  when no tab has a page to read. */
    private val emptyPage = PageState()

    /**
     * Every live tab's view stays in the container; which one is shown is a
     * visibility change, never a detach.
     *
     * Detaching is what a switch obviously wants and it does not work: a
     * hardware-accelerated WebView re-attached to the window comes back blank.
     * Measured on the 8000 — the view reported visible, correctly sized, with
     * the right URL, and painted black. GONE also stops the background tabs
     * drawing, which is the other half of what a detach was for.
     */
    private fun attachActivePage() {
        registry.tabs.forEach { tab ->
            val view: WebView = (tab.page as? WebViewHost)?.view ?: return@forEach
            if (view.parent == null) {
                pageContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            view.visibility = if (tab.id == registry.activeId) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fullscreen = FullscreenController(this) { active -> fullscreenActive = active }

        mediaSession = MediaSessionBridge(
            context = this,
            /**
             * Routed to the tab that published the session, never to the tab on
             * screen. The viewer presses play for what they can hear.
             */
            onAction = { tabId, action -> hostOf(tabId)?.sendMediaAction(action) },
            /**
             * Audio focus is deliberately NOT requested here.
             *
             * Chromium already takes focus for media the page plays, through its
             * own AudioFocusDelegate, and responds to loss by pausing. A second
             * request from the same app makes that delegate see a loss and pause
             * instantly: measured on the 8010, playback stopped 77ms in, every
             * time. The platform behaviour we wanted is already present, one
             * layer down.
             */
            onPlayingChanged = { playing -> playingChanged(playing) },
        )

        registry = TabRegistry(
            createPage = { tabId -> createPage(tabId) },
            isPlayingMedia = { tabId -> mediaSession.isPlaying(tabId) },
            now = { SystemClock.uptimeMillis() },
            onOpened = { tab -> (tab.page as? WebViewHost)?.load(HOME_URL) },
        )

        pageContainer = FrameLayout(this)

        registry.open()
        attachActivePage()

        setContent {
            TvTheme {
                BrowserScreen(
                    pageContainer = pageContainer,
                    scrollPage = { dx, dy -> host?.view?.scrollBy(dx, dy) },
                    activeId = registry.activeId,
                    tabs = registry.tabs,
                    cursor = cursor,
                    page = host?.state ?: emptyPage,
                    chrome = chrome,
                    editing = editingText,
                    onEditingChange = { value -> editingText = value },
                    cursorMoving = cursorMoving,
                    onNavigate = { typed -> navigate(typed) },
                    onBack = { host?.goBack() },
                    onReload = { host?.reload() },
                    onHome = { host?.load(HOME_URL) },
                    onTabs = { showChrome(ChromeSurface.Tabs) },
                    onSelectTab = { id -> selectTab(id) },
                    onCloseTab = { id -> closeTab(id) },
                    onNewTab = { newTab() },
                )
            }
        }
    }

    /**
     * Builds one tab's page and everything that has to be rebuilt with it.
     *
     * Suspension and renderer death end in the same state — no live view, a
     * saved bundle, a URL — so both come back through this one factory.
     */
    private fun createPage(tabId: String): TabPage {
        val page = WebViewHost(WebView(this))
        configureHost(page, tabId)

        page.onRebuildRequired { savedState, url ->
            val replacement = WebView(this)
            page.adopt(replacement)
            configureHost(page, tabId)

            if (replacement.restoreState(savedState) == null && url.isNotEmpty()) {
                // No usable history, so at least return to where they were.
                replacement.loadUrl(url)
            }
            attachActivePage()
            replacement
        }

        page.onRendererDeath {
            // The system just killed a process to reclaim memory. Rebuilding
            // this tab while every other tab still holds a renderer is how a
            // browser walks into a kill loop, so one death buys one eviction —
            // never a storm of them.
            registry.release(1)
        }
        return page
    }

    private fun hostOf(tabId: String): WebViewHost? =
        registry.tabs.firstOrNull { tab -> tab.id == tabId }?.page as? WebViewHost

    private fun configureHost(page: WebViewHost, tabId: String) {
        page.configure(
            userAgent = UserAgents.tenFoot(this, BuildConfig.VERSION_NAME),
            isDarkTheme = isSystemDark(),
            onEnterFullscreen = { view, callback ->
                // A held direction never receives its key-up once the video view
                // takes over, so the pointer would keep travelling behind it.
                cursor.releaseAll()
                gestures.clear()
                cursorMoving = false
                fullscreen.enter(view, callback)
            },
            onExitFullscreen = { fullscreen.exit() },
            scriptsAtDocumentStart = listOf(readAsset("mediasession-shim.js")),
            assetLoader = if (BuildConfig.DEBUG) {
                WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                    .build()
            } else {
                null
            },
        )
        // One interface per tab, so every report the page makes carries which
        // tab it came from.
        page.addBridge("NoMercyMedia", mediaSession.pageInterfaceFor(tabId))
    }

    private fun newTab() {
        pageContainer = FrameLayout(this)

        registry.open()
        host?.load(HOME_URL)
        attachActivePage()
        showChrome(ChromeSurface.None)
        relievePressure()
    }

    private fun selectTab(id: String) {
        registry.activate(id)
        attachActivePage()
        showChrome(ChromeSurface.None)
        relievePressure()
    }

    private fun closeTab(id: String) {
        // Before the tab goes, or its now-playing entry outlives the page that
        // published it and nothing is left to clear it.
        mediaSession.releaseIfOwnedBy(id)
        registry.close(id)
        attachActivePage()
    }

    /**
     * The trim callback is the signal this hardware sends, and it is not
     * sufficient on its own: the levels below UI_HIDDEN are deprecated and newer
     * Android versions stop dispatching them. [relievePressure] reads
     * MemoryInfo instead, at the moments the answer changes.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val released: List<String> = registry.applyTrim(level)
        if (BuildConfig.DEBUG) Log.v(TABS_TAG, "trim=$level released=$released")
    }

    private fun relievePressure() {
        val info = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java)?.getMemoryInfo(info) ?: return

        val count: Int = MemoryPressure.releasesFor(info.availMem, info.threshold, info.lowMemory)
        if (count <= 0) return

        val released: List<String> = registry.release(count)
        if (BuildConfig.DEBUG) Log.v(TABS_TAG, "avail=${info.availMem} released=$released")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system shows the bars again on its own after certain
        // interactions, and a video that grows letterboxing halfway through
        // looks broken.
        if (hasFocus) fullscreen.reapplyImmersiveIfActive()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity declares configChanges so playback survives, which means
        // the theme switch arrives here rather than through a recreate.
        registry.tabs.forEach { tab -> (tab.page as? WebViewHost)?.applyTheme(isSystemDark()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.destroy()
    }

    /** Kept as one place for later slices to hang background audio off. */
    private fun playingChanged(playing: Boolean) {
        if (BuildConfig.DEBUG) Log.v(INPUT_TAG, "playing=$playing")
    }

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader().use { reader -> reader.readText() }

    /**
     * Leaving the app with a direction still held would leave the pointer
     * travelling when it comes back, because no key-up ever arrives.
     */
    override fun onPause() {
        super.onPause()
        cursor.releaseAll()
        gestures.clear()
        cursorMoving = false
    }

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun browserState(): BrowserState = BrowserState(
        canGoBack = host?.state?.canGoBack ?: false,
        isPageAtTop = host?.state?.isAtTop ?: true,
        isCursorAtTopEdge = cursor.y <= EdgeScroller.EDGE_BAND_PX,
        isChromeOpen = chrome != ChromeSurface.None,
        isFullscreen = fullscreenActive,
        isEditingText = editingText,
    )

    /**
     * Opening a chrome surface must release the pointer. A direction held at the
     * moment it appears never receives its key-up through our path, so the
     * cursor would keep travelling behind it.
     */
    private fun showChrome(surface: ChromeSurface) {
        chrome = surface
        if (surface != ChromeSurface.NavBar) editingText = false

        // The page keeps Android focus while a surface is over it, so every key
        // the dispatcher hands on goes to the WebView's own focus walking rather
        // than to our controls. Measured on the 8010: opening the bar and
        // pressing toward the tabs button typed a letter into the page's search
        // box instead. Blocking descendants is not enough on its own — a view
        // that already holds focus keeps it.
        val blocking: Boolean = surface != ChromeSurface.None
        pageContainer.descendantFocusability = if (blocking) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        if (blocking) {
            host?.view?.clearFocus()
            pageContainer.clearFocus()
        }
        if (surface != ChromeSurface.None) {
            cursor.releaseAll()
            gestures.clear()
            cursorMoving = false
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
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // Declining a key means handing it on, never eating it. Returning false
        // from here does not pass the event down — it ends it — so every key the
        // dispatcher does not claim has to be dispatched explicitly. Without
        // this, the chrome's own controls are unreachable: the dispatcher
        // returns null for directional keys while a surface is open precisely so
        // Compose can move focus between them, and that focus move never
        // happened. Measured on the 8010: RIGHT from the address field never
        // reached the button beside it.
        val command: Command? = KeyDispatcher.dispatch(key, phase, browserState())

        // Debug builds only. Which of BACK's five meanings fired, and whether a
        // key was ours at all, is otherwise unobservable from outside: several
        // of them look identical on screen.
        if (BuildConfig.DEBUG) Log.v(INPUT_TAG, "key=$key phase=$phase cmd=$command ${browserState()}")

        if (command == null) return super.dispatchKeyEvent(event)
        return route(command) || super.dispatchKeyEvent(event)
    }

    private fun route(command: Command?): Boolean = when (command) {
        null -> false
        Command.GoBack -> { host?.goBack(); true }
        Command.ExitApp -> { finish(); true }

        is Command.StartMove -> {
            cursor.press(command.key, SystemClock.uptimeMillis())
            cursorMoving = cursor.isMoving
            true
        }
        is Command.StopMove -> {
            cursor.release(command.key)
            cursorMoving = cursor.isMoving
            true
        }
        Command.Activate -> {
            host?.view?.let { view -> TouchSynthesizer.tap(view, cursor.position()) }
            true
        }

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

        Command.RevealNavBar -> { showChrome(ChromeSurface.NavBar); true }
        Command.CloseChrome -> { showChrome(ChromeSurface.None); true }
        Command.StopEditing -> { editingText = false; true }

        Command.ExitFullscreen -> { fullscreen.exit(); true }
        Command.ToggleInputMode -> false
    }

    /**
     * Resolves what was typed and acts on it. A blocked scheme closes the bar
     * without navigating rather than silently doing nothing, so the refusal is
     * at least visible as the bar dismissing.
     */
    private fun navigate(typed: String) {
        when (val destination = UrlOrSearch.resolve(typed, UrlOrSearch.DUCKDUCKGO)) {
            is UrlOrSearch.Destination.Url -> host?.load(destination.url)
            is UrlOrSearch.Destination.Search ->
                host?.load(UrlOrSearch.searchUrl(destination.query, UrlOrSearch.DUCKDUCKGO))
            UrlOrSearch.Destination.Blocked, UrlOrSearch.Destination.Nothing -> Unit
        }
        showChrome(ChromeSurface.None)
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
        const val TABS_TAG: String = "NmTabs"

        val DIRECTIONS: Set<RemoteKey> =
            setOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right)
    }
}

@Composable
private fun BrowserScreen(
    pageContainer: FrameLayout,
    scrollPage: (dx: Int, dy: Int) -> Unit,
    activeId: String,
    tabs: List<Tab>,
    cursor: CursorState,
    page: PageState,
    chrome: ChromeSurface,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    cursorMoving: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
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
    }

    // Keyed on movement so the loop exists only while there is something to
    // animate. An always-running withFrameMillis loop keeps Compose permanently
    // busy and the device permanently awake.
    LaunchedEffect(cursorMoving, widthPx, heightPx) {
        if (!cursorMoving) return@LaunchedEffect

        var previousFrameMillis: Long = 0L
        while (cursorMoving) {
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
                        scrollPage(scroll.dx, scroll.dy)
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
        // The container, not the WebView. Which view is inside it is the
        // activity's business, and Compose never has to reparent anything.
        AndroidView(factory = { pageContainer }, modifier = Modifier.fillMaxSize())

        // Hidden while a chrome surface is open, so it is never ambiguous on
        // screen which of the two focus systems the D-pad is driving.
        CursorOverlay(position = position, visible = chrome == ChromeSurface.None)

        when (chrome) {
            ChromeSurface.None -> Unit

            ChromeSurface.NavBar -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavBar(
                    currentUrl = page.url,
                    canGoBack = page.canGoBack,
                    progress = page.progress,
                    tabCount = tabs.size,
                    editing = editing,
                    onEditingChange = onEditingChange,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onReload = onReload,
                    onHome = onHome,
                    onTabs = onTabs,
                )
            }

            ChromeSurface.Tabs -> TabList(
                tabs = tabs,
                activeId = activeId,
                onSelect = onSelectTab,
                onClose = onCloseTab,
                onNewTab = onNewTab,
            )
        }
    }
}
