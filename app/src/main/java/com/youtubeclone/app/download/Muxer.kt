package com.youtubeclone.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Alag video (H.264 mp4) aur alag audio (AAC m4a) ko ek mp4 file mein jodta hai. */
object Muxer {

    fun merge(video: File, audio: File, out: File) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            vEx.setDataSource(video.absolutePath)
            aEx.setDataSource(audio.absolutePath)

            val vTrack = findTrack(vEx, "video/")
            val aTrack = findTrack(aEx, "audio/")
            vEx.selectTrack(vTrack)
            aEx.selectTrack(aTrack)

            val vIdx = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val aIdx = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()

            val buf = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            // Samples ko time ke hisaab se baari-baari likho (interleave), taaki file smoothly chale
            while (true) {
                val vt = vEx.sampleTime
                val at = aEx.sampleTime
                if (vt < 0 && at < 0) break
                val useVideo = at < 0 || (vt in 0..at)
                val ex = if (useVideo) vEx else aEx
                val idx = if (useVideo) vIdx else aIdx

                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    ex.advance()
                    continue
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(idx, buf, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        throw IllegalStateException("File mein $prefix track nahi mila")
    }
}
