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

data class DownloadOption(val label: String, val url: String, val file: String)

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

    private fun dataSourceFactory() = DefaultHttpDataSource.Factory()
        .setUserAgent(UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    fun source(info: StreamInfo, height: Int?): MediaSource {
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

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().take(60).ifEmpty { "video" }

    fun downloadOptions(info: StreamInfo): List<DownloadOption> {
        val base = safeName(info.name)
        val out = mutableListOf<DownloadOption>()
        info.videoStreams.prog().sortedByDescending { heightOf(it) }.forEach { s ->
            out += DownloadOption(
                "Video ${s.resolution} (${s.format?.name ?: "mp4"}, with sound)",
                s.content,
                "${base}_${s.resolution}.${s.format?.suffix ?: "mp4"}",
            )
        }
        info.audioStreams.progA().sortedByDescending { it.averageBitrate }.forEach { a ->
            out += DownloadOption(
                "Audio only ${a.averageBitrate} kbps (${a.format?.name ?: "m4a"})",
                a.content,
                "${base}_audio_${a.averageBitrate}kbps.${a.format?.suffix ?: "m4a"}",
            )
        }
        return out
    }

    fun download(ctx: Context, o: DownloadOption) {
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(o.url))
                .setTitle(o.file)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "YouTube Clone/${o.file}")
                .addRequestHeader("User-Agent", UA)
            dm.enqueue(req)
            Toast.makeText(ctx, "Download shuru: Downloads/YouTube Clone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Download fail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
