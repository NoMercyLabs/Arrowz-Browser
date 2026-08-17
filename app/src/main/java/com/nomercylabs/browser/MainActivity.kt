/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import android.speech.RecognizerIntent
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.ui.res.stringResource
import com.nomercylabs.browser.chrome.FindBar
import com.nomercylabs.browser.chrome.LibraryRow
import com.nomercylabs.browser.chrome.LibraryScreen
import com.nomercylabs.browser.chrome.PermissionAsk
import com.nomercylabs.browser.chrome.PermissionPrompt
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import com.nomercylabs.browser.chrome.HomeGrid
import com.nomercylabs.browser.chrome.MenuOverlay
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
import com.nomercylabs.browser.input.InputMode
import com.nomercylabs.browser.input.RemoteKey
import com.nomercylabs.browser.spatial.NavigabilityProbe
import com.nomercylabs.browser.spatial.SpatialNavBridge
import com.nomercylabs.browser.ui.Tokens
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nomercylabs.browser.data.Bookmark
import com.nomercylabs.browser.data.BrowserStore
import com.nomercylabs.browser.data.HomeContent
import com.nomercylabs.browser.data.SqliteBrowserStore
import com.nomercylabs.browser.data.Suggestion
import com.nomercylabs.browser.data.Suggestions
import com.nomercylabs.browser.data.Tile
import com.nomercylabs.browser.data.Visit
import com.nomercylabs.browser.tabs.MemoryPressure
import com.nomercylabs.browser.tabs.Tab
import com.nomercylabs.browser.tabs.TabPage
import com.nomercylabs.browser.tabs.TabRegistry
import androidx.compose.ui.graphics.toArgb
import com.nomercylabs.browser.ui.LocalPalette
import com.nomercylabs.browser.ui.Palette
import com.nomercylabs.browser.ui.Palettes
import com.nomercylabs.browser.ui.ThemeMode
import com.nomercylabs.browser.ui.TvTheme
import androidx.compose.ui.focus.FocusRequester

/** Which chrome surface is over the page. Never two, and never both hidden and
 *  consuming input. */
