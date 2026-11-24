package com.youtubeclone.app.data

import androidx.core.text.HtmlCompat
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Sabhi blocking calls: inhe background thread (Dispatchers.IO) se chalao. */
object Repo {

    private val yt get() = ServiceList.YouTube

    private fun StreamInfoItem.toVideo() = VideoItem(
        url = url,
        title = name,
        uploader = uploaderName ?: "",
        uploaderUrl = uploaderUrl ?: "",
        thumb = thumbnails.bestUrl(),
        duration = duration,
        views = viewCount,
        uploaded = textualUploadDate ?: "",
    )

    private fun toEntries(items: List<InfoItem>): List<Entry> = items.mapNotNull {
        when (it) {
            is StreamInfoItem -> Entry.Video(it.toVideo())
            is ChannelInfoItem -> Entry.Channel(
                ChannelItem(it.url, it.name, it.thumbnails.bestUrl(), it.subscriberCount, it.description ?: "")
            )
            else -> null
        }
    }

    private fun List<Entry>.videos(): List<VideoItem> =
        filterIsInstance<Entry.Video>().map { it.v }

    // ---------- Home ----------

    private fun trending(): List<VideoItem> = try {
        val kiosk = yt.kioskList.defaultKioskExtractor
        kiosk.fetchPage()
        kiosk.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    } catch (e: Exception) {
        emptyList()
    }

    fun home(chip: String): List<VideoItem> {
        if (chip == "All") {
            val t = trending()
            if (t.isNotEmpty()) return t
            return search("trending videos today").items.videos()
        }
        val q = if (chip == "Live") "live now" else chip
        return search(q).items.videos()
    }

    // ---------- Search ----------

    fun search(query: String): Paged {
        val handler = yt.searchQHFactory.fromQuery(query)
        val info = SearchInfo.getInfo(yt, handler)
        return Paged(toEntries(info.relatedItems), info.nextPage)
    }

    fun searchMore(query: String, page: Page): Paged {
        val handler = yt.searchQHFactory.fromQuery(query)
        val p = SearchInfo.getMoreItems(yt, handler, page)
        return Paged(toEntries(p.items), p.nextPage)
    }

    fun suggestions(query: String): List<String> = try {
        yt.suggestionExtractor.suggestionList(query)
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Video ----------

    fun stream(url: String): StreamInfo = StreamInfo.getInfo(url)

    fun related(info: StreamInfo): List<VideoItem> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }

    fun comments(videoUrl: String): List<CommentItem> =
        CommentsInfo.getInfo(videoUrl).relatedItems.map {
            CommentItem(
                author = it.uploaderName ?: "",
                text = plain(it.commentText),
                likes = it.textualLikeCount ?: "",
                date = it.textualUploadDate ?: "",
            )
        }

    // ---------- Channel ----------

    fun channel(url: String): ChannelData {
        val info = ChannelInfo.getInfo(yt, url)
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        val videos = if (tab != null) {
            try {
                ChannelTabInfo.getInfo(yt, tab).relatedItems
                    .filterIsInstance<StreamInfoItem>().map { it.toVideo() }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        return ChannelData(
            channel = ChannelItem(
                url = info.url,
                name = info.name,
                avatar = info.avatars.bestUrl(),
                subs = info.subscriberCount,
                desc = info.description ?: "",
            ),
            banner = info.banners.bestUrl(),
            videos = videos,
        )
    }
}

/** Sabse badi image ka URL (thumbnail / avatar ke liye). */
fun List<Image>.bestUrl(): String = maxByOrNull { it.height }?.url ?: ""

/** NewPipe ka Description (HTML ya plain) -> sada text. */
fun plain(d: Description?): String {
    if (d == null) return ""
    val c = d.content ?: return ""
    return if (d.type == Description.HTML) {
        HtmlCompat.fromHtml(c, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    } else c
}

fun StreamInfo.toVideoItem() = VideoItem(
    url = url,
    title = name,
    uploader = uploaderName ?: "",
    uploaderUrl = uploaderUrl ?: "",
    thumb = thumbnails.bestUrl(),
    duration = duration,
    views = viewCount,
    uploaded = textualUploadDate ?: "",
)
