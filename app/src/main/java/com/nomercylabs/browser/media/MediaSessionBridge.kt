/*
 * Copyright (c) 2026 NoMercy Labs
 * SPDX-License-Identifier: MIT
 */

package com.nomercylabs.browser.media

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
    private val onAction: (String) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    // The platform API rather than MediaSessionCompat: minSdk is 28, so the
    // compat layer would be a dependency that buys nothing.
    private val session = MediaSession(context, SESSION_TAG).apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onAction("play")
            override fun onPause() = onAction("pause")
            override fun onStop() = onAction("stop")
            override fun onSkipToNext() = onAction("nexttrack")
            override fun onSkipToPrevious() = onAction("previoustrack")
            override fun onFastForward() = onAction("seekforward")
            override fun onRewind() = onAction("seekbackward")
        })
    }

    var nowPlaying: NowPlaying = NowPlaying()
        private set

    /**
     * The object the page talks to. Its surface is deliberately reporting only:
     * a page may describe what it is playing, and may not ask the app to do
     * anything.
     */
    inner class PageInterface {

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
            handler.post { publish(state, actions) }
        }

        @JavascriptInterface
        fun onPlaybackStopped() {
            handler.post { release() }
        }
    }

    val pageInterface: PageInterface = PageInterface()

    private fun publish(state: NowPlaying, actions: String) {
        val wasPlaying: Boolean = nowPlaying.isPlaying
        nowPlaying = state

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
        session.isActive = false
    }

    fun destroy() {
        session.isActive = false
        session.release()
    }

    private companion object {
        const val SESSION_TAG: String = "NoMercyBrowser"
    }
}
