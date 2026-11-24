package com.youtubeclone.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.Store
import com.youtubeclone.app.player.PlayerController

@Composable
fun ChannelScreen(url: String, nav: Nav) {
    var retry by remember { mutableIntStateOf(0) }
    val state by rememberLoad(url to retry) { Repo.channel(url) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        val title = (state as? UiState.Data)?.value?.channel?.name ?: ""
        SubTopBar(title, onBack = { nav.pop() })

        when (val s = state) {
            UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(s.msg) { retry++ }
            is UiState.Data -> {
                val d = s.value
                val c = d.channel
                var expanded by remember { mutableStateOf(false) }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (d.banner.isNotEmpty()) {
                        item { Thumb(d.banner, Modifier.fillMaxWidth().aspectRatio(16f / 4f)) }
                    }
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(c.avatar, c.name, 72.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val sub = formatSubs(c.subs)
                                    if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = cs.onSurfaceVariant)
                                }
                            }
                            if (c.desc.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    c.desc,
                                    fontSize = 13.sp,
                                    color = cs.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { expanded = !expanded },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            SubscribeButton(Store.isSubscribed(c.url)) { Store.toggleSub(c) }
                        }
                    }
                    if (d.videos.isEmpty()) {
                        item {
                            Text(
                                "Is channel ke videos nahi mile",
                                Modifier.padding(16.dp),
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    items(d.videos) { v ->
                        VideoCard(
                            v,
                            onClick = { PlayerController.open(v.url) },
                            onChannel = {},
                        )
                    }
                }
            }
        }
    }
}
