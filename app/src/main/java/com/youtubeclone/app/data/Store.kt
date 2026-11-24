package com.youtubeclone.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/** Local storage: history, watch later, likes, subscriptions, settings. Sab phone mein hi rehta hai. */
object Store {
    private lateinit var sp: SharedPreferences

    val history = mutableStateListOf<VideoItem>()
    val watchLater = mutableStateListOf<VideoItem>()
    val liked = mutableStateListOf<VideoItem>()
    val subs = mutableStateListOf<ChannelItem>()

    var backgroundPlay by mutableStateOf(true)
        private set
    var pip by mutableStateOf(true)
        private set
    var sponsorBlock by mutableStateOf(true)
        private set
    var autoplay by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("store", Context.MODE_PRIVATE)
        history.addAll(loadVideos("history"))
        watchLater.addAll(loadVideos("watch_later"))
        liked.addAll(loadVideos("liked"))
        subs.addAll(loadChannels())
        backgroundPlay = sp.getBoolean("bg", true)
        pip = sp.getBoolean("pip", true)
        sponsorBlock = sp.getBoolean("sponsor", true)
        autoplay = sp.getBoolean("autoplay", true)
    }

    // ---------- settings ----------
    fun updateBackgroundPlay(v: Boolean) { backgroundPlay = v; sp.edit().putBoolean("bg", v).apply() }
    fun updatePip(v: Boolean) { pip = v; sp.edit().putBoolean("pip", v).apply() }
    fun updateSponsorBlock(v: Boolean) { sponsorBlock = v; sp.edit().putBoolean("sponsor", v).apply() }
    fun updateAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("autoplay", v).apply() }

    // ---------- videos ----------
    fun addHistory(v: VideoItem) {
        history.removeAll { it.url == v.url }
        history.add(0, v)
        while (history.size > 200) history.removeAt(history.lastIndex)
        saveVideos("history", history)
    }

    fun clearHistory() { history.clear(); saveVideos("history", history) }

    fun removeFromHistory(v: VideoItem) { history.removeAll { it.url == v.url }; saveVideos("history", history) }

    fun toggleWatchLater(v: VideoItem) = toggle(watchLater, "watch_later", v)
    fun toggleLike(v: VideoItem) = toggle(liked, "liked", v)

    fun isWatchLater(url: String) = watchLater.any { it.url == url }

    private fun toggle(list: MutableList<VideoItem>, key: String, v: VideoItem) {
        if (!list.removeAll { it.url == v.url }) list.add(0, v)
        saveVideos(key, list)
    }

    // ---------- subscriptions ----------
    fun isSubscribed(url: String) = subs.any { it.url == url }

    fun toggleSub(c: ChannelItem) {
        if (c.url.isBlank()) return
        if (!subs.removeAll { it.url == c.url }) subs.add(0, c)
        val arr = JSONArray()
        subs.forEach { arr.put(it.toJson()) }
        sp.edit().putString("subs", arr.toString()).apply()
    }

    // ---------- persistence ----------
    private fun saveVideos(key: String, list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private fun loadVideos(key: String): List<VideoItem> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map { VideoItem.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadChannels(): List<ChannelItem> = try {
        val arr = JSONArray(sp.getString("subs", "[]"))
        (0 until arr.length()).map { ChannelItem.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }
}
