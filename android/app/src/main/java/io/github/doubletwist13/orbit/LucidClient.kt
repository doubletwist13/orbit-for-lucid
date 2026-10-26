package io.github.doubletwist13.orbit

import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mobilegateway.protos.LoginSessionOuterClass.GetNewJWTTokenRequest
import mobilegateway.protos.LoginSessionOuterClass.GetNewJWTTokenResponse
import mobilegateway.protos.LoginSessionOuterClass.GetUserVehiclesRequest
import mobilegateway.protos.LoginSessionOuterClass.GetUserVehiclesResponse
import mobilegateway.protos.LoginSessionOuterClass.LoginRequest
import mobilegateway.protos.LoginSessionOuterClass.LoginResponse
import mobilegateway.protos.LoginSessionOuterClass.NotificationChannelType
import mobilegateway.protos.LoginSessionOuterClass.Os
import mobilegateway.protos.LoginSessionOuterClass.SessionInfo
import mobilegateway.protos.VehicleStateServiceOuterClass.DoorLocksControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.HvacPower
import mobilegateway.protos.VehicleStateServiceOuterClass.LockState
import mobilegateway.protos.VehicleStateServiceOuterClass.PowerState
import mobilegateway.protos.VehicleStateServiceOuterClass.SetCabinTemperatureRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import mobilegateway.protos.VehicleStateServiceOuterClass.WakeupVehicleRequest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class LucidException(val grpcStatus: Int, message: String) : IOException(message) {
    val unauthenticated get() = grpcStatus == 16
}

/** What the UI and (later) the watch need; deliberately excludes VIN, location and owner data. */
data class VehicleStatus(
    val vehicleId: String,
    val nickname: String,
    val socPercent: Int,
    val locked: Boolean,
    val awake: Boolean,
    val chargeState: String,
    val hvacOn: Boolean,
    /** Driver-side cabin set point in Celsius; the car keeps one while climate is off too. 0 when not reported. */
    val setTempC: Double = 0.0,
    /** Cabin and outside air temperature in Celsius; null when the car sends no cabin block. */
    val insideC: Double? = null,
    val outsideC: Double? = null,
    val updatedMs: Long,
    val fromCache: Boolean = false,
)

/**
 * Minimal Lucid mobile-gateway client (unary gRPC over OkHttp HTTP/2). Protocol knowledge from python-lucidmotors
 * (MIT, Nick Shipp); the content-length/Accept-Encoding stripping is from Lucid-Motors-WearOS-Companion-App
 * (used with the author's permission), see NOTICE.
 */
