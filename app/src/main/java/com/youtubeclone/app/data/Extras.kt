package com.youtubeclone.app.data

import com.youtubeclone.app.DownloaderImpl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** SponsorBlock (sponsor segments skip) aur Return YouTube Dislike (dislike count). */
object Extras {
    private val client get() = DownloaderImpl.instance.client

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", DownloaderImpl.USER_AGENT).build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) {
        null
    }

    /** Skip karne wale segments (start, end) milliseconds mein. */
    fun sponsorSegments(videoId: String): List<Pair<Long, Long>> {
        val cats = URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\"]", "UTF-8")
        val body = get("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$cats")
            ?: return emptyList()
        return try {
            val arr = JSONArray(body)
            val out = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                if (o.optString("actionType", "skip") != "skip") continue
                val seg = o.getJSONArray("segment")
                out += (seg.getDouble(0) * 1000).toLong() to (seg.getDouble(1) * 1000).toLong()
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun dislikes(videoId: String): Long? {
        val body = get("https://returnyoutubedislikeapi.com/votes?videoId=$videoId") ?: return null
        return try {
            JSONObject(body).optLong("dislikes", -1).takeIf { it >= 0 }
        } catch (e: Exception) {
            null
        }
    }
}
