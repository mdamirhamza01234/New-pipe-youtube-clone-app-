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

    /** Har chip ke liye kai search queries; refresh par inme se alag-alag chune jaate hain. */
    private fun homePool(chip: String): List<String> {
        val sfx = listOf("", " lecture", " one shot", " revision", " important questions", " tricks", " 2027")
        return when (chip) {
            "All" -> listOf(
                "NEET 2027 preparation", "NEET physics lecture", "NEET chemistry lecture",
                "NEET biology lecture", "NEET strategy toppers", "NEET 2027 motivation",
                "NEET quick revision", "NEET PYQ solved", "NEET NCERT",
            )
            "NEET" -> sfx.map { "NEET$it" }
            "PYQ" -> listOf(
                "NEET PYQ solved", "NEET previous year questions physics", "NEET PYQ chemistry",
                "NEET PYQ biology", "NEET 2025 paper solution", "NEET 2024 paper solution",
            )
            "Mock Test" -> listOf(
                "NEET mock test", "NEET mock test discussion", "NEET full test paper solution", "NEET test series analysis",
            )
            "Revision" -> listOf(
                "NEET quick revision", "NEET last minute revision", "NEET one shot revision", "NEET formula revision",
            )
            "Motivation" -> listOf(
                "NEET motivation", "NEET topper story", "NEET droppers motivation", "NEET preparation tips",
            )
            "Study Tips" -> listOf(
                "NEET study strategy toppers", "NEET study plan", "NEET time table", "NEET how to study biology",
            )
            else -> sfx.map { "NEET $chip$it" }
        }
    }

    /**
     * Home feed: sirf normal videos (live aur Shorts nahi), kai searches mila kar.
     * fresh = true (refresh) par alag queries chuni jaati hain, isliye naya content aata hai.
     */
    fun home(chip: String, fresh: Boolean = false): List<VideoItem> {
        val pool = homePool(chip)
        val n = if (chip == "All") 3 else 2
        val qs = (if (fresh) pool.shuffled() else pool).take(n)
        val results = arrayOfNulls<List<VideoItem>>(qs.size)
        val threads = qs.mapIndexed { i, q ->
            Thread {
                results[i] = try { search(q).items.videos() } catch (e: Exception) { emptyList() }
            }.also { it.start() }
        }
        threads.forEach { it.join() }

        val lists = results.map { it ?: emptyList() }
        val merged = mutableListOf<VideoItem>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) lists.forEach { l -> l.getOrNull(i)?.let { merged.add(it) } }
        val out = merged.distinctBy { it.url }.filter { it.duration > 60 }
        return if (fresh) out.shuffled() else out
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
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }.filter { it.duration >= 0 }

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
