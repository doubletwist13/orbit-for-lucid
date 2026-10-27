package io.github.doubletwist13.orbit

import android.content.Context
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException

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
                    devices.forEach { listen(c, controller, it) }
                    // pushes go to every watch that has this app, connected or not; an absent one just fails
                    controller.startPush { push ->
                        devices.forEach { d -> c.sendMessage(d, IQApp(WATCH_APP_ID), push) { _, _, st ->
                            Log.d(TAG, "push $st")
                            controller.pushResult(st == ConnectIQ.IQMessageStatus.SUCCESS)
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
