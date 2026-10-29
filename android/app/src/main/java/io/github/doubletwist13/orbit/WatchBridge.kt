package io.github.doubletwist13.orbit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "OrbitBridge"
private const val WATCH_APP_ID = "f098eb3a-bdb6-4c15-91a5-fe6c5cb603e9"
private const val RETRY_MS = 30_000L

/** Carries watch requests to [Controller] and its replies back. Only this watch app's id is registered. */
object WatchBridge {
    private var ciq: ConnectIQ? = null
    private var wanted = false
    private val handler = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        wanted = true
        if (ciq != null) return
        val c = ConnectIQ.getInstance(ctx, ConnectIQ.IQConnectType.WIRELESS)
        ciq = c
        val controller = (ctx.applicationContext as OrbitApplication).controller
        c.initialize(ctx, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                try {
                    c.knownDevices.forEach { listen(c, controller, it) }
                    // pushes go to every known watch, connected or not; an absent one just fails. The list is read
                    // per push so a watch paired later is included, and one delivery is enough to report success.
                    Log.d(TAG, "sdk ready")
                    controller.startPush { push ->
                        val devices = c.knownDevices
                        Log.d(TAG, "pushing to ${devices.size} devices")
                        if (devices.isEmpty()) {
                            controller.pushResult(false)
                            return@startPush
                        }
                        val left = AtomicInteger(devices.size)
                        val delivered = AtomicBoolean(false)
                        val done = { if (left.decrementAndGet() == 0) controller.pushResult(delivered.get()) }
                        devices.forEach { d ->
                            try {
                                c.sendMessage(d, IQApp(WATCH_APP_ID), push) { dev, _, st ->
                                    Log.d(TAG, "push ${dev?.friendlyName} ${dev?.deviceIdentifier} $st")
                                    if (st == ConnectIQ.IQMessageStatus.SUCCESS) delivered.set(true)
                                    done()
                                }
                            } catch (e: InvalidStateException) {
                                Log.w(TAG, "push: $e")
                                done()
                            } catch (e: ServiceUnavailableException) {
                                Log.w(TAG, "push: $e")
                                done()
                            }
                        }
                    }
                } catch (e: InvalidStateException) {
                    Log.w(TAG, "devices: $e")
                } catch (e: ServiceUnavailableException) {
                    Log.w(TAG, "devices: $e")
                }
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                Log.w(TAG, "init error: $status")
                lost(ctx, controller)
            }

            override fun onSdkShutDown() {
                Log.w(TAG, "sdk shut down")
                lost(ctx, controller)
            }
        })
    }

    // the SDK is gone without us asking (Garmin Connect restarted or was updated): stop fetching for a dead link and
    // start over, as long as the service still wants the bridge
    private fun lost(ctx: Context, controller: Controller) {
        ciq = null
        controller.stopPush()
        if (wanted) handler.postDelayed({ if (wanted) start(ctx) }, RETRY_MS)
    }

    fun stop(ctx: Context) {
        wanted = false
        handler.removeCallbacksAndMessages(null)
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
