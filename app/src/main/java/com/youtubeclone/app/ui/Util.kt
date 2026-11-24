package com.youtubeclone.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.youtubeclone.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------- Loading state ----------

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val msg: String) : UiState<Nothing>
    data class Data<out T>(val value: T) : UiState<T>
}

/** Background thread par `block` chalata hai; `key` badalne par dobara load hota hai. */
@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): State<UiState<T>> =
    produceState<UiState<T>>(UiState.Loading, key) {
        value = UiState.Loading
        value = try {
            UiState.Data(withContext(Dispatchers.IO) { block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            UiState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

// ---------- Global UI flags ----------

object UiFlags {
    var fullscreen by mutableStateOf(false)
    var inPip by mutableStateOf(false)
}

// ---------- Helpers ----------

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun shareUrl(ctx: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

// ---------- Formatting ----------

fun compact(n: Long): String {
    fun fmt(v: Double, suffix: String): String {
        val s = if (v >= 100) String.format(Locale.US, "%.0f", v)
        else String.format(Locale.US, "%.1f", v).removeSuffix(".0")
        return s + suffix
    }
    return when {
        n >= 1_000_000_000 -> fmt(n / 1e9, "B")
        n >= 1_000_000 -> fmt(n / 1e6, "M")
        n >= 1_000 -> fmt(n / 1e3, "K")
        else -> n.toString()
    }
}

fun formatViews(n: Long) = if (n < 0) "" else "${compact(n)} views"

fun formatSubs(n: Long) = if (n < 0) "" else "${compact(n)} subscribers"

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun meta(v: VideoItem): String =
    listOf(v.uploader, formatViews(v.views), v.uploaded).filter { it.isNotBlank() }.joinToString(" · ")
