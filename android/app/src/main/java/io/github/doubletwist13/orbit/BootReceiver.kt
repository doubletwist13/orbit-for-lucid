package io.github.doubletwist13.orbit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the watch link after a reboot, once the phone is first unlocked; no need to open the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, BridgeService::class.java))
        }
    }
}
