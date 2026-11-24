package com.youtubeclone.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.data.ChannelItem
import com.youtubeclone.app.data.Entry
import com.youtubeclone.app.data.Repo
import com.youtubeclone.app.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

@Composable
fun SearchScreen(nav: Nav) {
    var q by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var next by remember { mutableStateOf<Page?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme

    fun run(text: String) {
        if (text.isBlank()) return
        submitted = text
        suggestions = emptyList()
        keyboard?.hide()
        loading = true
        error = null
        results = emptyList()
        next = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Repo.search(text) }
                results = r.items
                next = r.next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    // Typing suggestions (250ms debounce)
    LaunchedEffect(q) {
        if (q.isBlank() || q == submitted) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = withContext(Dispatchers.IO) { Repo.suggestions(q) }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    // Infinite scroll
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            results.isNotEmpty() && last >= results.size - 3
        }
    }
    LaunchedEffect(nearEnd, next) {
        val page = next
        val query = submitted
        if (nearEnd && page != null && query != null && !loadingMore) {
            loadingMore = true
            try {
                val r = withContext(Dispatchers.IO) { Repo.searchMore(query, page) }
                results = results + r.items
                next = r.next
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                next = null
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("Search YouTube Clone") },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(q) }),
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = ""; submitted = null; results = emptyList() }) {
                            Icon(Icons.Filled.Close, "Clear")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant,
                    unfocusedContainerColor = cs.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                suggestions.isNotEmpty() && q != submitted -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(suggestions) { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { q = s; run(s) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Search, null, tint = cs.onSurfaceVariant)
                                Spacer(Modifier.width(16.dp))
                                Text(s, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Outlined.NorthWest, null, tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                loading -> LoadingView()
                error != null -> ErrorView(error ?: "") { submitted?.let { run(it) } }
                submitted != null && results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Kuch nahi mila", color = cs.onSurfaceVariant) }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(results) { e ->
                            when (e) {
                                is Entry.Video -> VideoCard(
                                    e.v,
                                    onClick = { PlayerController.open(e.v.url) },
                                    onChannel = { nav.openChannel(e.v.uploaderUrl) },
                                )
                                is Entry.Channel -> ChannelRow(e.c) { nav.openChannel(e.c.url) }
                            }
                        }
                        if (loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = YtRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelRow(c: ChannelItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.avatar, c.name, 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = formatSubs(c.subs)
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.desc.isNotBlank()) {
                Text(
                    c.desc,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
