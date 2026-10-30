package io.github.doubletwist13.orbit

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class UiState(
    val loggedIn: Boolean = false,
    val status: VehicleStatus? = null,
    val busy: String? = null,
    val message: String? = null,
    /** Fetch status every few minutes and push it to the watch (docs/protocol.md, "Pushed status"). */
    val pushOn: Boolean = true,
    /** When the watch last answered a push, and whether it was delivered; null before the first answer. */
    val pushAtMs: Long? = null,
    val pushOk: Boolean = true,
    /** The watch app's version, from its last request; null until it has sent one. */
    val watchVersion: String? = null,
)

/** A command failed in a way the watch protocol names (see docs/protocol.md error codes). */
class CommandFailed(val code: String, message: String) : Exception(message)

/** App-scoped so commands outlive the activity: both the UI and the watch bridge drive the car through this. */
class Controller(context: Context) {
    private val store = TokenStore(context)
    private val client = LucidClient(store.deviceId())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(loggedIn = store.load() != null, pushOn = prefs.getBoolean("push", true)),
    )
    val state = _state.asStateFlow()

    /** This app's version, sent to the watch so its info screen can show it. */
    val version =context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    private var watchJob: Job? = null
    private var pushJob: Job? = null

    /** When the watch last asked for anything; the push loop reads and pushes faster for a while after it. */
    @Volatile private var watchAt = 0L
    private var pushSend: ((Map<String, Any>) -> Unit)? = null

    fun login(user: String, password: String) = run("Logging in") {
        try {
            store.save(client.login(user, password))
        } catch (e: LucidException) {
            // a rejected password must not take the logout path in run(), which would hide the reason
            _state.update { it.copy(message = e.message) }
            return@run
        }
        _state.update { it.copy(loggedIn = true) }
        fetch()
    }

    fun logout() {
        store.clear()
        _state.value = UiState(pushOn = _state.value.pushOn)
    }

    /** Switching on also sends the last known status now, with no Lucid call, so a returning watch is current. */
    fun setPush(on: Boolean) {
        prefs.edit().putBoolean("push", on).apply()
        _state.update { it.copy(pushOn = on) }
        val status = _state.value.status ?: return
        if (!on) return
        try {
            pushSend?.invoke(pushReply(status))
        } catch (e: Exception) {
            // the bridge can be mid-restart; the loop's next push covers it
        }
    }

    /**
     * Read the car (see [pollInterval]) and hand a changed status to [send] for the watch, so its glance and app open
     * on fresh data; [send] reports the delivery through [pushResult]. This is the one place Lucid is called without a
     * request. It reads with GetVehicleState, which showed no rate limit, unlike GetUserVehicles, and backs off after
     * a rate-limit answer. It runs whether or not a watch is connected: a connection check here once never passed
     * and silently stopped every push, and a send to an absent watch only fails.
     */
    fun startPush(send: (Map<String, Any>) -> Unit) {
        pushSend = send
        pushJob?.cancel()
        pushJob = scope.launch {
            pushLoop(
                { _state.value.pushOn && _state.value.loggedIn }, ::poll, { send(it + ("pv" to version)) }, ::logout,
                { System.currentTimeMillis() - watchAt < ACTIVE_WINDOW_MS },
            )
        }
    }

    fun pushResult(delivered: Boolean) {
        _state.update { it.copy(pushAtMs = System.currentTimeMillis(), pushOk = delivered) }
    }

    fun stopPush() {
        pushJob?.cancel()
        pushJob = null
        pushSend = null
    }

    fun refresh() = run("Refreshing") { fetch() }

    /** Handles one watch request; [reply] gets pending updates, then the final reply. Unknown input is ignored. */
    fun onWatch(req: Map<*, *>, send: (Map<String, Any>) -> Unit) {
        if ((req["v"] as? Number)?.toInt() != 1) return
        val id = req["id"] as? Number ?: return
        val cmd = req["cmd"] as? String ?: return
        val reply = { m: Map<String, Any> -> send(m + ("pv" to version)) }
        (req["wv"] as? String)?.let { w -> _state.update { it.copy(watchVersion = w.take(16)) } }
        if (cmd !in WATCH_COMMANDS) {
            // a watch newer than this app: say so, rather than let it wait for a reply that never comes
            reply(watchError(id, "badcmd"))
            return
        }
        watchAt = System.currentTimeMillis()
        // a newer request drops the older one's confirmation polling, not a command already on its way
        watchJob?.cancel()
        watchJob = scope.launch {
            try {
                val s = execute(cmd, climateTemp(req)) { reply(watchReply(id, _state.value.status, it)) }
                reply(watchReply(id, s, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is LucidException && e.unauthenticated) logout()
                reply(watchError(id, errCode(e)))
            }
        }
    }

    private fun token() = store.load() ?: throw LucidException(16, "Logged out")

    private suspend fun fetch(): VehicleStatus =
        client.status(token()).also { s -> _state.update { it.copy(status = s) } }

    private suspend fun execute(cmd: String, tempC: Double, progress: (String) -> Unit): VehicleStatus = when (cmd) {
        // answer at once so the watch's silence timer doesn't fire while a rate-limit retry runs
        "status" -> { progress("Updating"); fetch() }
        "lock" -> lock(true, progress)
        "unlock" -> lock(false, progress)
        "climate_on" -> climate(true, tempC, progress)
        "climate_off" -> climate(false, tempC, progress)
        "frunk_open", "frunk_close" -> closure("frunk", cmd == "frunk_open", progress)
        "trunk_open", "trunk_close" -> closure("trunk", cmd == "trunk_open", progress)
        // shortcut: the API gives no way to confirm a honk or flash (empty responses), so "sent" is all we know;
        // the labels say "Sending", not "Honking". Revisit if mobile_app_request proves to report them.
        "horn" -> command("Sending horn", null, progress) { s -> client.honk(token(), s.vehicleId) }
        "flash" -> command("Sending lights", null, progress) { s -> client.flashLights(token(), s.vehicleId) }
        else -> error("unhandled command $cmd")
    }

    private suspend fun closure(name: String, open: Boolean, progress: (String) -> Unit): VehicleStatus {
        val frunk = name == "frunk"
        // the watch never offers this; the check is for a request that skips the watch's menu
        if (gasStrutCloseRefused(frunk, open, _state.value.status)) {
            throw CommandFailed("unsupported", "Gas strut frunk cannot be closed")
        }
        return command(
            if (open) "Opening $name" else "Closing $name",
            { s -> closureDone(if (frunk) s.frunkOpen else s.trunkOpen, open) }, progress,
        ) { s ->
            if (frunk) client.setFrunk(token(), s.vehicleId, open) else client.setTrunk(token(), s.vehicleId, open)
        }
    }

    private suspend fun lock(locked: Boolean, progress: (String) -> Unit) =
        command(if (locked) "Locking" else "Unlocking", { it.locked == locked }, progress) { s ->
            client.setLocked(token(), s.vehicleId, locked)
        }

    private suspend fun climate(on: Boolean, tempC: Double, progress: (String) -> Unit) =
        command(if (on) "Starting climate" else "Stopping climate", { climateDone(it, on, tempC) }, progress) { s ->
            client.setPrecondition(token(), s.vehicleId, if (on) tempC else null)
        }

    /**
     * Wake if asleep, send, then poll until the car reports the change (it lags 10-40 s) or give up. A null [done] is
     * for a command with no state to read back (horn, lights): the answer is the status from before the send.
     */
    private suspend fun command(
        label: String,
        done: ((VehicleStatus) -> Boolean)?,
        progress: (String) -> Unit,
        send: suspend (VehicleStatus) -> Unit,
    ): VehicleStatus {
        progress(label)
        val sent = withContext(NonCancellable) {
            var s = fetch()
            if (!s.awake) {
                progress("Waking car")
                client.wake(token(), s.vehicleId)
                s = pollUntil { it.awake } ?: throw CommandFailed("wake", "Car did not wake")
                progress(label)
            }
            send(s)
            s
        }
        return if (done == null) sent else pollUntil(done) ?: throw CommandFailed("timeout", "No confirmation yet")
    }

    /** The cheap read for polling after a command: GetVehicleState showed no rate limit (see docs/research). */
    private suspend fun poll(): VehicleStatus =
        client.state(token()).also { s -> _state.update { it.copy(status = s) } }

    private suspend fun pollUntil(done: (VehicleStatus) -> Boolean): VehicleStatus? {
        repeat(POLL_COUNT) { i ->
            delay(pollDelayMs(i))
            // the command was already accepted, so a rate-limit answer only skips this read
            val s = try { poll() } catch (e: LucidException) { if (e.grpcStatus == 8) null else throw e }
            if (s != null && done(s)) return s
        }
        return null
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        scope.launch {
            _state.update { it.copy(busy = label, message = null) }
            try {
                block()
            } catch (e: LucidException) {
                if (e.unauthenticated) logout() else _state.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private companion object {
        val WATCH_COMMANDS = setOf(
            "status", "lock", "unlock", "climate_on", "climate_off", "frunk_open", "frunk_close", "trunk_open",
            "trunk_close", "horn", "flash",
        )
    }
}

/** Display data only: no VIN, location, owner data or tokens (docs/protocol.md). */
internal fun watchReply(id: Number, s: VehicleStatus?, pending: String?): Map<String, Any> = buildMap {
    put("v", 1)
    put("id", id)
    put("ok", true)
    s?.let {
        // the watch once showed 255%: a value outside 0..100 is not a reading, so send none
        if (it.socPercent in 0..100) put("soc", it.socPercent)
        it.locked?.let { l -> put("locked", l) }
        put("charge", it.chargeState)
        put("hvac", it.hvacOn)
        it.frunkOpen?.let { o -> put("frunk", o) }
        it.trunkOpen?.let { o -> put("trunk", o) }
        put("fpower", it.frunkPowered)
        if (it.model.isNotEmpty()) put("model", it.model)
        if (it.setTempC in MIN_TEMP_C..MAX_TEMP_C) put("tset", it.setTempC)
        // a value outside the plausible air range is a bad reading, not a temperature
        it.insideC?.takeIf { t -> t in AIR_RANGE_C }?.let { t -> put("tin", t) }
        it.outsideC?.takeIf { t -> t in AIR_RANGE_C }?.let { t -> put("tout", t) }
        put("awake", it.awake)
        put("age", ((System.currentTimeMillis() - it.updatedMs) / 1000).coerceAtLeast(0).toInt())
    }
    pending?.let { put("pending", it) }
}

internal fun watchError(id: Number, code: String): Map<String, Any> =
    mapOf("v" to 1, "id" to id, "ok" to false, "err" to code)

/** A gas-strut frunk can be unlatched but not closed by the car; an unknown status or strut is given the benefit. */
internal fun gasStrutCloseRefused(frunk: Boolean, open: Boolean, s: VehicleStatus?): Boolean =
    frunk && !open && s?.frunkPowered == false

/** A car that reports no state for a lid cannot confirm the change, so any answer after the send counts as done. */
internal fun closureDone(reported: Boolean?, wantOpen: Boolean): Boolean = reported == null || reported == wantOpen

// The car lags a command by 10-40 s, so poll fast for the first 30 s, then slowly to about 105 s in all.
internal const val POLL_COUNT = 35
internal fun pollDelayMs(i: Int): Long = if (i < 20) 1_500L else 5_000L

/** The longest the watch goes without a push, and the wait when the car's state is not known yet. */
internal const val PUSH_INTERVAL_MS = 5 * 60_000L
internal const val PUSH_MAX_MS = 60 * 60_000L

// How often to read the car, by its last state. A sleeping car's cloud copy is frozen, so it is read slowly.
// shortcut: untested values, to be tuned by a battery run (#47).
internal const val POLL_ASLEEP_MS = 60_000L
internal const val POLL_AWAKE_MS = 30_000L
internal const val POLL_CHARGING_MS = 30_000L
// After the watch asks for anything, read faster and let a change reach it sooner for a while.
internal const val POLL_ACTIVE_MS = 10_000L
internal const val ACTIVE_WINDOW_MS = 2 * 60_000L
// The fewest ms between pushes of a change, so the watch's background handler is not woken constantly.
internal const val PUSH_MIN_GAP_MS = 60_000L
internal const val PUSH_ACTIVE_GAP_MS = 10_000L

/** How long to wait before reading the car again, from what the last read showed. */
internal fun pollInterval(s: VehicleStatus, watchActive: Boolean): Long {
    val byState = when {
        !s.awake -> POLL_ASLEEP_MS
        s.chargeState == "CHARGING" -> POLL_CHARGING_MS
        else -> POLL_AWAKE_MS
    }
    return if (watchActive) minOf(byState, POLL_ACTIVE_MS) else byState
}

/**
 * What the watch would show, to tell whether a push has anything new. The car's report age is left out (it changes on
 * every read) and temperatures are rounded to a whole degree, or they would differ on almost every read.
 */
internal fun watchView(s: VehicleStatus): Map<String, Any> {
    val view = watchReply(0, s, null) - setOf("v", "id", "ok", "age")
    return view.mapValues { (k, v) -> if ((k == "tin" || k == "tout") && v is Double) Math.round(v) else v }
}

/**
 * The body of [Controller.startPush], apart from the controller so a test can drive it with a fake [fetch]. Reads the
 * car at [pollInterval] and pushes when what the watch shows has changed (at most once per [PUSH_MIN_GAP_MS], or
 * [PUSH_ACTIVE_GAP_MS] while [watchActive]), and at least every [PUSH_INTERVAL_MS] so the watch knows the link is up.
 */
internal suspend fun pushLoop(
    active: () -> Boolean,
    fetch: suspend () -> VehicleStatus,
    send: (Map<String, Any>) -> Unit,
    onUnauthenticated: () -> Unit,
    watchActive: () -> Boolean = { false },
) {
    var wait = PUSH_INTERVAL_MS
    var normal = PUSH_INTERVAL_MS
    var lastSent: Map<String, Any>? = null
    var sinceSend = 0L
    while (true) {
        if (active()) {
            wait = try {
                val s = fetch()
                val view = watchView(s)
                val fast = watchActive()
                val gap = if (fast) PUSH_ACTIVE_GAP_MS else PUSH_MIN_GAP_MS
                if (lastSent == null || sinceSend >= PUSH_INTERVAL_MS || (view != lastSent && sinceSend >= gap)) {
                    send(pushReply(s))
                    lastSent = view
                    sinceSend = 0
                }
                normal = pollInterval(s, fast)
                normal
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is LucidException && e.unauthenticated) onUnauthenticated()
                nextPushDelay(wait, e is LucidException && e.grpcStatus == 8, normal)
            }
        }
        delay(wait)
        sinceSend += wait
    }
}

/** After a rate-limit answer wait twice as long (up to an hour); after any other failure, the [normal] interval. */
internal fun nextPushDelay(current: Long, rateLimited: Boolean, normal: Long = PUSH_INTERVAL_MS): Long =
    if (rateLimited) (current * 2).coerceAtMost(PUSH_MAX_MS) else normal

/** A status the phone sends unasked: the same fields as a reply, flagged `push` instead of answering a request id. */
internal fun pushReply(s: VehicleStatus): Map<String, Any> = watchReply(0, s, null) + ("push" to true)

/** "now", "5m" or "3h" for how long before [nowMs] something was; the same wording as the watch. */
internal fun agoText(thenMs: Long, nowMs: Long): String {
    val secs = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
    return if (secs < 60) "now" else if (secs < 3600) "${secs / 60}m" else "${secs / 3600}h"
}

internal val AIR_RANGE_C = -50.0..70.0

internal const val DEFAULT_TEMP_C = 21.0
internal const val MIN_TEMP_C = 15.0 // the API's enforced range
internal const val MAX_TEMP_C = 30.0

/** The set point a watch asks for, clamped to the API's range; a missing or garbled one falls back to the default. */
internal fun climateTemp(req: Map<*, *>): Double =
    ((req["temp"] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: DEFAULT_TEMP_C).coerceIn(MIN_TEMP_C, MAX_TEMP_C)

/**
 * Whether the car reports the climate change. Starting also waits for the set point, because changing it while
 * climate is already on is a command like any other; a car that doesn't report one is judged on power alone.
 */
internal fun climateDone(s: VehicleStatus, on: Boolean, tempC: Double): Boolean =
    s.hvacOn == on && (!on || s.setTempC <= 0 || kotlin.math.abs(s.setTempC - tempC) <= TEMP_TOLERANCE_C)

// the car reads back within about 0.2 C of what was sent (measured in 0.5 F steps), so this is loose enough
private const val TEMP_TOLERANCE_C = 0.5

internal fun errCode(e: Exception): String = when {
    e is CommandFailed -> e.code
    e is NoVehicleException -> "novehicle"
    e is LucidException -> when (e.grpcStatus) {
        16 -> "auth"
        8 -> "rate"
        else -> "unknown"
    }
    e is IOException -> "offline"
    else -> "unknown"
}
