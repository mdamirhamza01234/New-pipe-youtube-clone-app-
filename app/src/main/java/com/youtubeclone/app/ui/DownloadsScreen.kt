package com.youtubeclone.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.download.DlItem
import com.youtubeclone.app.download.DlStatus
import com.youtubeclone.app.download.DownloadCenter
import java.util.Locale

private fun mb(b: Long) = String.format(Locale.US, "%.1f MB", b / 1048576.0)

@Composable
fun DownloadsScreen(nav: Nav) {
    val list = DownloadCenter.items.toList()
    Column(Modifier.fillMaxSize()) {
        SubTopBar("Downloads", onBack = { nav.pop() })
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Abhi koi download nahi hai", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { d -> DownloadRow(d) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DlItem) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val status = d.status

    val subtitle = when (status) {
        DlStatus.DONE -> "${mb(d.total)} · Downloads/YouTube Clone mein save"
        DlStatus.RUNNING -> if (d.total > 0) "${mb(d.done)} / ${mb(d.total)}" else "${mb(d.done)} download hua"
        DlStatus.QUEUED -> "Line mein hai…"
        DlStatus.PAUSED -> "Ruka hua · ${mb(d.done)} / ${mb(d.total)}"
        DlStatus.MERGING -> "Video aur audio jod rahe hain…"
        DlStatus.FAILED -> "Fail: ${d.error ?: "pata nahi kyun"}"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = status == DlStatus.DONE) { DownloadCenter.open(ctx, d) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.fileName, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (status == DlStatus.FAILED) cs.error else cs.onSurfaceVariant,
                )
            }
            when (status) {
                DlStatus.RUNNING, DlStatus.QUEUED -> {
                    IconButton(onClick = { DownloadCenter.pause(d) }) { Icon(Icons.Outlined.Pause, "Pause") }
                    IconButton(onClick = { DownloadCenter.cancel(d) }) { Icon(Icons.Outlined.Close, "Cancel") }
                }
                DlStatus.PAUSED, DlStatus.FAILED -> {
                    IconButton(onClick = { DownloadCenter.resume(d) }) { Icon(Icons.Outlined.PlayArrow, "Resume") }
                    IconButton(onClick = { DownloadCenter.cancel(d) }) { Icon(Icons.Outlined.Close, "Cancel") }
                }
                DlStatus.MERGING -> {}
                DlStatus.DONE -> IconButton(onClick = { DownloadCenter.delete(d) }) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
        if (status != DlStatus.DONE && status != DlStatus.FAILED) {
            val p = if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0f
            if (status == DlStatus.MERGING || d.total <= 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = YtRed)
            } else {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = YtRed)
            }
        }
    }
}
