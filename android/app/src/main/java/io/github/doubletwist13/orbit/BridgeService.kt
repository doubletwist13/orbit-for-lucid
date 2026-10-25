package io.github.doubletwist13.orbit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Keeps the Connect IQ SDK registered so watch messages reach a backgrounded app. Makes no Lucid calls. */
class BridgeService : Service() {
    override fun onCreate() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("bridge", "Watch link", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "bridge")
            .setSmallIcon(R.drawable.ic_stat_orbit)
            .setContentTitle("Watch link active")
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        WatchBridge.start(applicationContext)
    }

    override fun onDestroy() {
        WatchBridge.stop(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
