package io.github.doubletwist13.orbit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal const val BRIDGE_IDLE_TEXT = "Watch link active"

/**
 * The notification's one line: charge, lock and charging power, like "79% 🔒 ⚡7 kW". Symbols, not words, so it fits
 * the collapsed notification even at two digits of kW. The plain [BRIDGE_IDLE_TEXT] while logged out or before the
 * first read, or when the car reported none of them.
 */
internal fun notificationText(loggedIn: Boolean, s: VehicleStatus?): String {
    if (!loggedIn || s == null) return BRIDGE_IDLE_TEXT
    val parts = listOfNotNull(
        // the same plausibility rules as the watch's status (a value outside 0..100 is not a reading)
        s.socPercent.takeIf { it in 0..100 }?.let { "$it%" },
        s.locked?.let { if (it) "🔒" else "🔓" },
        if (s.chargeState != "CHARGING") null
        else if (s.chargeRateKw in MIN_RATE_KW..maxRateKw(s.model)) "⚡${Math.round(s.chargeRateKw)} kW"
        else "⚡",
    )
    return parts.joinToString(" ").ifEmpty { BRIDGE_IDLE_TEXT }
}

/**
 * Keeps the Connect IQ SDK registered so watch messages reach a backgrounded app. Makes no Lucid calls itself; the
 * background status push runs in [Controller.startPush] while this keeps the process alive. Its notification shows
 * the car's state from the last read, updated only when the text changes.
 */
class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bridge", "Watch link", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, build(BRIDGE_IDLE_TEXT), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        WatchBridge.start(applicationContext)
        val controller = (application as OrbitApplication).controller
        scope.launch {
            controller.state.map { notificationText(it.loggedIn, it.status) }.distinctUntilChanged()
                .collect { nm.notify(1, build(it)) }
        }
    }

    // On a secure lock screen Android shows the public version, so the car's state is not shown there.
    private fun build(text: String): Notification = Notification.Builder(this, "bridge")
        .setSmallIcon(R.drawable.ic_stat_orbit)
        .setContentTitle(text)
        .setOnlyAlertOnce(true)
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(
            Notification.Builder(this, "bridge").setSmallIcon(R.drawable.ic_stat_orbit)
                .setContentTitle(BRIDGE_IDLE_TEXT).build(),
        )
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    override fun onDestroy() {
        scope.cancel()
        WatchBridge.stop(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