private enum class ChromeSurface { None, NavBar, Tabs, Menu, Find, Bookmarks, History, Permission }

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

    /** Set while the keyboard holds window focus, so regaining it is read as the
     *  keyboard closing rather than as the app simply coming forward. */
    private var keyboardTookFocus: Boolean = false

    private var permissionAsk: PermissionAsk? by mutableStateOf(null)
    private var historyRows: List<LibraryRow> by mutableStateOf(emptyList())

    /** Origins the viewer asked to see the desktop build of, restored on the way
     *  in so the choice outlives the tab it was made in. */
    private var desktopOrigins: Set<String> by mutableStateOf(emptySet())

    private var pendingFileChooser: android.webkit.ValueCallback<Array<Uri>>? = null

    /** Cursor or focus. Never both: exactly one system may consume a key. */
    private var inputMode: InputMode by mutableStateOf(InputMode.Cursor)

    private val spatial = SpatialNavBridge { host?.view }

    private val voiceInput = registerForActivityResult(StartActivityForResult()) { result ->
        val spoken: String? = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) navigate(spoken)
    }

    /**
     * Answered even when nothing was chosen. A file input whose callback never
     * fires stays dead until the page reloads, which reads as the site being
     * broken rather than as a cancelled chooser.
     */
    private val fileChooser = registerForActivityResult(OpenDocument()) { uri ->
        pendingFileChooser?.onReceiveValue(if (uri == null) null else arrayOf(uri))
        pendingFileChooser = null
    }
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
    private lateinit var store: BrowserStore

    /**
     * What the home screen draws, kept in composition and refreshed after every
     * write. Reading the store on each frame would put SQLite on the main
     * thread, which a television's storage is slow enough to be felt as dropped
     * frames.
     */
    private var homeTiles: List<Tile> by mutableStateOf(emptyList())

    /** The same snapshot the tiles were built from. The address bar ranks
     *  against the raw rows, which the tiles have already trimmed and merged. */
    private var knownBookmarks: List<Bookmark> by mutableStateOf(emptyList())
    private var knownVisits: List<Visit> by mutableStateOf(emptyList())

    /** Read from the store on the way in and written back on every change, so
     *  the choice survives a restart the way a browser's does. */
    private var themeMode: ThemeMode by mutableStateOf(ThemeMode.System)

    /** Writes and reads happen here; the results are posted back. */
    private val storeThread = java.util.concurrent.Executors.newSingleThreadExecutor()

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

        store = SqliteBrowserStore(applicationContext)
        refreshHome()
        loadThemeMode()
        loadDesktopOrigins()

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
            // Nothing is loaded: a new tab shows the home screen, and the page
            // underneath stays blank until the viewer chooses something.
            onOpened = { },
        )

        pageContainer = FrameLayout(this)

        registry.open()
        attachActivePage()

        setContent {
            TvTheme(mode = themeMode) {
                BrowserScreen(
                    pageContainer = pageContainer,
                    scrollPage = { dx, dy -> host?.view?.scrollBy(dx, dy) },
                    activeId = registry.activeId,
                    tabs = registry.tabs,
                    cursor = cursor,
                    page = host?.state ?: emptyPage,
                    chrome = chrome,
                    focusMode = inputMode == InputMode.Focus,
                    editing = editingText,
                    onEditingChange = { value -> editingText = value },
                    cursorMoving = cursorMoving,
                    onNavigate = { typed -> navigate(typed) },
                    onBack = { host?.goBack() },
                    onReload = { host?.reload() },
                    onHome = { registry.active?.isHome = true; showChrome(ChromeSurface.None) },
                    onTabs = { showChrome(ChromeSurface.Tabs) },
                    onToggleFavourite = { toggleFavourite() },
                    isFavourite = isCurrentPageFavourite(),
                    showHome = registry.active?.isHome ?: true,
                    homeTiles = homeTiles,
                    onOpenTile = { url -> openFromHome(url) },
                    suggestionsFor = { query ->
                        Suggestions.forQuery(query, knownBookmarks, knownVisits)
                    },
                    onPickSuggestion = { suggestion -> navigate(suggestion.url) },
                    onVoice = { startVoiceInput() },
                    onMenu = { showChrome(ChromeSurface.Menu) },
                    isDesktopSite = HomeContent.originOf(host?.state?.url ?: "") in desktopOrigins,
                    onToggleDesktopSite = { toggleDesktopSite() },
                    onBookmarks = { showChrome(ChromeSurface.Bookmarks) },
                    onHistory = { refreshHistory(); showChrome(ChromeSurface.History) },
                    onFind = { showChrome(ChromeSurface.Find) },
                    onFindQuery = { query -> host?.find(query) },
                    onFindStep = { forward -> host?.findNext(forward) },
                    bookmarkRows = homeTiles
                        .filter { tile -> tile.isFavourite }
                        .map { tile -> LibraryRow(tile.title, tile.origin, tile.url) },
                    historyRows = historyRows,
                    onRemoveBookmark = { row -> removeBookmark(row.subtitle) },
                    permissionAsk = permissionAsk,
                    onAnswerPermission = { allow, remember -> answerPermission(allow, remember) },
                    onCloseSurface = { showChrome(ChromeSurface.None) },
                    themeMode = themeMode,
                    onCycleTheme = { cycleThemeMode() },
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

        page.onNavigated { url ->
            chooseInputMode(url)
            val title: String = page.pageTitle
            storeThread.execute {
                store.recordVisit(url, title)
                publishLibrary()
            }
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
            userAgent = userAgentFor(page.pageUrl),
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
            scriptsAtDocumentStart = listOf(
                readAsset("mediasession-shim.js"),
                readAsset("spatialnav.js"),
            ),
            assetLoader = if (BuildConfig.DEBUG) {
                WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
                    .build()
            } else {
                null
            },
            onPermissionAsked = { origin, kinds, grant, deny ->
                answerOrAsk(origin, kinds, grant, deny)
            },
            onFileChooser = { callback, params -> openFileChooser(callback, params) },
            onDownload = { url, agent, disposition, mimeType ->
                startDownload(url, agent, disposition, mimeType)
            },
        )
        // One interface per tab, so every report the page makes carries which
        // tab it came from.
        page.addBridge("NoMercyMedia", mediaSession.pageInterfaceFor(tabId))
    }

    private fun loadThemeMode() = storeThread.execute {
        val stored: String = store.preference(THEME_KEY) ?: ThemeMode.System.name
        val mode: ThemeMode = runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.System)
        runOnUiThread { themeMode = mode }
    }

    /**
     * Cycles rather than opening a submenu: three values, and a second level on
     * a television costs two presses to reach and two to leave.
     */
    private fun cycleThemeMode() {
        val next: ThemeMode = when (themeMode) {
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
            ThemeMode.Dark -> ThemeMode.System
        }
        themeMode = next
        storeThread.execute { store.setPreference(THEME_KEY, next.name) }

        // The page reads the app's theme through algorithmic darkening, so the
        // choice has to reach every live tab and not only the chrome.
        registry.tabs.forEach { tab ->
            (tab.page as? WebViewHost)?.applyTheme(isDarkFor(next))
        }
    }

    private fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.System -> isSystemDark()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    private fun refreshHome() = storeThread.execute { publishLibrary() }

    private fun refreshHistory() = storeThread.execute {
        val rows: List<LibraryRow> = store.history().map { entry ->
            LibraryRow(entry.title.ifBlank { entry.origin }, entry.url, entry.url)
        }
        runOnUiThread { historyRows = rows }
    }

    private fun removeBookmark(origin: String) = storeThread.execute {
        store.removeBookmark(origin)
        publishLibrary()
    }

    private fun loadDesktopOrigins() = storeThread.execute {
        val stored: Set<String> = (store.preference(DESKTOP_ORIGINS_KEY) ?: "")
            .split(',')
            .filter { origin -> origin.isNotBlank() }
            .toSet()
        runOnUiThread { desktopOrigins = stored }
    }

    /**
     * One read of the store feeding both the home grid and the address bar.
     *
     * Called on the store thread only: SQLite on the main thread is felt as
     * dropped frames on a television's storage.
     */
    private fun publishLibrary() {
        val bookmarks: List<Bookmark> = store.bookmarks()
        val visits: List<Visit> = store.visits()
        val tiles: List<Tile> = HomeContent.tiles(bookmarks, visits)
        runOnUiThread {
            knownBookmarks = bookmarks
            knownVisits = visits
            homeTiles = tiles
        }
    }

    /** Toggling reads the tiles rather than the store, because the tiles are
     *  what the viewer is looking at when they press it. */
    private fun toggleFavourite() {
        val page: PageState = host?.state ?: return
        if (page.url.isEmpty()) return

        val origin: String = HomeContent.originOf(page.url)
        val wasFavourite: Boolean = homeTiles.any { tile -> tile.isFavourite && tile.origin == origin }
        val url: String = page.url
        val title: String = page.title

        storeThread.execute {
            if (wasFavourite) store.removeBookmark(origin) else store.addBookmark(url, title)
            publishLibrary()
        }
    }

    private fun openFromHome(url: String) {
        registry.active?.isHome = false
        host?.load(url)
        showChrome(ChromeSurface.None)
    }

    private fun newTab() {
        registry.open()
        registry.active?.isHome = true
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

        // How editing ends. The leanback IME is its own window: it takes focus,
        // swallows the BACK that dismisses it, and reports no IME inset at all
        // on this platform, so neither our key path nor `WindowInsets.isImeVisible`
        // ever learns that it went away. Measured on the 8010: the keyboard was
        // down, the field still held the caret, and DOWN moved it through the
        // text instead of into the suggestions. Regaining window focus is the
        // one signal that does arrive.
        if (!hasFocus) {
            keyboardTookFocus = editingText
            return
        }
        if (editingText && keyboardTookFocus) {
            editingText = false
            keyboardTookFocus = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The activity declares configChanges so playback survives, which means
        // the theme switch arrives here rather than through a recreate.
        registry.tabs.forEach { tab -> (tab.page as? WebViewHost)?.applyTheme(isSystemDark()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        storeThread.shutdown()
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
        // The home screen is Compose focus territory, exactly like the bar and
        // the tab list: there is no page under the pointer to click.
        isChromeOpen = chrome != ChromeSurface.None || (registry.active?.isHome ?: true),
        isFullscreen = fullscreenActive,
        // The window is asked rather than the composition flag: the leanback IME
        // hides itself on BACK and does not always consume the key, so the flag
        // can already be false while the same press is still travelling. Read as
        // "not editing", that press falls through to the exit rung of the
        // ladder, and dropping the keyboard closes the browser.
        mode = inputMode,
        isEditingText = editingText || isKeyboardShowing(),
    )

    /**
     * Speaking instead of typing.
     *
     * Not every television has a speech service — the recogniser is part of the
     * Google app, which a stripped Android TV build may not carry — so the
     * absence is reported rather than crashing on a missing activity.
     */
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.nav_voice))
        }
        runCatching { voiceInput.launch(intent) }
            .onFailure { showMessage(getString(R.string.voice_unavailable)) }
    }

    /**
     * Answers from the store when the site has been asked before, and only
     * interrupts when it has not. A prompt on every page load is how a browser
     * teaches people to press allow without reading.
     */
    private fun answerOrAsk(
        origin: String,
        kinds: List<String>,
        grant: () -> Unit,
        deny: () -> Unit,
    ) {
        val host: String = HomeContent.originOf(origin)
        storeThread.execute {
            val decisions: List<String?> = kinds.map { kind -> store.sitePermission(host, kind) }
            runOnUiThread {
                when {
                    decisions.any { decision -> decision == DECISION_BLOCK } -> deny()
                    decisions.all { decision -> decision == DECISION_ALLOW } -> grant()
                    else -> {
                        permissionAsk = PermissionAsk(host, kinds, grant, deny)
                        showChrome(ChromeSurface.Permission)
                    }
                }
            }
        }
    }

    private fun answerPermission(allow: Boolean, remember: Boolean) {
        val ask: PermissionAsk = permissionAsk ?: return
        permissionAsk = null
        showChrome(ChromeSurface.None)

        if (allow) ask.grant() else ask.deny()
        if (!remember) return

        val decision: String = if (allow) DECISION_ALLOW else DECISION_BLOCK
        storeThread.execute {
            ask.kinds.forEach { kind -> store.setSitePermission(ask.origin, kind, decision) }
        }
    }

    /**
     * Handed to the system downloader rather than read through the WebView.
     * DownloadManager survives the app being killed, which a television will do
     * to anything in the background, and it already speaks HTTP redirects and
     * resumption.
     */
    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        val name: String = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, name)
        }
        runCatching { getSystemService(DownloadManager::class.java).enqueue(request) }
            .onSuccess { showMessage(getString(R.string.download_started, name)) }
    }

    private fun openFileChooser(
        callback: android.webkit.ValueCallback<Array<Uri>>,
        params: android.webkit.WebChromeClient.FileChooserParams,
    ): Boolean {
        // A pending callback that never fires leaves the page's file input dead
        // until reload, so an older one is always answered before it is dropped.
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = callback

        val types: Array<String> = params.acceptTypes
            .filter { type -> type.isNotBlank() }
            .toTypedArray()
        return runCatching {
            fileChooser.launch(if (types.isEmpty()) arrayOf("*/*") else types)
        }.onFailure {
            pendingFileChooser = null
            callback.onReceiveValue(null)
        }.isSuccess
    }

    /**
     * Per site, because the reason to switch is a specific site's layout rather
     * than a preference about the web.
     */
    private fun userAgentFor(url: String): String {
        val origin: String = HomeContent.originOf(url)
        return if (origin.isNotEmpty() && origin in desktopOrigins) {
            UserAgents.mobile(this, BuildConfig.VERSION_NAME)
        } else {
            UserAgents.tenFoot(this, BuildConfig.VERSION_NAME)
        }
    }

    private fun toggleDesktopSite() {
        val url: String = host?.state?.url ?: return
        val origin: String = HomeContent.originOf(url)
        if (origin.isEmpty()) return

        desktopOrigins = if (origin in desktopOrigins) desktopOrigins - origin else desktopOrigins + origin
        showChrome(ChromeSurface.None)
        host?.setUserAgent(userAgentFor(url))

        val stored: String = desktopOrigins.joinToString(",")
        storeThread.execute { store.setPreference(DESKTOP_ORIGINS_KEY, stored) }
    }

    private fun showMessage(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private fun isKeyboardShowing(): Boolean =
        ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) ?: false

    /**
     * Opening a chrome surface must release the pointer. A direction held at the
     * moment it appears never receives its key-up through our path, so the
     * cursor would keep travelling behind it.
     */
    private fun showChrome(surface: ChromeSurface) {
        // Leaving find without clearing takes the highlights with it; leaving a
        // prompt unanswered leaves the page waiting forever, so walking away
        // from the question is a refusal rather than silence.
        if (chrome == ChromeSurface.Find && surface != ChromeSurface.Find) host?.clearFind()
        if (chrome == ChromeSurface.Permission && surface != ChromeSurface.Permission) {
            permissionAsk?.deny()
            permissionAsk = null
        }

        chrome = surface
        if (surface != ChromeSurface.NavBar && surface != ChromeSurface.Find) editingText = false

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

        // Blocking descendants is not enough. A WebView whose page has a focused
        // text field keeps the input connection, so the keyboard our own field
        // raises is still typing into the page: measured on the 8000, an address
        // typed into the bar arrived in DuckDuckGo's search box. Taking its
        // focusability away and dismissing the page's keyboard is what actually
        // hands the editor over.
        host?.view?.let { view ->
            view.isFocusable = !blocking
            view.isFocusableInTouchMode = !blocking
            if (blocking) {
                view.clearFocus()
                getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        if (blocking) pageContainer.clearFocus()
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
            KeyEvent.ACTION_DOWN ->
                gestures.onDown(key, event.eventTime, event.repeatCount, event.isLongPress)

            KeyEvent.ACTION_UP -> when (val release = gestures.onUp(key)) {
                is KeyGestureTracker.Release.Acted -> release.phase

                // Eaten deliberately: the long press already acted, and passing
                // this on let the system read a plain BACK and close the app.
                KeyGestureTracker.Release.Swallowed -> {
                    if (key in DIRECTIONS) route(Command.StopMove(key))
                    return true
                }

                KeyGestureTracker.Release.Unknown -> {
                    // A direction's release must always stop the pointer, even
                    // when no press was seen, or the cursor keeps travelling.
                    if (key in DIRECTIONS) {
                        route(Command.StopMove(key))
                        return true
                    }

                    // BACK is never handed to the system, whatever state the
                    // tracker is in. A press whose key-down was lost — which
                    // happens across the window transition a surface opening
                    // causes — arrives here, and passing it on made the system
                    // close the menu the long press had just opened.
                    if (key == RemoteKey.Back) return true
                    return super.dispatchKeyEvent(event)
                }
            }

            else -> null
        }

        if (phase == null) return super.dispatchKeyEvent(event)

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

        // BACK is never handed on. Its press phase deliberately produces no
        // command — the meaning is decided on release — and forwarding that
        // press let the system's own back dispatcher close the activity, so a
        // hold opened the menu and the browser vanished behind it.
        if (command == null) return key == RemoteKey.Back || super.dispatchKeyEvent(event)
        return route(command) || key == RemoteKey.Back || super.dispatchKeyEvent(event)
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
        is Command.MoveFocus -> {
            spatial.move(
                direction = command.key,
                onScroll = { dx, dy -> host?.view?.scrollBy(dx, dy) },
                // No dead ends: nothing above and nothing left to scroll means
                // the chrome takes the press rather than it doing nothing.
                onLeavePage = {
                    if (command.key == RemoteKey.Up) showChrome(ChromeSurface.NavBar)
                },
            )
            true
        }

        Command.Activate -> {
            if (inputMode == InputMode.Focus) {
                spatial.activate()
            } else {
                host?.view?.let { view -> TouchSynthesizer.tap(view, cursor.position()) }
            }
            true
        }

        // Consumed here, and it must stay consumed: an unhandled long press is
        // not cancelled by the framework, so the following key-up fired a
        // second command and one hold produced both OpenMenu and ExitApp.
        Command.OpenMenu -> { showChrome(ChromeSurface.Menu); true }

        Command.RevealNavBar -> { showChrome(ChromeSurface.NavBar); true }
        Command.CloseChrome -> { showChrome(ChromeSurface.None); true }
        Command.StopEditing -> { editingText = false; hideKeyboard(); true }

        Command.ExitFullscreen -> { fullscreen.exit(); true }
        // Remembered per site: the override is a judgement about this page, and
        // making it again on every visit is the chore automatic mode selection
        // exists to remove.
        Command.ToggleInputMode -> {
            setInputMode(
                if (inputMode == InputMode.Focus) InputMode.Cursor else InputMode.Focus,
                remember = true,
            )
            true
        }
    }

    /**
     * The ring web content draws is the chrome's ring, taken from the same
     * token. Two definitions drift apart within two releases, and then native
     * and page focus stop looking like one interface.
     */
    private fun focusRingCss(): String {
        val palette: Palette = if (resolveDark()) Palettes.Dark else Palettes.Light
        val color: Int = palette.focusRing.toArgb()
        return "rgb(${(color shr 16) and 0xFF},${(color shr 8) and 0xFF},${color and 0xFF})"
    }

    private fun resolveDark(): Boolean = when (themeMode) {
        ThemeMode.System -> isSystemDark()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    private fun setInputMode(mode: InputMode, remember: Boolean) {
        inputMode = mode
        cursor.releaseAll()
        cursorMoving = false

        if (mode == InputMode.Focus) {
            spatial.applyRingStyle(
                colorCss = focusRingCss(),
                widthPx = Tokens.Focus.RingWidth.value.toInt(),
                radiusPx = Tokens.Radius.value.toInt(),
            )
            spatial.focusFirst(HomeContent.originOf(host?.state?.url ?: ""))
        } else {
            spatial.clear()
        }

        if (!remember) return
        val origin: String = HomeContent.originOf(host?.state?.url ?: "")
        if (origin.isEmpty()) return
        storeThread.execute { store.setPreference("$MODE_KEY_PREFIX$origin", mode.name) }
    }

    /**
     * What a freshly loaded page starts in: the site's remembered override if
     * there is one, and otherwise whatever the page's own shape argues for.
     */
    private fun chooseInputMode(url: String) {
        val origin: String = HomeContent.originOf(url)
        storeThread.execute {
            val remembered: String? =
                if (origin.isEmpty()) null else store.preference("$MODE_KEY_PREFIX$origin")
            runOnUiThread {
                if (remembered != null) {
                    setInputMode(
                        runCatching { InputMode.valueOf(remembered) }.getOrDefault(InputMode.Cursor),
                        remember = false,
                    )
                    return@runOnUiThread
                }
                spatial.probe { page ->
                    val wanted: InputMode =
                        if (page != null && NavigabilityProbe.prefersFocusMode(page)) {
                            InputMode.Focus
                        } else {
                            InputMode.Cursor
                        }
                    setInputMode(wanted, remember = false)
                }
            }
        }
    }

    /**
     * Resolves what was typed and acts on it. A blocked scheme closes the bar
     * without navigating rather than silently doing nothing, so the refusal is
     * at least visible as the bar dismissing.
     */
    private fun isCurrentPageFavourite(): Boolean {
        val url: String = host?.state?.url ?: return false
        if (url.isEmpty()) return false
        val origin: String = HomeContent.originOf(url)
        return homeTiles.any { tile -> tile.isFavourite && tile.origin == origin }
    }

    private fun navigate(typed: String) {
        registry.active?.isHome = false
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
        const val THEME_KEY: String = "theme.mode"
        const val DESKTOP_ORIGINS_KEY: String = "ua.desktop.origins"
        const val DECISION_ALLOW: String = "allow"
        const val DECISION_BLOCK: String = "block"
        const val MODE_KEY_PREFIX: String = "mode."

        val DIRECTIONS: Set<RemoteKey> =
            setOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right)
    }
}

