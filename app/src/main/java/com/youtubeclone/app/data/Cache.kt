package com.youtubeclone.app.data

import org.schabi.newpipe.extractor.stream.StreamInfo

object Cache {
    val home = HashMap<String, List<VideoItem>>()

    private val streams = LinkedHashMap<String, Pair<Long, StreamInfo>>()

    /**
     * Stream URLs kuch ghante mein expire ho jaate hain, isliye sirf 20 minute cache.
     * Network call lock ke bahar hai, taaki prefetch (Shorts) dusre loads ko na roke.
     */
    fun stream(url: String): StreamInfo {
        val now = System.currentTimeMillis()
        synchronized(this) {
            streams[url]?.let { if (now - it.first < 20 * 60_000) return it.second }
        }
        val info = Repo.stream(url)
        synchronized(this) {
            streams[url] = now to info
            while (streams.size > 25) streams.remove(streams.keys.first())
        }
        return info
    }
}
