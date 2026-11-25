@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.data.Cache
import com.youtubeclone.app.data.Extras
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.data.toVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Ek hi ExoPlayer poore app mein (Activity aur background service dono ke liye). */
object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer = player ?: ExoPlayer.Builder(ctx.applicationContext)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        // Bada buffer = kam rukna, smooth playback
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 1_000, 2_500)
                .build()
        )
        .build()
        .also { player = it }
}

/** Abhi kaunsa video chal raha hai, uski state + open/close/quality/speed ka logic. */
object PlayerController {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var generation = 0

    /** SponsorBlock: skip karne wale (start, end) milliseconds. */
    private var segments: List<Pair<Long, Long>> = emptyList()

    var url by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<StreamInfo?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var quality by mutableStateOf<Int?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var loop by mutableStateOf(false)
        private set

    /** Chuna hua caption (null = band). */
    var caption by mutableStateOf<org.schabi.newpipe.extractor.stream.SubtitlesStream?>(null)
        private set

    /** true = poori screen ka Watch page, false = neeche mini player. */
    var expanded by mutableStateOf(false)

    val exo: ExoPlayer get() = PlayerHolder.get(app)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerController.isPlaying = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                this@PlayerController.error = "Playback error (${error.errorCodeName}). Dobara try karo ya quality badlo."
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !loop) playNext()
            }
        })

        // SponsorBlock: har 0.4 second mein dekho ki sponsor segment mein to nahi
        scope.launch {
            while (true) {
                delay(400)
                if (segments.isNotEmpty() && Store.sponsorBlock && exo.isPlaying) {
                    val p = exo.currentPosition
                    val hit = segments.firstOrNull { p >= it.first && p < it.second - 300 }
                    if (hit != null) {
                        exo.seekTo(hit.second)
                        Toast.makeText(app, "Sponsor segment skip kiya", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /** Video khatam hone par agla related video apne aap chalao. */
    private fun playNext() {
        if (!Store.autoplay) return
        next()
    }

    private val stack = ArrayDeque<String>()
    var backCount by mutableStateOf(0)
        private set

    fun previous() {
        val u = stack.removeLastOrNull() ?: return
        backCount = stack.size
        open(u, false)
    }

    fun next() {
        val i = info ?: return
        val next = Repo.related(i).firstOrNull { it.url != i.url } ?: return
        open(next.url)
    }

    fun open(rawUrl: String, pushCurrent: Boolean = true) {
        if (rawUrl == url && (info != null || loading)) {
            expanded = true
            return
        }
        if (pushCurrent) url?.let { stack.addLast(it); backCount = stack.size }
        job?.cancel()
        val my = ++generation
        url = rawUrl
        info = null
        error = null
        quality = null
        caption = null
        loading = true
        expanded = true
        segments = emptyList()
        exo.stop()
        exo.clearMediaItems()

        job = scope.launch {
            try {
                val i = withContext(Dispatchers.IO) { Cache.stream(rawUrl) }
                info = i
                Store.addHistory(i.toVideoItem())
                play(i, null, 0L)
                if (Store.sponsorBlock) {
                    val s = withContext(Dispatchers.IO) { Extras.sponsorSegments(i.id) }
                    if (my == generation) segments = s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (my == generation) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (my == generation) loading = false
            }
        }
    }

    fun retry() {
        val u = url ?: return
        url = null
        open(u)
    }

    fun changeQuality(height: Int?) {
        val i = info ?: return
        val pos = exo.currentPosition
        quality = height
        try {
            play(i, height, pos)
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun changeLoop(on: Boolean) {
        loop = on
        exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun changeCaption(c: org.schabi.newpipe.extractor.stream.SubtitlesStream?) {
        val i = info ?: return
        val pos = exo.currentPosition
        caption = c
        try {
            play(i, quality, pos)
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun changeSpeed(s: Float) {
        speed = s
        exo.setPlaybackSpeed(s)
    }

    private fun play(i: StreamInfo, height: Int?, startMs: Long) {
        val src = Playback.source(i, height, caption)
        exo.setMediaSource(src, startMs)
        exo.prepare()
        exo.setPlaybackSpeed(speed)
        exo.playWhenReady = true
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) {
            // App background mein ho to service start allowed nahi; player phir bhi chalta rahega.
        }
    }

    fun close() {
        stack.clear()
        backCount = 0
        job?.cancel()
        generation++
        segments = emptyList()
        exo.stop()
        exo.clearMediaItems()
        url = null
        info = null
        error = null
        loading = false
        expanded = false
        try {
            app.stopService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) {
        }
    }
}