@Composable
private fun BrowserScreen(
    pageContainer: FrameLayout,
    showHome: Boolean,
    homeTiles: List<Tile>,
    onOpenTile: (String) -> Unit,
    suggestionsFor: (String) -> List<Suggestion>,
    onPickSuggestion: (Suggestion) -> Unit,
    onVoice: () -> Unit,
    onMenu: () -> Unit,
    isDesktopSite: Boolean,
    onToggleDesktopSite: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onFind: () -> Unit,
    onFindQuery: (String) -> Unit,
    onFindStep: (Boolean) -> Unit,
    bookmarkRows: List<LibraryRow>,
    historyRows: List<LibraryRow>,
    onRemoveBookmark: (LibraryRow) -> Unit,
    permissionAsk: PermissionAsk?,
    onAnswerPermission: (Boolean, Boolean) -> Unit,
    onCloseSurface: () -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    scrollPage: (dx: Int, dy: Int) -> Unit,
    activeId: String,
    tabs: List<Tab>,
    cursor: CursorState,
    page: PageState,
    chrome: ChromeSurface,
    focusMode: Boolean,
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

        // Drawn over the page rather than instead of it, so a tab keeps its
        // loaded page while home is showing and returns to it untouched.
        //
        // The bar is part of this screen rather than something to reveal: a new
        // tab is where an address gets typed, and hiding the field behind a
        // gesture on the one screen that exists to accept one is perverse.
        // Owned here rather than inside either section, because it is the door
        // between them: the bar sends DOWN to it and the grid answers to it.
        val firstTile = remember { FocusRequester() }
        val homeField = remember { FocusRequester() }

        // Every surface that closes hands focus back to something. Compose does
        // not restore it on its own: the surface that had focus is gone, the
        // home screen becomes focusable again, and nothing asks for it — so
        // every direction does nothing and the viewer is stranded on a screen
        // that looks fine. Keyed on the surface, so it runs on each change.
        LaunchedEffect(chrome, showHome) {
            if (chrome == ChromeSurface.None && showHome) {
                runCatching { homeField.requestFocus() }
            }
        }

        if (showHome) {
            // Opaque: the page underneath is still loaded and would otherwise
            // read through the gaps as a ghost of the last thing opened.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalPalette.current.surface)
                    // While a surface is over the home screen, nothing here may
                    // take focus. Without this the D-pad walks out of the menu
                    // into the address bar behind it, and the menu is still on
                    // screen with nothing in it focused.
                    .focusGroup()
                    .focusProperties { canFocus = chrome == ChromeSurface.None },
            ) {
                NavBar(
                    currentUrl = page.url,
                    canGoBack = page.canGoBack,
                    progress = page.progress,
                    tabCount = tabs.size,
                    isFavourite = isFavourite,
                    editing = editing,
                    onEditingChange = onEditingChange,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onReload = onReload,
                    onHome = onHome,
                    onTabs = onTabs,
                    onToggleFavourite = onToggleFavourite,
                    suggestionsFor = suggestionsFor,
                    onPickSuggestion = onPickSuggestion,
                    onVoice = onVoice,
                    onMenu = onMenu,
                    downTarget = if (homeTiles.isEmpty()) null else firstTile,
                    fieldFocusRequester = homeField,
                )
                HomeGrid(
                    tiles = homeTiles,
                    onOpen = onOpenTile,
                    firstTileFocusRequester = firstTile,
                )
            }
        }

        // Hidden while a chrome surface or the home screen is up, so it is never
        // ambiguous on screen which of the two focus systems the D-pad drives.
        CursorOverlay(
            position = position,
            visible = chrome == ChromeSurface.None && !showHome && !focusMode,
        )

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
                    isFavourite = isFavourite,
                    editing = editing,
                    onEditingChange = onEditingChange,
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onReload = onReload,
                    onHome = onHome,
                    onTabs = onTabs,
                    onToggleFavourite = onToggleFavourite,
                    suggestionsFor = suggestionsFor,
                    onPickSuggestion = onPickSuggestion,
                    onVoice = onVoice,
                    onMenu = onMenu,
                )
            }

            ChromeSurface.Menu -> MenuOverlay(
                canKeepPage = !showHome && page.url.isNotEmpty(),
                isFavourite = isFavourite,
                isDesktopSite = isDesktopSite,
                themeMode = themeMode,
                onNewTab = onNewTab,
                onTabs = onTabs,
                onHome = onHome,
                onReload = onReload,
                onToggleFavourite = onToggleFavourite,
                onCycleTheme = onCycleTheme,
                onBookmarks = onBookmarks,
                onHistory = onHistory,
                onFind = onFind,
                onToggleDesktopSite = onToggleDesktopSite,
            )

            ChromeSurface.Tabs -> TabList(
                tabs = tabs,
                activeId = activeId,
                onSelect = onSelectTab,
                onClose = onCloseTab,
                onNewTab = onNewTab,
            )

            ChromeSurface.Find -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                FindBar(
                    matches = page.findMatches,
                    activeMatch = page.findActiveMatch,
                    editing = editing,
                    onEditingChange = onEditingChange,
                    onQueryChange = onFindQuery,
                    onNext = { onFindStep(true) },
                    onPrevious = { onFindStep(false) },
                )
            }

            ChromeSurface.Bookmarks -> LibraryScreen(
                title = stringResource(R.string.bookmarks_title),
                rows = bookmarkRows,
                emptyMessage = stringResource(R.string.bookmarks_empty),
                onOpen = onOpenTile,
                onClose = onCloseSurface,
                onRemove = onRemoveBookmark,
                removeDescription = stringResource(R.string.bookmarks_remove),
            )

            ChromeSurface.History -> LibraryScreen(
                title = stringResource(R.string.history_title),
                rows = historyRows,
                emptyMessage = stringResource(R.string.history_empty),
                onOpen = onOpenTile,
                onClose = onCloseSurface,
            )

            ChromeSurface.Permission -> permissionAsk?.let { ask ->
                PermissionPrompt(ask = ask, onAnswer = onAnswerPermission)
            } ?: Unit
        }
    }
}
