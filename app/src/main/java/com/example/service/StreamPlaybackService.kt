package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StreamNotificationHelper {
    const val CHANNEL_ALERTS = "streampulse_live_alerts"
    const val CHANNEL_PLAYBACK = "streampulse_bg_playback"
    const val PLAYBACK_NOTIFICATION_ID = 4001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Live Stream & Donation Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time notifications for live broadcasts, SuperChat donations, and key activation"
            }
            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Background Stream Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent media controls for continuous background stream playback"
            }
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(playbackChannel)
        }
    }

    fun showPushAlert(context: Context, title: String, body: String) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}

data class BackgroundPlaybackState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val streamTitle: String = "",
    val hostName: String = "",
    val qualityLabel: String = "720p"
)

class StreamPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        StreamNotificationHelper.ensureChannels(this)
        when (intent?.action) {
            ACTION_STOP -> {
                _playbackState.value = BackgroundPlaybackState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                val cur = _playbackState.value
                if (cur.active) {
                    val updated = cur.copy(paused = !cur.paused)
                    _playbackState.value = updated
                    startForegroundCompat(buildNotification(updated))
                }
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "StreamPulse Live" }
                val host = intent?.getStringExtra(EXTRA_HOST).orEmpty().ifBlank { "Creator Studio" }
                val quality = intent?.getStringExtra(EXTRA_QUALITY).orEmpty().ifBlank { "720p" }
                val updated = BackgroundPlaybackState(
                    active = true,
                    paused = false,
                    streamTitle = title,
                    hostName = host,
                    qualityLabel = quality
                )
                _playbackState.value = updated
                startForegroundCompat(buildNotification(updated))
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                StreamNotificationHelper.PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(StreamNotificationHelper.PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: BackgroundPlaybackState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, StreamPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePending = PendingIntent.getService(
            this,
            11,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            12,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (state.paused) "Paused • ${state.hostName}" else "Playing in background • ${state.hostName} (${state.qualityLabel})"
        return NotificationCompat.Builder(this, StreamNotificationHelper.CHANNEL_PLAYBACK)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(state.streamTitle)
            .setContentText(statusText)
            .setOngoing(!state.paused)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (state.paused) "Resume" else "Pause",
                togglePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPending
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        _playbackState.value = BackgroundPlaybackState()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_BG_PLAYBACK"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_BG_PLAYBACK"
        const val ACTION_STOP = "com.example.action.STOP_BG_PLAYBACK"
        const val EXTRA_TITLE = "extra_stream_title"
        const val EXTRA_HOST = "extra_host_name"
        const val EXTRA_QUALITY = "extra_quality"

        private val _playbackState = MutableStateFlow(BackgroundPlaybackState())
        val playbackState: StateFlow<BackgroundPlaybackState> = _playbackState.asStateFlow()

        fun start(context: Context, title: String, host: String, quality: String) {
            val intent = Intent(context, StreamPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_QUALITY, quality)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun togglePause(context: Context) {
            val intent = Intent(context, StreamPlaybackService::class.java).apply {
                action = ACTION_TOGGLE_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
