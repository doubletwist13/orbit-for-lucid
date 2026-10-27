package io.github.doubletwist13.orbit

import android.content.Context
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "OrbitBridge"
private const val WATCH_APP_ID = "f098eb3a-bdb6-4c15-91a5-fe6c5cb603e9"

/** Carries watch requests to [Controller] and its replies back. Only this watch app's id is registered. */
object WatchBridge {
    private var ciq: ConnectIQ? = null

    fun start(ctx: Context) {
        if (ciq != null) return
        val c = ConnectIQ.getInstance(ctx, ConnectIQ.IQConnectType.WIRELESS)
        ciq = c
        val controller = (ctx.applicationContext as OrbitApplication).controller
        c.initialize(ctx, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                try {
                    val devices = c.knownDevices
                    devices.forEach { listen(c, controller, it); track(c, it) }
                    // the same registration serves both: pushes go to every watch that has this app
                    controller.startPush({ anyConnected(devices) }) { push ->
                        devices.forEach { d -> c.sendMessage(d, IQApp(WATCH_APP_ID), push) { _, _, st ->
                            Log.d(TAG, "push $st")
                        } }
                    }
                } catch (e: InvalidStateException) {
                    Log.w(TAG, "devices: $e")
                } catch (e: ServiceUnavailableException) {
                    Log.w(TAG, "devices: $e")
                }
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                Log.w(TAG, "init error: $status")
                ciq = null
            }

            override fun onSdkShutDown() {
                ciq = null
            }
        })
    }

    // the SDK only keeps a device's status current for devices we registered for, so getDeviceStatus alone stays stale
    private val status = ConcurrentHashMap<Long, IQDevice.IQDeviceStatus>()

    private fun track(c: ConnectIQ, d: IQDevice) {
        // not seeded from d.status: that is what was stale, and an unseen watch counts as connected
        Log.d(TAG, "watch listed as ${d.status}")
        try {
            c.registerForDeviceEvents(d) { dev, st ->
                Log.d(TAG, "watch $st")
                status[dev.deviceIdentifier] = st
            }
        } catch (e: InvalidStateException) {
            Log.w(TAG, "device events: $e")
        }
    }

    /** True unless every known watch is known not to be connected; an unknown status must not skip a push. */
    private fun anyConnected(devices: List<IQDevice>): Boolean {
        val seen = devices.map { status[it.deviceIdentifier] ?: IQDevice.IQDeviceStatus.UNKNOWN }
        Log.d(TAG, "watch status $seen")
        return seen.any { it == IQDevice.IQDeviceStatus.CONNECTED || it == IQDevice.IQDeviceStatus.UNKNOWN }
    }

    fun stop(ctx: Context) {
        val c = ciq ?: return
        ciq = null
        (ctx.applicationContext as OrbitApplication).controller.stopPush()
        try {
            c.shutdown(ctx)
        } catch (e: InvalidStateException) {
            Log.w(TAG, "shutdown: $e")
        }
    }

    private fun listen(c: ConnectIQ, controller: Controller, device: IQDevice) {
        val app = IQApp(WATCH_APP_ID)
        c.registerForAppEvents(device, app) { d, a, messages, _ ->
            val req = messages?.firstOrNull() as? Map<*, *> ?: return@registerForAppEvents
            controller.onWatch(req) { reply -> c.sendMessage(d, a, reply) { _, _, st -> Log.d(TAG, "reply $st") } }
        }
    }
}
