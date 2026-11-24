@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.player

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.youtubeclone.app.DownloaderImpl
import com.youtubeclone.app.data.bestUrl
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

enum class DlKind(val label: String) { VIDEO("Video"), AUDIO("Audio"), SUBTITLES("Subtitles") }

/** Download dialog ki ek line: kya download hoga, kitni files, merge karna hai ya nahi. */
data class DlOption(
    val kind: DlKind,
    val label: String,
    val urls: List<String>,
    val merge: Boolean,
    val ext: String,
    val mime: String,
    val rank: Int,
)

/** StreamInfo se ExoPlayer ka MediaSource banata hai (quality choose karna, audio+video merge karna). */
object Playback {

    private val UA = DownloaderImpl.USER_AGENT

    private fun heightOf(s: VideoStream): Int =
        Regex("(\\d+)p").find(s.resolution ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun List<VideoStream>.prog() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
    private fun List<AudioStream>.progA() = filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

    /** Available video heights, bade se chhote. */
    fun qualities(info: StreamInfo): List<Int> =
        (info.videoStreams.prog() + info.videoOnlyStreams.prog())
            .map { heightOf(it) }.filter { it > 0 }.distinct().sortedDescending()

    private fun defaultHeight(q: List<Int>): Int? = q.firstOrNull { it <= 720 } ?: q.lastOrNull()

    private fun dataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        return androidx.media3.datasource.DataSource.Factory { ChunkedDataSource(http.createDataSource()) }
    }

    /** Subtitles wale (captions) streams, jo seedhe URL se chalte hain. */
    fun captions(info: StreamInfo): List<org.schabi.newpipe.extractor.stream.SubtitlesStream> =
        info.subtitles.filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP }

    fun source(info: StreamInfo, height: Int?, caption: org.schabi.newpipe.extractor.stream.SubtitlesStream? = null): MediaSource {
        val base = baseSource(info, height)
        if (caption == null) return base
        val mime = when (caption.format) {
            MediaFormat.TTML -> androidx.media3.common.MimeTypes.APPLICATION_TTML
            else -> androidx.media3.common.MimeTypes.TEXT_VTT
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(Uri.parse(caption.content))
            .setMimeType(mime)
            .setLanguage(caption.languageTag)
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()
        val subSrc = androidx.media3.exoplayer.source.SingleSampleMediaSource.Factory(dataSourceFactory())
            .createMediaSource(sub, androidx.media3.common.C.TIME_UNSET)
        return MergingMediaSource(base, subSrc)
    }

    private fun baseSource(info: StreamInfo, height: Int?): MediaSource {
        val df = dataSourceFactory()
        val meta = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            .setArtworkUri(info.thumbnails.bestUrl().takeIf { it.isNotEmpty() }?.let { Uri.parse(it) })
            .build()

        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaMetadata(meta).build()
        fun progressive(url: String): MediaSource = ProgressiveMediaSource.Factory(df).createMediaSource(item(url))

        // Live stream
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val hls = info.hlsUrl
        if (live && !hls.isNullOrEmpty()) {
            return HlsMediaSource.Factory(df).createMediaSource(item(hls))
        }

        val muxed = info.videoStreams.prog()
        val videoOnly = info.videoOnlyStreams.prog()
        val audio = info.audioStreams.progA()
        val bestAudio = audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: audio.maxByOrNull { it.averageBitrate }

        val target = height ?: defaultHeight(qualities(info))
        if (target != null) {
            // 1) Audio+video wali ek hi file
            muxed.firstOrNull { heightOf(it) == target }?.let { return progressive(it.content) }

            // 2) Alag video + alag audio, merge karke
            val v = videoOnly.filter { heightOf(it) == target }
                .let { list -> list.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: list.firstOrNull() }
            if (v != null) {
                val videoSrc = progressive(v.content)
                return if (bestAudio != null) {
                    MergingMediaSource(videoSrc, progressive(bestAudio.content))
                } else videoSrc
            }
        }

        // 3) Fallbacks
        muxed.maxByOrNull { heightOf(it) }?.let { return progressive(it.content) }
        bestAudio?.let { return progressive(it.content) }
        val dash = info.dashMpdUrl
        if (!dash.isNullOrEmpty()) return DashMediaSource.Factory(df).createMediaSource(item(dash))
        if (!hls.isNullOrEmpty()) return HlsMediaSource.Factory(df).createMediaSource(item(hls))
        throw IllegalStateException("Is video ka koi playable stream nahi mila")
    }

    // ---------- Downloads ----------

    fun safeName(s: String): String =
        s.replace(Regex("[^\\p{L}\\p{N} _.()\\-]"), "").trim().take(80).ifEmpty { "video" }

    /** NewPipe jaisi list: har quality, audio formats, subtitles. */
    fun downloadOptions(info: StreamInfo): List<DlOption> {
        val out = mutableListOf<DlOption>()
        val audio = info.audioStreams.progA()
        val bestM4a = audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
        val mergedHeights = mutableSetOf<Int>()

        // Alag video + alag audio -> ek mp4 (1080p tak): H.264 video + AAC audio ko merge kar sakte hain
        if (bestM4a != null) {
            val byHeight = info.videoOnlyStreams.prog()
                .filter { it.format == MediaFormat.MPEG_4 && (it.codec?.startsWith("avc1") ?: true) }
                .groupBy { heightOf(it) }
                .filterKeys { it > 0 }
            for ((h, list) in byHeight) {
                val s = list.first()
                mergedHeights += h
                out += DlOption(
                    DlKind.VIDEO, "${s.resolution} · MP4 · video + audio",
                    listOf(s.content, bestM4a.content), true, "mp4", "video/mp4", h,
                )
            }
        }

        // Pehle se audio wali single files
        info.videoStreams.prog().forEach { s ->
            val h = heightOf(s)
            if (h in mergedHeights) return@forEach
            out += DlOption(
                DlKind.VIDEO, "${s.resolution} · ${s.format?.name ?: "MP4"} · video + audio",
                listOf(s.content), false, s.format?.suffix ?: "mp4", s.format?.mimeType ?: "video/mp4", h,
            )
        }

        audio.forEach { a ->
            out += DlOption(
                DlKind.AUDIO, "${a.averageBitrate} kbps · ${a.format?.name ?: "Audio"}",
                listOf(a.content), false, a.format?.suffix ?: "m4a", a.format?.mimeType ?: "audio/mp4", a.averageBitrate,
            )
        }

        info.subtitles.forEach { st ->
            out += DlOption(
                DlKind.SUBTITLES,
                "${st.languageTag}${if (st.isAutoGenerated) " (auto)" else ""} · ${st.format?.name ?: "subtitle"}",
                listOf(st.content), false, st.format?.suffix ?: "vtt", st.format?.mimeType ?: "text/vtt", 0,
            )
        }

        return out.sortedWith(compareBy<DlOption>({ it.kind.ordinal }, { -it.rank }))
    }
}
