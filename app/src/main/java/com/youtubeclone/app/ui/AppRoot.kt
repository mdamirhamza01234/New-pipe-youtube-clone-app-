package com.youtubeclone.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.data.bestUrl
import com.youtubeclone.app.player.PlayerController

@Composable
fun AppRoot() {
    val nav = remember { Nav() }
    val cs = MaterialTheme.colorScheme
    val homeList = rememberLazyListState()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = nav.stack.isNotEmpty() && !PlayerController.expanded) { nav.pop() }

    Surface(Modifier.fillMaxSize(), color = cs.background, contentColor = cs.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = cs.background,
                contentColor = cs.onBackground,
                bottomBar = {
                    Column {
                        if (PlayerController.url != null && !PlayerController.expanded) {
                            MiniPlayer(
                                if (nav.stack.isEmpty()) Modifier else Modifier.navigationBarsPadding(),
                            )
                        }
                        if (nav.stack.isEmpty()) BottomBar(nav)
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (val top = nav.stack.lastOrNull()) {
                        null -> when (nav.tab) {
                            0 -> HomeScreen(nav, homeList)
                            1 -> SubscriptionsScreen(nav)
                            else -> LibraryScreen(nav)
                        }
                        is Screen.Search -> SearchScreen(nav)
                        is Screen.Channel -> ChannelScreen(top.url, nav)
                        is Screen.VideoList -> VideoListScreen(top.kind, nav)
                    }
                }
            }

            if (PlayerController.url != null && PlayerController.expanded) {
                WatchScreen(nav)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: Nav) {
    val cs = MaterialTheme.colorScheme
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedIconColor = cs.onBackground,
        selectedTextColor = cs.onBackground,
        unselectedIconColor = cs.onBackground,
        unselectedTextColor = cs.onBackground,
    )
    NavigationBar(containerColor = cs.background, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = nav.tab == 0,
            onClick = { nav.tab = 0 },
            icon = { Icon(if (nav.tab == 0) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
            label = { Text("Home", fontSize = 10.sp) },
            colors = colors,
        )
        NavigationBarItem(
            selected = nav.tab == 1,
            onClick = { nav.tab = 1 },
            icon = { Icon(if (nav.tab == 1) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions, "Subscriptions") },
            label = { Text("Subscriptions", fontSize = 10.sp) },
            colors = colors,
        )
        NavigationBarItem(
            selected = nav.tab == 2,
            onClick = { nav.tab = 2 },
            icon = { Icon(if (nav.tab == 2) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle, "You") },
            label = { Text("You", fontSize = 10.sp) },
            colors = colors,
        )
    }
}

/** Video minimize karne par neeche dikhne wala mini player. */
@Composable
private fun MiniPlayer(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val info = PlayerController.info
    Surface(
        onClick = { PlayerController.expanded = true },
        modifier = modifier.fillMaxWidth(),
        color = cs.surfaceVariant,
        contentColor = cs.onBackground,
    ) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(info?.thumbnails?.bestUrl() ?: "", Modifier.width(100.dp).height(56.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                info?.name ?: "Loading…",
                Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = {
                if (PlayerController.isPlaying) PlayerController.exo.pause() else PlayerController.exo.play()
            }) {
                Icon(
                    if (PlayerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play/Pause",
                )
            }
            IconButton(onClick = { PlayerController.close() }) { Icon(Icons.Filled.Close, "Close") }
        }
    }
}
