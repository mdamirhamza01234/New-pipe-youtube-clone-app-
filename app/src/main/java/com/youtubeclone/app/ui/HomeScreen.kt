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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.youtubeclone.app.data.Cache
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.player.PlayerController

private val CHIPS = listOf(
    "All", "NEET", "Physics", "Chemistry", "Biology", "NCERT",
    "PYQ", "Mock Test", "Revision", "Motivation", "Study Tips",
)

@Composable
fun HomeScreen(nav: Nav, listState: LazyListState) {
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val chip = CHIPS[sel]

    val state by rememberLoad(sel to retry) {
        Cache.home[chip] ?: Repo.home(chip).also { if (it.isNotEmpty()) Cache.home[chip] = it }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YtLogo()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { nav.push(Screen.Downloads) }) { Icon(Icons.Outlined.Download, "Downloads") }
            IconButton(onClick = { Cache.home.clear(); retry++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Outlined.Search, "Search") }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(CHIPS) { i, c -> YtChip(c, i == sel) { sel = i } }
        }
        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(s.msg) { retry++ }
            is UiState.Data -> {
                if (s.value.isEmpty()) {
                    ErrorView("Koi video nahi mila. Internet check karo.") { retry++ }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
    }
}
