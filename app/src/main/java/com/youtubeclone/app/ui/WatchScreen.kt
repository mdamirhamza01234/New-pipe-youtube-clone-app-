@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.youtubeclone.app.data.ChannelItem
import com.youtubeclone.app.data.CommentItem
import com.youtubeclone.app.data.Extras
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.data.bestUrl
import com.youtubeclone.app.data.plain
import com.youtubeclone.app.data.Entry
import com.youtubeclone.app.data.VideoItem
import com.youtubeclone.app.data.toVideoItem
import com.youtubeclone.app.player.Playback
import com.youtubeclone.app.player.PlayerController
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

@Composable
fun WatchScreen(nav: Nav) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val cs = MaterialTheme.colorScheme
    val info = PlayerController.info
    val fullscreen = UiFlags.fullscreen
    val inPip = UiFlags.inPip
    val compact = fullscreen || inPip

    BackHandler {
        if (UiFlags.fullscreen) UiFlags.fullscreen = false else PlayerController.expanded = false
    }

    // Fullscreen: landscape + system bars chhupao
    DisposableEffect(fullscreen) {
        activity?.let { a ->
            val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
            if (fullscreen) {
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            UiFlags.fullscreen = false
            activity?.let { a ->
                a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(a.window, a.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Gesture ka chhota message (+10s, volume, brightness)
    var hud by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }
    val showHud: (String) -> Unit = { hud = it }

    Surface(Modifier.fillMaxSize(), color = cs.background, contentColor = cs.onBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (compact) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()),
        ) {
            Box(
                if (compact) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            ) {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            player = PlayerController.exo
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowRewindButton(false)
                            setShowFastForwardButton(false)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setFullscreenButtonClickListener { UiFlags.fullscreen = it }

                            // YouTube jaise gestures: double-tap seek, left swipe = brightness, right swipe = volume
                            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            var volF = 0f
                            var brF = 0.5f
                            var mode = 0 // 1 = volume, 2 = brightness, 3 = ignore
                            val detector = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDown(e: MotionEvent): Boolean {
                                    mode = 0
                                    volF = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    val b = activity?.window?.attributes?.screenBrightness ?: -1f
                                    brF = if (b < 0f) 0.5f else b
                                    return true
                                }

                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    if (isControllerFullyVisible) hideController() else showController()
                                    return true
                                }

                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    val p = PlayerController.exo
                                    when {
                                        e.x < width / 3f -> {
                                            p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                                            showHud("⏪ -10s")
                                        }
                                        e.x > width * 2f / 3f -> {
                                            p.seekTo(p.currentPosition + 10_000)
                                            showHud("+10s ⏩")
                                        }
                                        else -> if (p.isPlaying) p.pause() else p.play()
                                    }
                                    return true
                                }

                                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                                    if (e1 == null) return false
                                    if (mode == 0) {
                                        mode = if (abs(dy) > abs(dx) * 1.5f) {
                                            if (e1.x < width / 2f) 2 else 1
                                        } else if (abs(dx) > abs(dy)) 3 else 0
                                    }
                                    when (mode) {
                                        1 -> {
                                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            volF = (volF + dy / height * max * 1.6f).coerceIn(0f, max.toFloat())
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, volF.roundToInt(), 0)
                                            showHud("🔊 ${(volF / max * 100).roundToInt()}%")
                                        }
                                        2 -> {
                                            brF = (brF + dy / height * 1.4f).coerceIn(0.02f, 1f)
                                            activity?.window?.let { w ->
                                                val lp = w.attributes
                                                lp.screenBrightness = brF
                                                w.attributes = lp
                                            }
                                            showHud("🔆 ${(brF * 100).roundToInt()}%")
                                        }
                                    }
                                    return mode == 1 || mode == 2
                                }
                            })
                            setOnTouchListener { _, ev ->
                                detector.onTouchEvent(ev)
                                true
                            }
                        }
                    },
                    update = { it.useController = !inPip },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )

                // YouTube jaisa "video niche karo" (minimize) arrow
                if (!compact) {
                    IconButton(
                        onClick = { PlayerController.expanded = false },
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Minimize", Modifier.size(34.dp), tint = Color.White)
                    }
                }

                // Settings (gear) icon
                if (!compact && !inPip && info != null) {
                    var settings by remember { mutableStateOf(false) }
                    IconButton(onClick = { settings = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Outlined.Settings, "Settings", Modifier.size(26.dp), tint = Color.White)
                    }
                    if (settings) PlayerSettingsSheet(info) { settings = false }
                }

                hud?.let { t ->
                    Text(
                        t,
                        Modifier
                            .align(Alignment.Center)
                            .background(Color(0x99000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }

                if (PlayerController.loading && info == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
                val err = PlayerController.error
                if (err != null && !inPip) {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(err, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { PlayerController.retry() }) { Text("Retry") }
                    }
                }
            }

            if (!compact && info != null) {
                Details(info, nav, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Details(info: StreamInfo, nav: Nav, modifier: Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var descOpen by remember(info) { mutableStateOf(false) }
    var showDownload by remember(info) { mutableStateOf(false) }
    var qualityMenu by remember(info) { mutableStateOf(false) }
    var commentsOpen by remember(info) { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var dislikes by remember(info) { mutableStateOf<Long?>(null) }
    var comments by remember(info) { mutableStateOf<List<CommentItem>?>(null) }

    val me = remember(info) { info.toVideoItem() }
    val related = remember(info) { Repo.related(info) }

    // Related chips: All / More from this story / From <channel>
    var relTab by remember(info) { mutableStateOf(0) }
    var storyList by remember(info) { mutableStateOf<List<VideoItem>?>(null) }
    var chanList by remember(info) { mutableStateOf<List<VideoItem>?>(null) }
    LaunchedEffect(info, relTab) {
        try {
            if (relTab == 1 && storyList == null) {
                val q = info.name.split("|", "-", "–", "•").first().trim().split(" ").take(7).joinToString(" ")
                storyList = withContext(Dispatchers.IO) {
                    Repo.search(q).items.filterIsInstance<Entry.Video>().map { it.v }
                        .filter { it.url != info.url && it.duration > 60 }
                }
            }
            if (relTab == 2 && chanList == null) {
                chanList = withContext(Dispatchers.IO) {
                    Repo.channel(info.uploaderUrl ?: "").videos.filter { it.url != info.url }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (relTab == 1) storyList = emptyList() else chanList = emptyList()
        }
    }
    val shownRelated = when (relTab) { 1 -> storyList; 2 -> chanList; else -> related } ?: emptyList()
    val qualities = remember(info) { Playback.qualities(info) }
    val liked = Store.liked.any { it.url == me.url }
    val saved = Store.watchLater.any { it.url == me.url }
    val subscribed = Store.isSubscribed(info.uploaderUrl ?: "")

    LaunchedEffect(info) {
        dislikes = withContext(Dispatchers.IO) { Extras.dislikes(info.id) }
    }

    LaunchedEffect(commentsOpen) {
        if (commentsOpen && comments == null) {
            comments = withContext(Dispatchers.IO) {
                try { Repo.comments(info.url) } catch (e: Throwable) { emptyList() }
            }
        }
    }

    LazyColumn(modifier) {
        // Title + views
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(info.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(formatViews(info.viewCount), timeAgo(info.textualUploadDate ?: ""))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // Channel row
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable { nav.openChannel(info.uploaderUrl ?: "") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(info.uploaderAvatars.bestUrl(), info.uploaderName ?: "", 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                info.uploaderName ?: "",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (info.isUploaderVerified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.CheckCircle, "Verified", Modifier.height(14.dp), tint = cs.onSurfaceVariant)
                            }
                        }
                        val subs = formatSubs(info.uploaderSubscriberCount)
                        if (subs.isNotEmpty()) Text(subs, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                SubscribeButton(subscribed) {
                    Store.toggleSub(
                        ChannelItem(
                            url = info.uploaderUrl ?: "",
                            name = info.uploaderName ?: "",
                            avatar = info.uploaderAvatars.bestUrl(),
                            subs = info.uploaderSubscriberCount,
                        )
                    )
                }
            }
        }

        // Action pills
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    if (info.likeCount >= 0) compact(info.likeCount) else "Like",
                    active = liked,
                ) { Store.toggleLike(me) }
                Pill(Icons.Outlined.ThumbDown, dislikes?.let { compact(it) } ?: "Dislike") { }
                Pill(Icons.Outlined.Share, "Share") { shareUrl(ctx, info.url) }
                Pill(Icons.Outlined.Download, "Download") { showDownload = true }
                Pill(
                    if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                    "Save",
                    active = saved,
                ) { Store.toggleWatchLater(me) }
                val curSpeed = PlayerController.speed
                Pill(Icons.Outlined.Speed, if (curSpeed == 1f) "Speed" else speedLabel(curSpeed)) { speedMenu = true }
                Box {
                    val q = PlayerController.quality
                    Pill(Icons.Outlined.Settings, if (q != null) "${q}p" else "Quality") { qualityMenu = true }
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (q == null) "Auto (720p) ✓" else "Auto (720p)") },
                            onClick = { qualityMenu = false; PlayerController.changeQuality(null) },
                        )
                        qualities.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(if (q == h) "${h}p ✓" else "${h}p") },
                                onClick = { qualityMenu = false; PlayerController.changeQuality(h) },
                            )
                        }
                    }
                }
            }
        }

        // Description
        item {
            val desc = remember(info) { plain(info.description) }
            if (desc.isNotBlank()) {
                Surface(
                    onClick = { descOpen = !descOpen },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = cs.surfaceVariant,
                    contentColor = cs.onBackground,
                ) {
                    Text(
                        desc,
                        Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        maxLines = if (descOpen) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Comments
        item {
            Surface(
                onClick = { commentsOpen = !commentsOpen },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = cs.surfaceVariant,
                contentColor = cs.onBackground,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Comments", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (commentsOpen) "Hide" else "Show", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        if (commentsOpen) {
            val list = comments
            if (list == null) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtRed) } }
            } else if (list.isEmpty()) {
                item { Text("Comments nahi mile (ya band hain)", Modifier.padding(16.dp), color = cs.onSurfaceVariant, fontSize = 13.sp) }
            } else {
                items(list) { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Avatar(null, c.author, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                listOf(c.author, c.date).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant,
                            )
                            Text(c.text, fontSize = 14.sp)
                            if (c.likes.isNotBlank()) {
                                Text("👍 ${c.likes}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Related videos
        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { YtChip("All", relTab == 0) { relTab = 0 } }
                item { YtChip("More from this story", relTab == 1) { relTab = 1 } }
                item { YtChip("From ${info.uploaderName ?: "channel"}", relTab == 2) { relTab = 2 } }
            }
            Spacer(Modifier.height(10.dp))
            if (relTab != 0 && (if (relTab == 1) storyList else chanList) == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        items(shownRelated) { v ->
            VideoCard(
                v,
                onClick = { PlayerController.open(v.url) },
                onChannel = { nav.openChannel(v.uploaderUrl) },
            )
        }
    }

    if (showDownload) DownloadDialog(info) { showDownload = false }
    if (speedMenu) SpeedDialog { speedMenu = false }
}
