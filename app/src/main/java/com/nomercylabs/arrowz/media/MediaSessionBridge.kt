/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.arrowz.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.webkit.JavascriptInterface

data class NowPlaying(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val durationMillis: Long = 0L,
    val positionMillis: Long = 0L,
)

/**
 * Publishes an Android MediaSession for whatever the page is playing, and routes
 * transport commands back into the page.
 *
 * WebView does none of this. `navigator.mediaSession` exists in the page and can
 * be called without error, but nothing is published to the system, so no
 * transport key reaches anything. Chrome and Brave implement this layer
 * themselves; this is ours.
 */
class MediaSessionBridge(
    context: Context,
    private val onAction: (tabId: String, action: String) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Which tab the published session belongs to.
     *
     * One session is published for the app, never one per tab: two sessions
     * from one process make the system choose, and its choice is not reliably
     * the one the viewer is listening to. That makes ownership something the
     * bridge has to record, so a transport key reaches the tab that is playing
     * rather than the tab that happens to be on screen.
     */
    var owningTabId: String = ""
        private set

    // The platform API rather than MediaSessionCompat: minSdk is 28, so the
    // compat layer would be a dependency that buys nothing.
    private val session = MediaSession(context, SESSION_TAG).apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = dispatch("play")
            override fun onPause() = dispatch("pause")
            override fun onStop() = dispatch("stop")
            override fun onSkipToNext() = dispatch("nexttrack")
            override fun onSkipToPrevious() = dispatch("previoustrack")
            override fun onFastForward() = dispatch("seekforward")
            override fun onRewind() = dispatch("seekbackward")
        })
    }

    var nowPlaying: NowPlaying = NowPlaying()
        private set

    private fun dispatch(action: String) {
        if (owningTabId.isNotEmpty()) onAction(owningTabId, action)
    }

    /** What the eviction policy reads for its media exemption. */
    fun isPlaying(tabId: String): Boolean = nowPlaying.isPlaying && owningTabId == tabId

    /**
     * The object the page talks to, one per tab so every report carries its
     * origin. Its surface is deliberately reporting only: a page may describe
     * what it is playing, and may not ask the app to do anything.
     */
    inner class PageInterface(private val tabId: String) {

        @JavascriptInterface
        fun onPlaybackState(
            playing: Boolean,
            title: String,
            artist: String,
            artwork: String,
            durationMillis: Long,
            positionMillis: Long,
            actions: String,
        ) {
            val state = NowPlaying(
                isPlaying = playing,
                title = title,
                artist = artist,
                artworkUrl = artwork,
                durationMillis = durationMillis,
                positionMillis = positionMillis,
            )
            // The bridge is called from the WebView's JavaScript thread, and a
            // MediaSession must be touched from the main thread.
            handler.post { publish(tabId, state, actions) }
        }

        /**
         * Only the owning tab may clear the session. A second tab loading a page
         * that stops its own media would otherwise wipe the now-playing state of
         * the tab the viewer is actually listening to.
         */
        @JavascriptInterface
        fun onPlaybackStopped() {
            handler.post { if (tabId == owningTabId) release() }
        }
    }

    fun pageInterfaceFor(tabId: String): PageInterface = PageInterface(tabId)

    /** Called when a tab is closed, so its session does not outlive it. */
    fun releaseIfOwnedBy(tabId: String) {
        if (tabId == owningTabId) release()
    }

    private fun publish(tabId: String, state: NowPlaying, actions: String) {
        val wasPlaying: Boolean = nowPlaying.isPlaying
        nowPlaying = state
        owningTabId = tabId

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, state.artworkUrl)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMillis)
                .build(),
        )

        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(
                    if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.positionMillis,
                    1f,
                )
                .setActions(availableActions(actions))
                .build(),
        )

        session.isActive = true
        if (wasPlaying != state.isPlaying) onPlayingChanged(state.isPlaying)
    }

    /**
     * PLAY_PAUSE is always advertised because the fallback path can drive any
     * media element, whether or not the page declared a handler. The rest are
     * advertised only when the page said it can service them, so a remote does
     * not offer a skip button that does nothing.
     */
    private fun availableActions(declared: String): Long {
        var actions: Long = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP

        if (declared.contains("nexttrack")) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (declared.contains("previoustrack")) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (declared.contains("seekforward")) actions = actions or PlaybackState.ACTION_FAST_FORWARD
        if (declared.contains("seekbackward")) actions = actions or PlaybackState.ACTION_REWIND
        return actions
    }

    /**
     * A session left active after playback ends is a stale now-playing entry the
     * user has no way to clear.
     */
    fun release() {
        if (nowPlaying.isPlaying) onPlayingChanged(false)
        nowPlaying = NowPlaying()
        owningTabId = ""
        session.isActive = false
    }

    fun destroy() {
        session.isActive = false
        session.release()
    }

    private companion object {
        const val SESSION_TAG: String = "ArrowzBrowser"
    }
}
