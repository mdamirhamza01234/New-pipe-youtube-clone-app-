package com.youtubeclone.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.ShortsLimiter
import com.youtubeclone.app.download.DownloadCenter
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.data.VideoItem
import com.youtubeclone.app.player.PlayerController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// ------------------------------------------------------------------ Subscriptions

@Composable
fun SubscriptionsScreen(nav: Nav) {
    val subs = Store.subs.toList()
    val urls = subs.map { it.url }
    var retry by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme

    val state by rememberLoad(urls to retry) {
        coroutineScope {
            val lists = urls.take(15).map { u ->
                async { runCatching { Repo.channel(u).videos.take(6) }.getOrDefault(emptyList()) }
            }.awaitAll()
            // Har channel se baari-baari ek video (round-robin)
            val out = mutableListOf<VideoItem>()
            for (i in 0 until 6) lists.forEach { l -> l.getOrNull(i)?.let { out.add(it) } }
            out.toList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) { YtLogo() }

        if (subs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Subscriptions, null, Modifier.height(56.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Subscriptions khaali hain", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kisi channel par Subscribe dabao, uske naye videos yahan dikhenge.",
                    textAlign = TextAlign.Center,
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(subs) { c ->
                Column(
                    Modifier.width(64.dp).clickable { nav.openChannel(c.url) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(c.avatar, c.name, 52.dp)
                    Text(c.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(s.msg) { retry++ }
            is UiState.Data -> LazyColumn(Modifier.fillMaxSize()) {
                items(s.value) { v ->
                    VideoCard(
                        v,
                        onClick = { PlayerController.open(v.url) },
                        onChannel = { nav.openChannel(v.uploaderUrl) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ You / Library

@Composable
fun LibraryScreen(nav: Nav) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(null, "You", 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("You", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${Store.subs.size} subscriptions",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { nav.push(Screen.Downloads) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Download, null)
                Spacer(Modifier.width(16.dp))
                Text("Downloads", Modifier.weight(1f), fontSize = 15.sp)
                Text("${DownloadCenter.items.size}", color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        item { Section("History", Store.history) { nav.push(Screen.VideoList(ListKind.HISTORY)) } }
        item { Section("Watch later", Store.watchLater) { nav.push(Screen.VideoList(ListKind.WATCH_LATER)) } }
        item { Section("Liked videos", Store.liked) { nav.push(Screen.VideoList(ListKind.LIKED)) } }

        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text("Settings", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                SettingRow("Background play", "Screen band ya app minimize hone par bhi audio chale", Store.backgroundPlay) {
                    Store.updateBackgroundPlay(it)
                }
                SettingRow("Picture-in-picture", "Home dabane par chhoti video window", Store.pip) {
                    Store.updatePip(it)
                }
                SettingRow("SponsorBlock", "Video ke andar ke sponsor / intro / outro hisse apne aap skip", Store.sponsorBlock) {
                    Store.updateSponsorBlock(it)
                }
                SettingRow("Autoplay", "Video khatam hone par agla video apne aap chale", Store.autoplay) {
                    Store.updateAutoplay(it)
                }
                Text(
                    "Shorts aaj: ${ShortsLimiter.todayMs / 60_000} / 120 minute · ek baar mein max 3 minute",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "YouTube Clone · NewPipeExtractor (GPLv3) se bana.\nYeh YouTube ka official app nahi hai.",
                Modifier.padding(16.dp),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, list: List<VideoItem>, onAll: () -> Unit) {
    if (list.isEmpty()) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            TextButton(onClick = onAll) { Text("View all") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.take(15)) { v ->
                Column(Modifier.width(160.dp).clickable { PlayerController.open(v.url) }) {
                    Thumb(v.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text(v.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                    Text(
                        v.uploader,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------ Full list (History / Watch later / Liked)

@Composable
fun VideoListScreen(kind: ListKind, nav: Nav) {
    val list = when (kind) {
        ListKind.HISTORY -> Store.history
        ListKind.WATCH_LATER -> Store.watchLater
        ListKind.LIKED -> Store.liked
    }
    Column(Modifier.fillMaxSize()) {
        SubTopBar(
            kind.title,
            onBack = { nav.pop() },
            actions = {
                if (kind == ListKind.HISTORY && list.isNotEmpty()) {
                    IconButton(onClick = { Store.clearHistory() }) { Icon(Icons.Outlined.Delete, "Clear history") }
                }
            },
        )
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Yahan abhi kuch nahi hai", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.toList()) { v ->
                    VideoRow(
                        v,
                        onClick = { PlayerController.open(v.url) },
                        trailing = {
                            IconButton(onClick = {
                                when (kind) {
                                    ListKind.HISTORY -> Store.removeFromHistory(v)
                                    ListKind.WATCH_LATER -> Store.toggleWatchLater(v)
                                    ListKind.LIKED -> Store.toggleLike(v)
                                }
                            }) { Icon(Icons.Outlined.Close, "Remove") }
                        },
                    )
                }
            }
        }
    }
}
