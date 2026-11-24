package com.youtubeclone.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.youtubeclone.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Download chalte waqt app ko zinda rakhta hai aur progress notification dikhata hai. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIF_ID, build("Download shuru ho raha hai…", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        scope.coroutineContext.cancelChildren()
        scope.launch {
            while (true) {
                val active = DownloadCenter.items.filter {
                    it.status == DlStatus.RUNNING || it.status == DlStatus.QUEUED || it.status == DlStatus.MERGING
                }
                if (active.isEmpty()) break
                val done = active.sumOf { it.done }
                val total = active.sumOf { it.total }
                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                val text = if (active.any { it.status == DlStatus.MERGING }) "Video + audio jod rahe hain…"
                else "${active.size} download chal rahe hain" + if (pct >= 0) " · $pct%" else ""
                nm.notify(NOTIF_ID, build(text, pct))
                delay(1000)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, pct: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("YouTube Clone")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
