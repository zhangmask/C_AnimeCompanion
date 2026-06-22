package com.companion.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class InferenceForegroundService : Service() {

    companion object {
        private const val TAG = "InferenceFgService"
        const val CHANNEL_ID = "inference_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.companion.chat.action.START_INFERENCE"
        const val ACTION_STOP = "com.companion.chat.action.STOP_INFERENCE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "推理前台服务已启动")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "推理前台服务已停止")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "推理服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持推理引擎在后台运行"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Anime Companion")
            .setContentText("AI 伴侣运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
