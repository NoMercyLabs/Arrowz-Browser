/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.CaptioningManager
import android.net.Uri
import android.os.Environment
import android.speech.RecognizerIntent
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.ui.res.stringResource
import com.nomercylabs.arrowz.a11y.A11yMode
import com.nomercylabs.arrowz.a11y.Announcer
import com.nomercylabs.arrowz.a11y.CaptionStyles
import com.nomercylabs.arrowz.a11y.ScreenReaderWatch
import com.nomercylabs.arrowz.a11y.TextScale
import com.nomercylabs.arrowz.browser.PageError
import com.nomercylabs.arrowz.browser.asJsString
import com.nomercylabs.arrowz.chrome.FindBar
import com.nomercylabs.arrowz.privacy.CookiePolicy
import com.nomercylabs.arrowz.privacy.FilterEngine
import com.nomercylabs.arrowz.privacy.ListUpdater
import com.nomercylabs.arrowz.privacy.RequestFilter
import java.io.File
import com.nomercylabs.arrowz.chrome.LibraryRow
import com.nomercylabs.arrowz.chrome.LibraryScreen
import com.nomercylabs.arrowz.chrome.PermissionAsk
import com.nomercylabs.arrowz.chrome.PermissionPrompt
import com.nomercylabs.arrowz.forms.FieldKind
import com.nomercylabs.arrowz.forms.FormBridge
import com.nomercylabs.arrowz.forms.FormField
import com.nomercylabs.arrowz.forms.FormFieldOverlay
import com.nomercylabs.arrowz.forms.SelectListSheet
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
import com.nomercylabs.arrowz.browser.ExternalLink
import com.nomercylabs.arrowz.browser.UrlOrSearch
import com.nomercylabs.arrowz.browser.UserAgents
import androidx.webkit.WebViewAssetLoader
import com.nomercylabs.arrowz.browser.PageState
import com.nomercylabs.arrowz.browser.WebViewHost
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import com.nomercylabs.arrowz.chrome.HomeGrid
import com.nomercylabs.arrowz.chrome.MenuOverlay
import com.nomercylabs.arrowz.chrome.NavBar
import com.nomercylabs.arrowz.chrome.TabList
import com.nomercylabs.arrowz.cursor.CursorOverlay
import com.nomercylabs.arrowz.cursor.CursorPosition
import com.nomercylabs.arrowz.cursor.CursorState
import com.nomercylabs.arrowz.cursor.EdgeScroller
import com.nomercylabs.arrowz.cursor.TouchSynthesizer
import com.nomercylabs.arrowz.input.BrowserState
import com.nomercylabs.arrowz.media.FullscreenController
import com.nomercylabs.arrowz.media.MediaSessionBridge
import com.nomercylabs.arrowz.input.Command
import com.nomercylabs.arrowz.input.KeyDispatcher
import com.nomercylabs.arrowz.input.KeyGestureTracker
import com.nomercylabs.arrowz.input.KeyPhase
import com.nomercylabs.arrowz.input.InputMode
import com.nomercylabs.arrowz.input.RemoteKey
import com.nomercylabs.arrowz.spatial.NavigabilityProbe
import com.nomercylabs.arrowz.spatial.SpatialNavBridge
import com.nomercylabs.arrowz.ui.Tokens
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nomercylabs.arrowz.data.Bookmark
import com.nomercylabs.arrowz.data.BrowserStore
import com.nomercylabs.arrowz.data.HomeContent
import com.nomercylabs.arrowz.data.SqliteBrowserStore
import com.nomercylabs.arrowz.data.Suggestion
import com.nomercylabs.arrowz.data.Suggestions
import com.nomercylabs.arrowz.data.Tile
import com.nomercylabs.arrowz.data.Visit
import com.nomercylabs.arrowz.tabs.MemoryPressure
import com.nomercylabs.arrowz.tabs.Tab
import com.nomercylabs.arrowz.tabs.TabPage
import com.nomercylabs.arrowz.tabs.TabRegistry
import androidx.compose.ui.graphics.toArgb
import com.nomercylabs.arrowz.ui.LocalPalette
import com.nomercylabs.arrowz.ui.Palette
import com.nomercylabs.arrowz.ui.Palettes
import com.nomercylabs.arrowz.ui.ThemeMode
import com.nomercylabs.arrowz.ui.TvTheme
import androidx.compose.ui.focus.FocusRequester

