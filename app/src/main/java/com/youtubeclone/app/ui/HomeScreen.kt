@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.youtubeclone.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.data.Cache
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.data.VideoItem
import com.youtubeclone.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CHIPS = listOf(
    "All", "NEET", "Physics", "Chemistry", "Biology", "NCERT",
    "PYQ", "Mock Test", "Revision", "Motivation", "Study Tips",
)

@Composable
fun HomeScreen(nav: Nav, listState: LazyListState) {
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val chip = CHIPS[sel]
    val scope = rememberCoroutineScope()

    var feed by remember(sel) { mutableStateOf<List<VideoItem>?>(Cache.home[chip]) }
    var error by remember(sel) { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadId by remember { mutableIntStateOf(0) }

    /** fresh = true: naye queries se naya content (pull-to-refresh / refresh button). */
    fun load(fresh: Boolean) {
        val id = ++loadId
        scope.launch {
            refreshing = true
            try {
                val r = withContext(Dispatchers.IO) { Repo.home(chip, fresh) }
                if (r.isNotEmpty()) {
                    feed = r
                    error = null
                    Cache.home[chip] = r
                    if (fresh) listState.scrollToItem(0)
                } else if (feed == null) {
                    error = "Koi video nahi mila. Internet check karo."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (feed == null) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (id == loadId) refreshing = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(sel) { if (feed == null) load(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YtLogo()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { nav.push(Screen.Downloads) }) { Icon(Icons.Outlined.Download, "Downloads") }
            IconButton(onClick = { load(true) }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Outlined.Search, "Search") }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(CHIPS) { i, c -> YtChip(c, i == sel) { sel = i } }
        }
        Spacer(Modifier.height(10.dp))

        PullToRefreshBox(
            isRefreshing = refreshing && feed != null,
            onRefresh = { load(true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            val list = feed
            when {
                list == null && error == null -> LoadingView()
                list == null -> ErrorView(error ?: "") { error = null; load(false) }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(list) { v ->
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
}
