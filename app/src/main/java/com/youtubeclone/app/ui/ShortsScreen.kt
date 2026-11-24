@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.youtubeclone.app.data.Cache
import com.youtubeclone.app.data.ChannelItem
import com.youtubeclone.app.data.Entry
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.ShortsBlock
import com.youtubeclone.app.data.ShortsLimiter
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.data.VideoItem
import com.youtubeclone.app.player.Playback
import com.youtubeclone.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

/** Shorts ka feed: search se chhote (<= 60s) videos chun kar lagataar bharta hai. */
object ShortsState {
    val items = androidx.compose.runtime.mutableStateListOf<VideoItem>()

    // Sirf NEET / padhai se jude Shorts; har baar alag order mein, taaki naye aate rahein
    private val queries = listOf(
        "NEET shorts", "NEET 2027 shorts", "NEET physics shorts", "NEET chemistry shorts",
        "NEET biology shorts", "NEET motivation shorts", "NEET study tips shorts",
        "NEET important questions shorts", "NEET revision shorts", "NCERT biology shorts",
        "NEET PYQ shorts", "NEET topper shorts",
    ).shuffled()
    private var qIdx = 0
    private var query: String? = null
    private var next: Page? = null

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun loadMore() {
        if (loading) return
        loading = true
        try {
            var added = 0
            var guard = 0
            while (added < 8 && guard < 6) {
                guard++
                val r = withContext(Dispatchers.IO) {
                    val p = next
                    val q = query
                    if (p != null && q != null) {
                        Repo.searchMore(q, p)
                    } else {
                        val nq = queries[qIdx % queries.size]
                        qIdx++
                        query = nq
                        Repo.search(nq)
                    }
                }
                next = r.next
                if (r.next == null) query = null // agli baar naya topic
                val fresh = r.items.filterIsInstance<Entry.Video>().map { it.v }
                    .filter { v -> v.duration in 1L..61L && items.none { it.url == v.url } }
                items.addAll(fresh)
                added += fresh.size
            }
            error = if (items.isEmpty()) "Abhi Shorts nahi mile. Internet check karo." else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }
}

@Composable
fun ShortsScreen() {
    val ctx = LocalContext.current
    val items = ShortsState.items
    val scope = rememberCoroutineScope()

    // Shorts ka apna player (main player se alag)
    val exo = remember {
        ExoPlayer.Builder(ctx.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 30_000, 500, 1_500).build())
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) {
        PlayerController.exo.pause() // bada video chal raha ho to roko
        ShortsLimiter.enter()
        onDispose {
            ShortsLimiter.leave()
            exo.release()
        }
    }

    var playing by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { exo.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (playing && items.isNotEmpty() && ShortsLimiter.blocked == null) exo.play()
    }

    // Har second: chalta hai to time gino; limit puri ho to rok do
    LaunchedEffect(exo) {
        while (true) {
            delay(1000)
            if (exo.isPlaying) ShortsLimiter.tick(1000)
            if (ShortsLimiter.blocked != null && exo.isPlaying) {
                exo.pause()
                playing = false
            }
        }
    }

