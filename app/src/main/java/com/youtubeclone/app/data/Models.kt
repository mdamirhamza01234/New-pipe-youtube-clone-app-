package com.youtubeclone.app.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.Page

data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String = "",
    val uploaderUrl: String = "",
    val uploaderAvatar: String = "",
    val thumb: String = "",
    val duration: Long = 0,
    val views: Long = -1,
    val uploaded: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("uploader", uploader)
        .put("uploaderUrl", uploaderUrl)
        .put("uploaderAvatar", uploaderAvatar)
        .put("thumb", thumb)
        .put("duration", duration)
        .put("views", views)
        .put("uploaded", uploaded)

    companion object {
        fun fromJson(o: JSONObject) = VideoItem(
            url = o.getString("url"),
            title = o.optString("title"),
            uploader = o.optString("uploader"),
            uploaderUrl = o.optString("uploaderUrl"),
            uploaderAvatar = o.optString("uploaderAvatar"),
            thumb = o.optString("thumb"),
            duration = o.optLong("duration"),
            views = o.optLong("views", -1),
            uploaded = o.optString("uploaded"),
        )
    }
}

data class ChannelItem(
    val url: String,
    val name: String,
    val avatar: String = "",
    val subs: Long = -1,
    val desc: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("name", name)
        .put("avatar", avatar)
        .put("subs", subs)
        .put("desc", desc)

    companion object {
        fun fromJson(o: JSONObject) = ChannelItem(
            url = o.getString("url"),
            name = o.optString("name"),
            avatar = o.optString("avatar"),
            subs = o.optLong("subs", -1),
            desc = o.optString("desc"),
        )
    }
}

data class CommentItem(
    val author: String,
    val text: String,
    val likes: String,
    val date: String,
)

sealed interface Entry {
    data class Video(val v: VideoItem) : Entry
    data class Channel(val c: ChannelItem) : Entry
}

class Paged(val items: List<Entry>, val next: Page?)

class ChannelData(val channel: ChannelItem, val banner: String, val videos: List<VideoItem>)
