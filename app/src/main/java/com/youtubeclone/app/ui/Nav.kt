package com.youtubeclone.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.youtubeclone.app.player.PlayerController

enum class ListKind(val title: String) {
    HISTORY("History"),
    WATCH_LATER("Watch later"),
    LIKED("Liked videos"),
}

sealed interface Screen {
    data object Search : Screen
    data class Channel(val url: String) : Screen
    data class VideoList(val kind: ListKind) : Screen
    data object Downloads : Screen
}

class Nav {
    /** 0 = Home, 1 = Shorts, 2 = Subscriptions, 3 = You */
    var tab by mutableIntStateOf(0)

    /** Khaali = tab screens dikhte hain. */
    val stack = mutableStateListOf<Screen>()

    fun push(s: Screen) { stack.add(s) }

    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    fun openChannel(url: String) {
        if (url.isBlank()) return
        PlayerController.expanded = false
        push(Screen.Channel(url))
    }
}
