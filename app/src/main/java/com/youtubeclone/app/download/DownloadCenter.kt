package com.youtubeclone.app.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.youtubeclone.app.DownloaderImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class DlStatus { QUEUED, RUNNING, PAUSED, MERGING, DONE, FAILED }

class DlItem(
    val id: Long,
    val title: String,
    val fileName: String,
    val mime: String,
    val urls: List<String>,
    val merge: Boolean,
) {
    var status by mutableStateOf(DlStatus.QUEUED)
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var uri by mutableStateOf<String?>(null)
    var job: Job? = null
}

/** Apna downloader: chunked Range requests, pause/resume, video+audio merge, Downloads folder mein save. */
object DownloadCenter {
    private const val CHUNK = 6L * 1024 * 1024

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client get() = DownloaderImpl.instance.client

    val items = mutableStateListOf<DlItem>()

    val activeCount: Int
        get() = items.count { it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED || it.status == DlStatus.MERGING }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(sp.getString("done", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = DlItem(o.getLong("id"), o.optString("title"), o.optString("file"), o.optString("mime"), emptyList(), false)
                d.status = DlStatus.DONE
                d.uri = o.optString("uri")
                d.total = o.optLong("size")
                d.done = d.total
                items.add(d)
            }
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        val arr = JSONArray()
        items.filter { it.status == DlStatus.DONE && it.uri != null }.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("file", it.fileName)
                    .put("mime", it.mime).put("uri", it.uri).put("size", it.total)
            )
        }
        sp.edit().putString("done", arr.toString()).apply()
    }

    // ---------------------------------------------------------------- public actions

    fun enqueue(title: String, fileName: String, mime: String, urls: List<String>, merge: Boolean) {
        val item = DlItem(System.currentTimeMillis(), title, fileName, mime, urls, merge)
        items.add(0, item)
        start(item)
    }

    fun pause(item: DlItem) {
        if (item.status == DlStatus.RUNNING || item.status == DlStatus.QUEUED) {
            item.status = DlStatus.PAUSED
            item.job?.cancel()
        }
    }

    fun resume(item: DlItem) {
        if ((item.status == DlStatus.PAUSED || item.status == DlStatus.FAILED) && item.urls.isNotEmpty()) start(item)
    }

    /** Chal rahe / ruke hue download ko hatao (adhi file bhi delete). */
    fun cancel(item: DlItem) {
        item.job?.cancel()
        scope.launch { partFiles(item).forEach { it.delete() } }
        items.remove(item)
    }

    /** Poora hua download: file bhi Downloads se delete. */
    fun delete(item: DlItem) {
        try {
            item.uri?.let { app.contentResolver.delete(Uri.parse(it), null, null) }
        } catch (_: Exception) {
        }
        items.remove(item)
        persist()
    }

    fun open(ctx: Context, item: DlItem) {
        val u = item.uri ?: return
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(u), item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Is file ko kholne wala app nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- engine

    private fun dir() = File(app.cacheDir, "dl").apply { mkdirs() }

    private fun partFiles(item: DlItem): List<File> =
        item.urls.indices.map { File(dir(), "${item.id}_$it.part") }

    private fun start(item: DlItem) {
        item.status = DlStatus.RUNNING
        item.error = null
        DownloadService.start(app)
        item.job = scope.launch {
            try {
                run(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                item.status = DlStatus.FAILED
                item.error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    private suspend fun run(item: DlItem) {
        val parts = partFiles(item)
        val totals = item.urls.map { probe(it) }
        item.total = totals.sum()

        var doneTotal = parts.sumOf { it.length() }
        item.done = doneTotal
        var lastUi = 0L

        for (i in parts.indices) {
            downloadPart(item.urls[i], parts[i], totals[i]) { n ->
                doneTotal += n
                val now = SystemClock.elapsedRealtime()
                if (now - lastUi > 250) {
                    item.done = doneTotal
                    lastUi = now
                }
            }
        }
        item.done = doneTotal

        val out: File
        if (item.merge && parts.size == 2) {
            item.status = DlStatus.MERGING
            out = File(dir(), "${item.id}_merged.mp4")
            Muxer.merge(parts[0], parts[1], out)
        } else {
            out = parts[0]
        }

        val uri = saveToDownloads(out, item.fileName, item.mime)
        item.total = out.length()
        item.done = item.total
        item.uri = uri.toString()
        parts.forEach { it.delete() }
        out.delete()
        item.status = DlStatus.DONE
        persist()
    }

    /** File ka size pata karo (Range 0-1 request se). */
    private fun probe(url: String): Long = try {
        val req = Request.Builder().url(url)
            .header("User-Agent", DownloaderImpl.USER_AGENT)
            .header("Range", "bytes=0-1")
            .build()
        client.newCall(req).execute().use { r ->
            r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: (if (r.code == 200) r.body?.contentLength()?.takeIf { it > 0 } else null)
                ?: 0L
        }
    } catch (e: Exception) {
        0L
    }

    private suspend fun downloadPart(url: String, file: File, knownTotal: Long, onBytes: (Long) -> Unit) {
        var total = knownTotal
        var offset = file.length()
        if (total > 0 && offset >= total) return
        val buf = ByteArray(64 * 1024)

        while (true) {
            currentCoroutineContext().ensureActive()
            val req = Request.Builder().url(url)
                .header("User-Agent", DownloaderImpl.USER_AGENT)
                .header("Range", "bytes=$offset-${offset + CHUNK - 1}")
                .build()
            val resp = client.newCall(req).execute()
            try {
                when (resp.code) {
                    416 -> return
                    206 -> {
                        resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { total = it }
                        val read = copyBody(resp, file, true, buf, onBytes)
                        offset += read
                        if (read == 0L || read < CHUNK || (total > 0 && offset >= total)) return
                    }
                    200 -> {
                        // Server ne Range ignore kiya: shuru se poori file lo
                        if (offset > 0) {
                            onBytes(-offset)
                            file.delete()
                        }
                        copyBody(resp, file, false, buf, onBytes)
                        return
                    }
                    else -> throw IOException("Server error: HTTP ${resp.code}")
                }
            } finally {
                resp.close()
            }
        }
    }

    private suspend fun copyBody(resp: Response, file: File, append: Boolean, buf: ByteArray, onBytes: (Long) -> Unit): Long {
        val body = resp.body ?: throw IOException("Khaali response")
        var sum = 0L
        FileOutputStream(file, append).use { out ->
            body.byteStream().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sum += n
                    onBytes(n.toLong())
                }
            }
        }
        return sum
    }

    private fun saveToDownloads(src: File, name: String, mime: String): Uri {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/YouTube Clone")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: throw IOException("Downloads folder mein file nahi ban saki")
        resolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
            ?: throw IOException("Downloads folder mein likh nahi paye")
        cv.clear()
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, cv, null, null)
        return uri
    }
}
