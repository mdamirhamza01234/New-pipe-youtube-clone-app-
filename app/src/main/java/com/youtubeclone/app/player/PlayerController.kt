@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.player

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.youtubeclone.app.data.Cache
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.data.toVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
        .build()
        .also { player = it }
}

/** Abhi kaunsa video chal raha hai, uski state + open/close/quality ka logic. */
object PlayerController {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var generation = 0

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
        })
    }

    fun open(rawUrl: String) {
        if (rawUrl == url && (info != null || loading)) {
            expanded = true
            return
        }
        job?.cancel()
        val my = ++generation
        url = rawUrl
        info = null
        error = null
        quality = null
        loading = true
        expanded = true
        exo.stop()
        exo.clearMediaItems()

        job = scope.launch {
            try {
                val i = withContext(Dispatchers.IO) { Cache.stream(rawUrl) }
                info = i
                Store.addHistory(i.toVideoItem())
                play(i, null, 0L)
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

    private fun play(i: StreamInfo, height: Int?, startMs: Long) {
        val src = Playback.source(i, height)
        exo.setMediaSource(src, startMs)
        exo.prepare()
        exo.playWhenReady = true
        try {
            app.startService(Intent(app, PlaybackService::class.java))
        } catch (_: Exception) {
            // App background mein ho to service start allowed nahi; player phir bhi chalta rahega.
        }
    }

    fun close() {
        job?.cancel()
        generation++
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