/** Which chrome surface is over the page. Never two, and never both hidden and
 *  consuming input. */
private enum class ChromeSurface {
    None, NavBar, Tabs, Menu, Find, Bookmarks, History, Permission, Form, Select,
}

/** Where a dictation result belongs. The recogniser is a separate activity, so
 *  the answer comes back long after whatever asked for it stopped being on
 *  screen, and without this it lands wherever the address bar happens to be. */
private enum class VoiceTarget { Address, Field }

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
    private var signedInOrigins: Set<String> by mutableStateOf(emptySet())

    private val cookiePolicy = CookiePolicy(allowedOrigins = { signedInOrigins })

    private var pendingFileChooser: android.webkit.ValueCallback<Array<Uri>>? = null

    /** Cursor or focus. Never both: exactly one system may consume a key. */
    private var inputMode: InputMode by mutableStateOf(InputMode.Cursor)

    private lateinit var screenReader: ScreenReaderWatch

    private val filters = FilterEngine()
    private var filteringEnabled: Boolean by mutableStateOf(true)
    private var blockedOnPage: Int by mutableStateOf(0)

    private val announcer = Announcer(
        isActive = { screenReader.isActive },
        speak = { text -> pageContainer.announceForAccessibility(text) },
    )

    private val spatial = SpatialNavBridge { host?.view }

    /** Re-asking the page what it is, because a page that renders itself has
     *  nothing to report at the moment it commits. */
    private val modeProbe = android.os.Handler(android.os.Looper.getMainLooper())

    /** Bumped by every navigation and every deliberate choice, so probes still
     *  walking an older page cannot land on a newer one. */
    private var modeProbeGeneration: Int = 0

    /** The field being edited natively, and what has been typed into it so far.
     *  Held on the activity rather than in the sheet's composition, because
     *  dictation tears that composition down and comes back to it. */
    private var formField: FormField? by mutableStateOf(null)
    private var formValue: String by mutableStateOf("")

    private var voiceTarget: VoiceTarget = VoiceTarget.Address

    private val voiceInput = registerForActivityResult(StartActivityForResult()) { result ->
        val spoken: String? = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (spoken.isNullOrBlank()) return@registerForActivityResult

        when (voiceTarget) {
            VoiceTarget.Address -> navigate(spoken)
            // Appended rather than replacing, so a second phrase adds to the
            // first instead of throwing it away.
            VoiceTarget.Field -> formValue = if (formValue.isEmpty()) {
                spoken
            } else {
                "$formValue $spoken"
            }
        }
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
        loadSignedInOrigins()
        loadFilterPreference()
        loadFilters()

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

        // Started before the first tab exists, so the first page never renders a
        // pointer over a reader that was already running when the app launched.
        screenReader = ScreenReaderWatch(this) { active -> screenReaderChanged(active) }
        screenReader.start()

        registry.open()
        // A cold start from a link has one empty tab and nothing to protect, so
        // the link takes it rather than opening a second one behind the first.
        openExternalLink(intent, inNewTab = false)
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
                    // Anything but the pointer hides the pointer. A screen reader
                    // draws its own highlight, and two of them on one screen
                    // means every press appears to do two things.
                    focusMode = inputMode != InputMode.Cursor,
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
                    inputModeIsFocus = when (inputMode) {
                        InputMode.ScreenReader -> null
                        InputMode.Focus -> true
                        InputMode.Cursor -> false
                    },
                    onToggleInputMode = {
                        setInputMode(
                            if (inputMode == InputMode.Focus) InputMode.Cursor else InputMode.Focus,
                            remember = true,
                        )
                        showChrome(ChromeSurface.None)
                    },
                    isStaySignedIn =
                        HomeContent.originOf(host?.state?.url ?: "") in signedInOrigins,
                    onToggleStaySignedIn = { toggleStaySignedIn() },
                    isFilteringOn = filteringEnabled,
                    blockedOnPage = blockedOnPage,
                    onToggleFiltering = { toggleFiltering() },
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
                    formField = formField,
                    formValue = formValue,
                    onFormValueChange = { typed -> formValue = typed },
                    onCommitField = { commitField() },
                    onChooseOption = { index -> chooseOption(index) },
                    onFieldVoice = { startVoiceInput(VoiceTarget.Field) },
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

        // Before the first configure, which is what registers the interface the
        // injected script talks to.
        page.formBridge = FormBridge(
            webView = { page.view },
            now = { SystemClock.uptimeMillis() },
            onOpenField = { field -> openField(field) },
        )
        page.requestFilter = RequestFilter(
            engine = filters,
            isEnabled = { filteringEnabled },
            // Off a network thread onto the one that owns the state Compose
            // reads, and only for the tab on screen: a background tab counting
            // up would relabel the shield over whatever is in front.
            onBlocked = { total ->
                runOnUiThread { if (registry.active?.page === page) blockedOnPage = total }
            },
        )
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
            spatial.clear()
            // A new document, so the old page's focused field and its edits are
            // gone with it. Keeping either exempts this tab from suspension for
            // the rest of its life.
            page.formBridge?.forgetPage()
            // Before anything is fetched for the new document, or the first
            // requests are judged against the previous page's host.
            page.requestFilter?.pageChanged(url)
            if (registry.active?.page === page) blockedOnPage = 0
            applyCosmeticRules(page)
            chooseInputMode(url)
            // A new document, so whatever was last said describes a page that is
            // no longer on screen and must not suppress an identical sentence
            // about this one.
            announcer.forgetLast()
            val title: String = page.pageTitle
            storeThread.execute {
                store.recordVisit(url, title)
                publishLibrary()
            }
        }

        page.onLoaded { url ->
            applyCaptionStyles(page)
            announcePage(page, url)
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
            textZoomPercent = TextScale.zoomPercent(resources.configuration.fontScale),
            scriptsAtDocumentStart = listOf(
                readAsset("mediasession-shim.js"),
                readAsset("spatialnav.js"),
                readAsset("formbridge.js"),
                readAsset("captions.js"),
                readAsset("cosmetic.js"),
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
            onInterceptRequest = { request -> page.requestFilter?.intercept(request) },
        )
        // One interface per tab, so every report the page makes carries which
        // tab it came from.
        page.addBridge("NoMercyMedia", mediaSession.pageInterfaceFor(tabId))

        // Re-added on every rebuild: a renderer death takes the interfaces with
        // it, and a form bridge missing from a rebuilt tab fails as silence.
        page.formBridge?.let { bridge -> page.addBridge("NmForms", bridge) }
    }

    private fun loadFilterPreference() = storeThread.execute {
        val stored: String? = store.preference(FILTER_KEY)
        runOnUiThread { filteringEnabled = stored != FILTER_OFF }
    }

    /**
     * Off is a per-browser choice rather than per site, because the reason to
     * turn it off is nearly always "this one page is broken and I want to see
     * it", and a setting that has to be found again to turn back on is a setting
     * that stays off.
     */
    private fun toggleFiltering() {
        filteringEnabled = !filteringEnabled
        val value: String = if (filteringEnabled) FILTER_ON else FILTER_OFF
        storeThread.execute { store.setPreference(FILTER_KEY, value) }
        // The page in front was built under the old answer, so it has to be
        // built again for the change to mean anything.
        host?.reload()
        showChrome(ChromeSurface.None)
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
        val stored: Set<String> = originSet(DESKTOP_ORIGINS_KEY)
        runOnUiThread { desktopOrigins = stored }
    }

    private fun loadSignedInOrigins() = storeThread.execute {
        val stored: Set<String> = originSet(SIGNED_IN_ORIGINS_KEY)
        runOnUiThread { signedInOrigins = stored }
    }

    private fun originSet(key: String): Set<String> = (store.preference(key) ?: "")
        .split(',')
        .filter { origin -> origin.isNotBlank() }
        .toSet()

    /**
     * Whether this site's cookies survive the browser closing.
     *
     * A television is shared. Keeping every session hands the next person in the
     * room somebody else's mail; keeping none means nobody can stay signed in
     * anywhere. So it is a choice, per site, and it is theirs.
     */
    private fun toggleStaySignedIn() {
        val origin: String = HomeContent.originOf(host?.state?.url ?: "")
        if (origin.isEmpty()) return
        signedInOrigins =
            if (origin in signedInOrigins) signedInOrigins - origin else signedInOrigins + origin
        val stored: String = signedInOrigins.joinToString(",")
        storeThread.execute { store.setPreference(SIGNED_IN_ORIGINS_KEY, stored) }
        showChrome(ChromeSurface.None)
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

    /**
     * A link arriving while the browser is already up.
     *
     * `singleTask` means this activity is reused rather than recreated, so
     * without this a shared link would raise the window and show whatever was
     * already on screen — which is what it did, and which reads as the link
     * being swallowed.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Kept, or a later getIntent() still reports the one we launched with.
        setIntent(intent)
        openExternalLink(intent, inNewTab = true)
    }

    /**
     * [inNewTab] is the whole difference between a cold start and a warm one.
     * Loading over the live tab would discard a page the viewer was reading and,
     * worse, a form they were part way through — the one thing the registry
     * refuses to do even under memory pressure.
     */
    private fun openExternalLink(intent: Intent?, inNewTab: Boolean) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url: String = ExternalLink.resolve(intent.dataString) ?: return

        if (inNewTab) {
            registry.open()
            attachActivePage()
        }
        openFromHome(url)
        if (inNewTab) relievePressure()
    }

    private fun newTab() {
        registry.open()
        registry.active?.isHome = true
        attachActivePage()
        showChrome(ChromeSurface.None)
        announcer.announce(getString(R.string.a11y_tab_opened))
        relievePressure()
    }

    private fun selectTab(id: String) {
        registry.activate(id)
        attachActivePage()
        showChrome(ChromeSurface.None)
        // Switching tabs replaces everything on screen and says nothing. The
        // page behind the list is the one fact that makes the switch legible.
        announcer.announce(
            getString(
                R.string.a11y_tab_switched,
                Announcer.nameFor(
                    host?.pageTitle.orEmpty(),
                    HomeContent.originOf(host?.state?.url.orEmpty()),
                ),
            ),
        )
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
        screenReader.stop()
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
        // Told apart because BACK treats them differently and everything else
        // does not: the home screen is Compose focus territory exactly like the
        // bar and the tab list, but it is a place to return to rather than an
        // overlay to close.
        isSurfaceOpen = chrome != ChromeSurface.None,
        isShowingHome = registry.active?.isHome ?: true,
        isFullscreen = fullscreenActive,
        // The window is asked rather than the composition flag: the leanback IME
        // hides itself on BACK and does not always consume the key, so the flag
        // can already be false while the same press is still travelling. Read as
        // "not editing", that press falls through to the exit rung of the
        // ladder, and dropping the keyboard closes the browser.
        mode = inputMode,
        isEditingText = editingText || isKeyboardShowing(),
        // The page's own field, not ours. BACK releases it and stops there,
        // which is what every native app on this platform does.
        isPageFieldFocused = host?.formBridge?.focusedField != null,
        isPageModalOpen = spatial.hasModal,
    )

    /**
     * Speaking instead of typing.
     *
     * Not every television has a speech service — the recogniser is part of the
     * Google app, which a stripped Android TV build may not carry — so the
     * absence is reported rather than crashing on a missing activity.
     */
    private fun startVoiceInput(target: VoiceTarget = VoiceTarget.Address) {
        voiceTarget = target
        val prompt: String = when (target) {
            VoiceTarget.Address -> getString(R.string.nav_voice)
            VoiceTarget.Field -> formField?.label?.ifBlank { null } ?: getString(R.string.form_voice)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        runCatching { voiceInput.launch(intent) }
            .onFailure { showMessage(getString(R.string.voice_unavailable)) }
    }

    /**
     * Opens the native sheet for a field the viewer's own press focused.
     *
     * A select becomes a list and a text field becomes a native editor. Nothing
     * else is offered: checkboxes and buttons are activated rather than typed
     * into, and the page reports none of them.
     */
    private fun openField(field: FormField) {
        formField = field
        formValue = field.value
        showChrome(if (field.kind == FieldKind.Select) ChromeSurface.Select else ChromeSurface.Form)
        reportForTests("open", field.id)
    }

    /**
     * Tells the page when the sheet opened and when a commit finished.
     *
     * Debug builds only, and it exists because nothing outside the app could
     * answer either question. A harness driving the overlay had to guess at
     * delays instead: typing on a fixed delay put characters into a sheet before
     * it existed, so a whole run came out shifted by one field. `activeElement`
     * answers the first question only after the tap has landed, and the second
     * has no external answer at all.
     *
     * Written into the page rather than logged, because the thing that needs to
     * wait on it is already talking to the page.
     */
    private fun reportForTests(event: String, id: String, value: String = "") {
        if (!BuildConfig.DEBUG) return
        host?.view?.evaluateJavascript(
            "window.__nmTest = Object.assign(window.__nmTest || {}, {" +
                "event:${event.asJsString()}," +
                "id:${id.asJsString()}," +
                "value:${value.asJsString()}," +
                "seq:((window.__nmTest && window.__nmTest.seq) || 0) + 1})",
            null,
        )
    }

    /**
     * Writes what was typed back into the page and closes.
     *
     * The element is found by the id it was stamped with rather than by whatever
     * holds focus now: opening the sheet took Android focus off the WebView, so
     * `activeElement` is not a safe answer by the time this runs.
     */
    private fun commitField() {
        val field: FormField = formField ?: return
        host?.formBridge?.commit(field.id, formValue)
        showChrome(ChromeSurface.None)
        reportForTests("commit", field.id, formValue)
    }

    private fun chooseOption(optionIndex: Int) {
        val field: FormField = formField ?: return
        host?.formBridge?.select(field.id, optionIndex)
        showChrome(ChromeSurface.None)
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

        // Changing what the site is told means asking it again, and a reload
        // discards form state the same way memory pressure would. Said before it
        // happens rather than discovered afterwards.
        if (host?.hasDirtyForm == true) showMessage(getString(R.string.form_dirty_reload))

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

        // Leaving a field sheet by any route other than committing discards what
        // was typed, exactly as cancelling a native dialog does. The page keeps
        // the value it already had.
        if (chrome in FIELD_SURFACES && surface !in FIELD_SURFACES) {
            formField = null
            formValue = ""
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

        // The grid the viewer came from, not the launcher. The page stays
        // loaded behind it, so returning to it costs nothing.
        Command.GoHome -> {
            registry.active?.isHome = true
            showChrome(ChromeSurface.None)
            true
        }
        // The wipe runs before finish() rather than in onDestroy: a process the
        // system reclaims never reaches onDestroy, and a session that survives
        // because the television was busy is a session the next person inherits.
        Command.ExitApp -> {
            cookiePolicy.wipeSession { finish() }
            true
        }

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
            /**
             * Always a press, never a shortcut through what the page last
             * reported.
             *
             * This used to open whichever field the bridge remembered, because
             * the spatial search focused a field on the way in and no new report
             * was coming. That stopped being true when arriving at a field
             * stopped giving it DOM focus — which is what keeps the system
             * keyboard down — and the remembered field became a stale record of
             * something edited minutes ago.
             *
             * Measured on the 8000 across a page of every input type: OK on the
             * second field reopened the first, so the first was typed into twice
             * and the other eight received nothing at all.
             */
            // Recorded before the press lands. A field focus arriving shortly
            // after this is the viewer's; one arriving on its own is the page
            // focusing its own search box on load, and interrupting for that is
            // what makes a browser raise a keyboard over every home page it
            // opens.
            host?.formBridge?.noteActivation()
            if (inputMode == InputMode.Focus) {
                spatial.activate { x, y ->
                    host?.view?.let { view ->
                        TouchSynthesizer.tap(view, CursorPosition(x, y))
                    }
                }
            } else {
                host?.view?.let { view -> TouchSynthesizer.tap(view, cursor.position()) }
            }
            true
        }

        Command.ReleasePageFocus -> {
            host?.formBridge?.blurFocusedField()
            true
        }

        Command.DismissPageModal -> {
            spatial.dismissModal()
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

    /**
     * A screen reader arrived or left.
     *
     * The generation bump is the important half. A probe scheduled before the
     * reader arrived would otherwise answer afterwards and hand the page back to
     * our own search, and two focus systems would be walking the same tree.
     */
    private fun screenReaderChanged(active: Boolean) {
        val next: InputMode = A11yMode.modeFor(active, inputMode)
        modeProbeGeneration++
        if (next != inputMode) setInputMode(next, remember = false)
    }

    /**
     * The television's caption preferences, as a `::cue` rule.
     *
     * Applied per document rather than once, because a page replaces the
     * stylesheet along with everything else, and re-applied when the preference
     * changes while a page is open.
     */
    private fun applyCaptionStyles(page: WebViewHost) {
        val captioning: CaptioningManager? =
            getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        val style: CaptioningManager.CaptionStyle? = captioning?.userStyle
        val css: String = CaptionStyles.css(
            enabled = captioning?.isEnabled == true,
            captionFontScale = captioning?.fontScale ?: 1f,
            userFontScale = resources.configuration.fontScale,
            foregroundArgb = style?.takeIf { it.hasForegroundColor() }?.foregroundColor,
            backgroundArgb = style?.takeIf { it.hasBackgroundColor() }?.backgroundColor,
            edgeType = style?.takeIf { it.hasEdgeType() }?.edgeType ?: CaptionStyles.EDGE_NONE,
            edgeArgb = style?.takeIf { it.hasEdgeColor() }?.edgeColor,
        )
        page.view?.evaluateJavascript(
            "window.__nmCaptions && window.__nmCaptions.apply(${css.asJsString()})",
            null,
        )
    }

    /**
     * Element hiding, applied at document start rather than at load.
     *
     * Later than this and the slot is drawn before it is hidden, which reads as
     * the page flickering rather than as anything being blocked.
     */
    private fun applyCosmeticRules(page: WebViewHost) {
        val css: String = if (filteringEnabled) {
            page.requestFilter?.cssForCurrentPage().orEmpty()
        } else {
            ""
        }
        page.view?.evaluateJavascript(
            "window.__nmHide && window.__nmHide.apply(${css.asJsString()})",
            null,
        )
    }

    /**
     * Reads the lists and keeps them current, entirely off the UI thread.
     *
     * The seed ships in the APK so a television is protected on its first page
     * load rather than after its first successful fetch, which on a box that is
     * switched off more than it is on could be days.
     */
    private fun loadFilters() = storeThread.execute {
        val updater = ListUpdater(
            cacheDirectory = File(filesDir, FILTER_DIRECTORY),
            now = { System.currentTimeMillis() },
            readSeed = { readAsset("filters-seed.txt") },
        )
        filters.replaceRules(updater.load())

        // Blocking turned off means no request is made, which is what the
        // privacy policy says and therefore what the code has to do. Read from
        // the store rather than from the state the UI holds, because this runs
        // during startup and that state may not have arrived yet.
        if (store.preference(FILTER_KEY) == FILTER_OFF) return@execute

        if (!updater.isDue()) return@execute
        if (updater.update()) filters.replaceRules(updater.load())
    }

    /** A page finishing is a visual event with no announcement of its own, and a
     *  page that failed has no title to announce, which is when it matters most. */
    private fun announcePage(page: WebViewHost, url: String) {
        val name: String = Announcer.nameFor(page.pageTitle, HomeContent.originOf(url))
        if (name.isEmpty()) return
        val error: PageError? = page.state.error
        announcer.announce(
            if (error != null) {
                getString(R.string.a11y_page_failed, name)
            } else {
                getString(R.string.a11y_page_loaded, name)
            },
        )
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
            spatial.focusFirst()
        } else {
            spatial.clear()
        }

        if (!remember) return

        // A deliberate choice ends the argument. Any probe still waiting to
        // report would otherwise arrive afterwards and overrule it.
        modeProbeGeneration++

        val origin: String = HomeContent.originOf(host?.state?.url ?: "")
        if (origin.isEmpty()) return
        storeThread.execute { store.setPreference("$MODE_KEY_PREFIX$origin", mode.name) }
    }

    /**
     * What a freshly loaded page starts in: the site's remembered override if
     * there is one, and otherwise whatever the page's own shape argues for.
     */
    private fun chooseInputMode(url: String) {
        // Above the remembered override deliberately. Per-site memory is a
        // convenience; a reader running right now is not, and a site remembered
        // as focus mode must not drag our search over the top of it.
        if (!A11yMode.mayChooseMode(screenReader.isActive)) {
            setInputMode(InputMode.ScreenReader, remember = false)
            return
        }

        val origin: String = HomeContent.originOf(url)
        val generation: Int = ++modeProbeGeneration
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
                // Deliberately does not switch to the pointer first. Doing that
                // put a cursor on screen for a quarter of a second on every
                // navigation and then took it away again, and mid-flip both
                // systems were drawn at once — a pointer and a focus ring on the
                // same frame, which is what "it does both" looks like. The mode
                // in the viewer's hands stays as it is until the page has
                // actually answered.
                askThePageAgain(generation, attempt = 0)
            }
        }
    }

    /**
     * Asks the page whether it can be walked by focus, more than once.
     *
     * Measured on the 8010: DuckDuckGo sat in cursor mode with the pointer
     * parked against the bottom edge, which reads exactly like a dead remote.
     * The page reported six reachable controls when asked — but the question had
     * been put at navigation commit, before its own script had drawn any of
     * them. Deciding once, at the earliest possible instant, gets the answer
     * wrong on every page that renders itself, and that is most of the web now.
     *
     * Only ever upgrades. Taking the pointer away from somebody already using it
     * because a late banner appeared is worse than starting in the wrong mode,
     * and a long press remembers their choice and cancels this outright.
     */
    private fun askThePageAgain(generation: Int, attempt: Int) {
        if (generation != modeProbeGeneration) return
        if (attempt >= MODE_PROBE_DELAYS_MS.size) return

        modeProbe.postDelayed({
            if (generation != modeProbeGeneration) return@postDelayed
            spatial.probe { page ->
                if (generation != modeProbeGeneration) return@probe
                // Checked when the answer lands, not only when it was asked for.
                // A reader can arrive during the three seconds this ladder spans.
                if (!A11yMode.mayChooseMode(screenReader.isActive)) return@probe
                when {
                    page != null && NavigabilityProbe.prefersFocusMode(page) ->
                        setInputMode(InputMode.Focus, remember = false)

                    attempt + 1 < MODE_PROBE_DELAYS_MS.size ->
                        askThePageAgain(generation, attempt + 1)

                    // Out of chances. A page that has drawn nothing focusable by
                    // now is one the pointer should have, and this is the only
                    // place the switch is made — after an answer, not before.
                    else -> setInputMode(InputMode.Cursor, remember = false)
                }
            }
        }, MODE_PROBE_DELAYS_MS[attempt])
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
        const val SIGNED_IN_ORIGINS_KEY: String = "cookies.keep"
        const val DECISION_ALLOW: String = "allow"
        const val DECISION_BLOCK: String = "block"
        const val MODE_KEY_PREFIX: String = "mode."
        const val FILTER_KEY: String = "privacy.filtering"
        const val FILTER_ON: String = "on"
        const val FILTER_OFF: String = "off"

        /** Under filesDir, never in the cache directory: the system may empty
         *  that at any moment, and a television that wakes up unprotected
         *  because the OS wanted 30MB back is the failure this whole slice is. */
        const val FILTER_DIRECTORY: String = "filters"

        val DIRECTIONS: Set<RemoteKey> =
            setOf(RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right)

        /** The two surfaces that hold a web field's value. Moving between them
         *  keeps it; leaving for anything else discards it. */
        val FIELD_SURFACES: Set<ChromeSurface> =
            setOf(ChromeSurface.Form, ChromeSurface.Select)

        /**
         * When to ask the page what it is, after it commits.
         *
         * Spread rather than repeated: a static page answers on the first, a
         * framework that renders on load answers on the second or third, and a
         * slow one on a weak processor gets the last. Stopping at three seconds
         * because a page that has drawn nothing focusable by then is a page the
         * pointer should keep.
         */
        val MODE_PROBE_DELAYS_MS: LongArray = longArrayOf(250, 750, 1500, 3000)
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
    inputModeIsFocus: Boolean?,
    onToggleInputMode: () -> Unit,
    isStaySignedIn: Boolean,
    onToggleStaySignedIn: () -> Unit,
    isFilteringOn: Boolean,
    blockedOnPage: Int,
    onToggleFiltering: () -> Unit,
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
    formField: FormField?,
    formValue: String,
    onFormValueChange: (String) -> Unit,
    onCommitField: () -> Unit,
    onChooseOption: (Int) -> Unit,
    onFieldVoice: () -> Unit,
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

        // Every surface that closes hands focus back to the home screen.
        // Compose restores nothing on its own: the surface that had focus is
        // gone, the home screen becomes focusable again, and nothing asks for
        // it, so every direction does nothing on a screen that looks fine.
        //
        // The field, not the section. Asking the section was the bug: requesting
        // focus on a focus group reports Success(true) — measured on the 8000 —
        // and leaves the group itself holding focus with no leaf below it
        // active. Nothing draws a ring, and a direction press has no origin to
        // move from, so the screen ignored UP, DOWN and LEFT and only came back
        // when OK reached the field by another route.
        //
        // `focusRestorer` still earns its place for directional re-entry, which
        // is where it actually fires; it does not run for a programmatic request.
        LaunchedEffect(chrome, showHome) {
            if (chrome != ChromeSurface.None || !showHome) return@LaunchedEffect
            runCatching { homeField.requestFocus() }
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
                    .focusRestorer(homeField)
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
                inputModeIsFocus = inputModeIsFocus,
                onToggleInputMode = onToggleInputMode,
                isStaySignedIn = isStaySignedIn,
                onToggleStaySignedIn = onToggleStaySignedIn,
                isFilteringOn = isFilteringOn,
                blockedOnPage = blockedOnPage,
                onToggleFiltering = onToggleFiltering,
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

            // Editing state lives on the activity rather than in here, because
            // dictation launches a system activity and tears this composition
            // down around it.
            ChromeSurface.Form -> formField?.let { field ->
                FormFieldOverlay(
                    field = field,
                    value = formValue,
                    onValueChange = onFormValueChange,
                    editing = editing,
                    onEditingChange = onEditingChange,
                    onCommit = onCommitField,
                    onVoice = onFieldVoice,
                )
            } ?: Unit

            ChromeSurface.Select -> formField?.let { field ->
                SelectListSheet(
                    field = field,
                    onChoose = onChooseOption,
                    onClose = onCloseSurface,
                )
            } ?: Unit
        }
    }
}
