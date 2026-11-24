@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.youtubeclone.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * YouTube lambi (unbounded) request ko throttle kar deta hai, isliye video atakta hai.
 * NewPipe ki tarah hum file ko chhote chunks (`&range=a-b`) mein maangte hain, jisse speed full rehti hai.
 * URL mein `clen` (poori file ki length) ho tabhi chunking hoti hai; baaki sab seedha chalta hai.
 */
class ChunkedDataSource(private val upstream: DefaultHttpDataSource) : DataSource {

    private var base: DataSpec? = null
    private var chunked = false
    private var cur = 0L
    private var endAbs = 0L
    private var chunkRemaining = 0L

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val clen = uri.getQueryParameter("clen")?.toLongOrNull()
        chunked = clen != null && (uri.host ?: "").contains("googlevideo")
        if (!chunked) return upstream.open(dataSpec)
        base = dataSpec
        cur = dataSpec.position
        endAbs = if (dataSpec.length == C.LENGTH_UNSET.toLong()) clen!! else minOf(clen!!, dataSpec.position + dataSpec.length)
        chunkRemaining = 0
        openNext()
        return endAbs - dataSpec.position
    }

    private fun withRange(uri: Uri, from: Long, to: Long): Uri {
        val b = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "range" }.forEach { n ->
            uri.getQueryParameters(n).forEach { v -> b.appendQueryParameter(n, v) }
        }
        return b.appendQueryParameter("range", "$from-$to").build()
    }

    private fun openNext() {
        val spec = base!!
        val to = minOf(cur + CHUNK, endAbs) - 1
        upstream.open(
            spec.buildUpon()
                .setUri(withRange(spec.uri, cur, to))
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
        )
        chunkRemaining = to - cur + 1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (chunkRemaining == 0L) {
            if (cur >= endAbs) return C.RESULT_END_OF_INPUT
            upstream.close()
            openNext()
        }
        val n = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
        if (n == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
        cur += n
        chunkRemaining -= n
        return n
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
        base = null
    }

    companion object {
        private const val CHUNK = 2L * 1024 * 1024
    }
}