class LucidClient(
    private val deviceId: String,
    private val http: OkHttpClient = defaultHttp(),
    // shortcut: US gateway only, add other regions when someone needs them
    private val host: String = "mobile.deneb.prod.infotainment.pdx.atieva.com",
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private var idToken: String? = null
    private var expirySec = 0L
    private val refreshLock = Mutex()

    /** Returns the refresh token to persist. The password is never kept. */
    suspend fun login(username: String, password: String): String {
        val req = LoginRequest.newBuilder()
            .setUsername(username).setPassword(password)
            .setNotificationChannelType(NotificationChannelType.NOTIFICATION_CHANNEL_FIREBASE)
            .setNotificationDeviceToken(deviceId).setOs(Os.OS_IOS).setLocale("en_US")
            .setClientName("orbit-for-lucid/0.1").setDeviceId(deviceId).build()
        val reply = call("mobilegateway.protos.LoginSession/Login", req, LoginResponse.parser(), auth = false)
        return useSession(reply.sessionInfo)
    }

    /** Ids are per-call; the 5-minute id_token is renewed when under 60 s remain. */
    private suspend fun ensureSession(refreshToken: String) = refreshLock.withLock {
        if (idToken == null || expirySec - now() < 60) refresh(refreshToken)
    }

    private suspend fun refresh(refreshToken: String) {
        val reply = call(
            "mobilegateway.protos.LoginSession/GetNewJWTToken",
            GetNewJWTTokenRequest.newBuilder().setRefreshToken(refreshToken).build(),
            GetNewJWTTokenResponse.parser(), auth = false,
        )
        useSession(reply.sessionInfo)
    }

    private fun useSession(s: SessionInfo): String {
        idToken = s.idToken
        expirySec = s.expiryTimeSec.toLong()
        return s.refreshToken
    }

    private var cached: Pair<Long, VehicleStatus>? = null

    /** GetUserVehicles is throttled by the gateway, so repeat calls within 4 s reuse the last answer. */
    suspend fun status(refreshToken: String): VehicleStatus {
        cached?.takeIf { System.currentTimeMillis() - it.first < STATUS_TTL_MS }
            ?.let { return it.second.copy(fromCache = true) }
        ensureSession(refreshToken)
        val reply = call(
            "mobilegateway.protos.LoginSession/GetUserVehicles", GetUserVehiclesRequest.getDefaultInstance(),
            GetUserVehiclesResponse.parser(),
        )
        // shortcut: first vehicle only
        return reply.userVehicleDataList.first().toStatus().also { cached = System.currentTimeMillis() to it }
    }

    suspend fun wake(refreshToken: String, vehicleId: String) = command(
        refreshToken, "WakeupVehicle", WakeupVehicleRequest.newBuilder().setVehicleId(vehicleId).build(),
    )

    suspend fun setLocked(refreshToken: String, vehicleId: String, locked: Boolean) = command(
        refreshToken, "DoorLocksControl",
        DoorLocksControlRequest.newBuilder().setVehicleId(vehicleId).addAllDoorLocation(listOf(1, 2, 3, 4))
            .setLockState(if (locked) LockState.LOCK_STATE_LOCKED else LockState.LOCK_STATE_UNLOCKED).build(),
    )

    /** 15-30 C starts cabin precondition (API-enforced range); null stops it. */
    suspend fun setPrecondition(refreshToken: String, vehicleId: String, celsius: Double?) = command(
        refreshToken, "SetCabinTemperature",
        SetCabinTemperatureRequest.newBuilder().setVehicleId(vehicleId).setTemperature(celsius ?: 0.0)
            .setState(if (celsius == null) HvacPower.HVAC_OFF else HvacPower.HVAC_PRECONDITION).build(),
    )

    private suspend fun command(refreshToken: String, method: String, req: MessageLite) {
        ensureSession(refreshToken)
        callRaw("mobilegateway.protos.VehicleStateService/$method", req, auth = true)
    }

    private suspend fun <T> call(
        method: String, req: MessageLite, parser: com.google.protobuf.Parser<T>, auth: Boolean = true,
    ): T = parser.parseFrom(callRaw(method, req, auth))

    /** The gateway throttles with RESOURCE_EXHAUSTED (8); python-lucidmotors also backs off 2/4/8 s and retries. */
    private suspend fun callRaw(method: String, req: MessageLite, auth: Boolean): ByteArray {
        for (delayMs in listOf(2000L, 4000L, 8000L)) {
            try {
                return send(method, req, auth)
            } catch (e: LucidException) {
                if (e.grpcStatus != 8) throw e
            }
            delay(delayMs)
        }
        return send(method, req, auth)
    }

    private suspend fun send(method: String, req: MessageLite, auth: Boolean): ByteArray =
        withContext(Dispatchers.IO) {
            val framed = ByteBuffer.allocate(5 + req.serializedSize).put(0).putInt(req.serializedSize)
                .put(req.toByteArray()).array()
            val body = object : RequestBody() {
                override fun contentType() = "application/grpc".toMediaType()
                override fun contentLength() = -1L // gateway resets the stream if content-length is sent
                override fun writeTo(sink: BufferedSink) { sink.write(framed) }
            }
            val builder = Request.Builder().url("https://$host/$method").post(body)
                .header("content-type", "application/grpc").header("te", "trailers")
                .header("user-agent", "grpc-python-asyncio/1.75.1 orbit-for-lucid/0.1")
            if (auth) builder.header("authorization", "Bearer ${idToken ?: error("not logged in")}")
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw LucidException(resp.code, "Lucid HTTP ${resp.code}")
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                // trailers() throws when the transport delivered none (trailers-only reply, HTTP/1.1 fallback)
                val trailers = runCatching { resp.trailers() }.getOrNull()
                val code = (trailers?.get("grpc-status") ?: resp.header("grpc-status"))?.toIntOrNull() ?: 0
                if (code != 0) {
                    val msg = trailers?.get("grpc-message") ?: resp.header("grpc-message")
                        ?: if (code == 8) "Lucid is rate limiting us, wait a minute" else "gRPC status $code"
                    throw LucidException(code, URLDecoder.decode(msg, "UTF-8") + " [${method.substringAfter('/')}]")
                }
                if (bytes.size < 5) ByteArray(0) else bytes.copyOfRange(5, bytes.size)
            }
        }

    companion object {
        private const val STATUS_TTL_MS = 4_000L

        // Strips headers the gateway rejects (see WearOS transport); applied at network level so OkHttp can't re-add.
        private val sanitizer = Interceptor {
            it.proceed(it.request().newBuilder().removeHeader("Content-Length").removeHeader("Accept-Encoding").build())
        }

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .addNetworkInterceptor(sanitizer).build()
    }
}

private fun Vehicle.toStatus() = VehicleStatus(
    vehicleId = vehicleId,
    nickname = config.nickname,
    socPercent = state.battery.chargePercent.toInt(),
    locked = state.body.doorLocks == LockState.LOCK_STATE_LOCKED,
    awake = state.power != PowerState.POWER_STATE_SLEEP,
    chargeState = state.charging.chargeState.name.removePrefix("CHARGE_STATE_"),
    hvacOn = state.hvac.power != HvacPower.HVAC_OFF,
    setTempC = state.hvac.frontLeftSetTemperature,
    insideC = state.cabin.interiorTemp.takeIf { state.hasCabin() },
    outsideC = state.cabin.exteriorTemp.takeIf { state.hasCabin() },
    updatedMs = state.lastUpdatedMs,
)
