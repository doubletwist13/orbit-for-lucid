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
)

/** A command failed in a way the watch protocol names (see docs/protocol.md error codes). */
class CommandFailed(val code: String, message: String) : Exception(message)

/** App-scoped so commands outlive the activity: both the UI and the watch bridge drive the car through this. */
class Controller(context: Context) {
    private val store = TokenStore(context)
    private val client = LucidClient(store.deviceId())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(UiState(loggedIn = store.load() != null))
    val state = _state.asStateFlow()
    private var watchJob: Job? = null

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
        _state.value = UiState()
    }

    fun refresh() = run("Refreshing") { fetch() }

    fun setLocked(locked: Boolean) = run(if (locked) "Locking" else "Unlocking") { lock(locked, ::busy) }

    fun setPrecondition(on: Boolean) = run(if (on) "Starting climate" else "Stopping climate") { climate(on, ::busy) }

    /** Handles one watch request; [reply] gets pending updates, then the final reply. Unknown input is ignored. */
    fun onWatch(req: Map<*, *>, reply: (Map<String, Any>) -> Unit) {
        if ((req["v"] as? Number)?.toInt() != 1) return
        val id = req["id"] as? Number ?: return
        val cmd = req["cmd"] as? String ?: return
        if (cmd !in WATCH_COMMANDS) return
        // a newer request drops the older one's confirmation polling, not a command already on its way
        watchJob?.cancel()
        watchJob = scope.launch {
            try {
                val s = execute(cmd) { reply(watchReply(id, _state.value.status, it)) }
                reply(watchReply(id, s, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is LucidException && e.unauthenticated) logout()
                reply(mapOf("v" to 1, "id" to id, "ok" to false, "err" to errCode(e)))
            }
        }
    }

    private fun busy(label: String) = _state.update { it.copy(busy = label) }

    private fun token() = store.load() ?: throw LucidException(16, "Logged out")

    private suspend fun fetch(): VehicleStatus =
        client.status(token()).also { s -> _state.update { it.copy(status = s) } }

    private suspend fun execute(cmd: String, progress: (String) -> Unit): VehicleStatus = when (cmd) {
        // answer at once so the watch's silence timer doesn't fire while a rate-limit retry runs
        "status" -> { progress("Updating"); fetch() }
        "lock" -> lock(true, progress)
        "unlock" -> lock(false, progress)
        "climate_on" -> climate(true, progress)
        else -> climate(false, progress)
    }

    private suspend fun lock(locked: Boolean, progress: (String) -> Unit) =
        command(if (locked) "Locking" else "Unlocking", { it.locked == locked }, progress) { s ->
            client.setLocked(token(), s.vehicleId, locked)
        }

    private suspend fun climate(on: Boolean, progress: (String) -> Unit) =
        command(if (on) "Starting climate" else "Stopping climate", { it.hvacOn == on }, progress) { s ->
            client.setPrecondition(token(), s.vehicleId, if (on) 21.0 else null)
        }

    /** Wake if asleep, send, then poll until the car reports the change (it lags 10-40 s) or give up. */
    private suspend fun command(
        label: String,
        done: (VehicleStatus) -> Boolean,
        progress: (String) -> Unit,
        send: suspend (VehicleStatus) -> Unit,
    ): VehicleStatus {
        progress(label)
        withContext(NonCancellable) {
            var s = fetch()
            if (!s.awake) {
                progress("Waking car")
                client.wake(token(), s.vehicleId)
                s = pollUntil { it.awake } ?: throw CommandFailed("wake", "Car did not wake")
                progress(label)
            }
            send(s)
        }
        return pollUntil(done) ?: throw CommandFailed("timeout", "No confirmation yet")
    }

    // shortcut: 3 quick 5 s polls, then 10 s (about 105 s); GetUserVehicles hit the gateway's rate limit (status 8)
    // when polled steadily at 4-6 s, so only the first seconds are fast. Tune once more latencies are measured.
    private suspend fun pollUntil(done: (VehicleStatus) -> Boolean): VehicleStatus? {
        repeat(12) { i ->
            delay(if (i < 3) 5_000 else 10_000)
            fetch().let { if (done(it)) return it }
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
        val WATCH_COMMANDS = setOf("status", "lock", "unlock", "climate_on", "climate_off")
    }
}

/** Display data only: no VIN, location, owner data or tokens (docs/protocol.md). */
internal fun watchReply(id: Number, s: VehicleStatus?, pending: String?): Map<String, Any> = buildMap {
    put("v", 1)
    put("id", id)
    put("ok", true)
    s?.let {
        put("soc", it.socPercent)
        put("locked", it.locked)
        put("charge", it.chargeState)
        put("hvac", it.hvacOn)
        put("awake", it.awake)
        put("age", ((System.currentTimeMillis() - it.updatedMs) / 1000).coerceAtLeast(0).toInt())
    }
    pending?.let { put("pending", it) }
}

internal fun errCode(e: Exception): String = when {
    e is CommandFailed -> e.code
    e is LucidException -> when (e.grpcStatus) {
        16 -> "auth"
        8 -> "rate"
        else -> "unknown"
    }
    e is IOException -> "offline"
    else -> "unknown"
}