    LaunchedEffect(Unit) { if (items.isEmpty()) ShortsState.loadMore() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            if (ShortsState.loading) {
                LoadingView()
            } else {
                ErrorView(ShortsState.error ?: "Shorts nahi mile") { scope.launch { ShortsState.loadMore() } }
            }
        } else {
            val pager = rememberPagerState { items.size }
            val page = pager.settledPage
            var failed by remember { mutableStateOf<String?>(null) }
            var progress by remember { mutableFloatStateOf(0f) }

            // Page badalne par naya short load karo, agle 2 pehle se tayyar rakho
            LaunchedEffect(page) {
                val v = items.getOrNull(page) ?: return@LaunchedEffect
                failed = null
                try {
                    val info = withContext(Dispatchers.IO) { Cache.stream(v.url) }
                    exo.setMediaSource(Playback.source(info, null))
                    exo.prepare()
                    val ok = ShortsLimiter.blocked == null
                    exo.playWhenReady = ok
                    playing = ok
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    failed = e.message ?: "Ye short nahi chal paya"
                }
                for (k in 1..2) {
                    items.getOrNull(page + k)?.let { n ->
                        launch(Dispatchers.IO) { runCatching { Cache.stream(n.url) } }
                    }
                }
                if (page >= items.size - 4) ShortsState.loadMore()
            }

            LaunchedEffect(exo) {
                while (true) {
                    delay(200)
                    val d = exo.duration
                    progress = if (d > 0) (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
            }

            VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                val v = items[i]
                ShortPage(
                    v = v,
                    active = i == page,
                    exo = exo,
                    playing = playing,
                    failed = if (i == page) failed else null,
                    progress = progress,
                    onToggle = {
                        if (exo.isPlaying) {
                            exo.pause(); playing = false
                        } else {
                            exo.play(); playing = true
                        }
                    },
                )
            }
        }

        Text(
            "Shorts",
            Modifier.align(Alignment.TopStart).padding(16.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        // Limit poori ho gayi: Shorts band
        ShortsLimiter.blocked?.let { b ->
            LimitOverlay(b) {
                ShortsLimiter.newSession()
                playing = true
                exo.play()
            }
        }
    }
}

@Composable
private fun LimitOverlay(block: ShortsBlock, onContinue: () -> Unit) {
    val usedMin = (ShortsLimiter.todayMs / 60_000).toInt()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            if (block == ShortsBlock.DAILY) "Aaj ka Shorts limit poora" else "Thoda break lo",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (block == ShortsBlock.DAILY)
                "Aaj aapne $usedMin minute Shorts dekhe (limit 2 ghante). Kal phir milte hain!"
            else
                "Lagataar 3 minute Shorts ho gaye, isliye ruk gaye. Aaj ab tak $usedMin minute dekhe (limit 120 minute).",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (block == ShortsBlock.SESSION) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onContinue) { Text("Phir se dekho") }
        }
    }
}

@Composable
private fun ShortPage(
    v: VideoItem,
    active: Boolean,
    exo: ExoPlayer,
    playing: Boolean,
    failed: String?,
    progress: Float,
    onToggle: () -> Unit,
) {
    val ctx = LocalContext.current
    val liked = Store.liked.any { it.url == v.url }
    val saved = Store.watchLater.any { it.url == v.url }
    val subscribed = Store.isSubscribed(v.uploaderUrl)

    Box(Modifier.fillMaxSize()) {
        // Video aane tak thumbnail
        AsyncImage(
            model = v.thumb.ifEmpty { null },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        if (active) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        player = exo
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Tap = play / pause
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
        )

        if (active && !playing && failed == null) {
            Icon(
                Icons.Filled.PlayArrow, null,
                Modifier.align(Alignment.Center).size(80.dp),
                tint = Color(0xCCFFFFFF),
            )
        }
        if (failed != null) {
            Text(
                failed,
                Modifier.align(Alignment.Center).padding(24.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        }

        // Neeche ka gradient + info
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
        )

        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 84.dp, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(null, v.uploader, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    v.uploader,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = { Store.toggleSub(ChannelItem(v.uploaderUrl, v.uploader)) },
                    shape = RoundedCornerShape(50),
                    color = if (subscribed) Color(0x55FFFFFF) else Color.White,
                    contentColor = if (subscribed) Color.White else Color.Black,
                ) {
                    Text(
                        if (subscribed) "Subscribed" else "Subscribe",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(v.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShortAction(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", liked) { Store.toggleLike(v) }
            Spacer(Modifier.height(14.dp))
            ShortAction(Icons.Outlined.Share, "Share", false) { shareUrl(ctx, v.url) }
            Spacer(Modifier.height(14.dp))
            ShortAction(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, "Save", saved) { Store.toggleWatchLater(v) }
        }

        if (active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
                color = Color.White,
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, Modifier.size(30.dp), tint = if (active) YtRed else Color.White)
        }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
