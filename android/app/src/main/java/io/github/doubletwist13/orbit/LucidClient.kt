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
import mobilegateway.protos.VehicleStateServiceOuterClass.AllWindowControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.AllWindowPosition
import mobilegateway.protos.VehicleStateServiceOuterClass.BodyState
import mobilegateway.protos.VehicleStateServiceOuterClass.ChargeAction
import mobilegateway.protos.VehicleStateServiceOuterClass.ChargeControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.ControlChargePortRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.CreatureComfortMode
import mobilegateway.protos.VehicleStateServiceOuterClass.DoorLocksControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.DoorState
import mobilegateway.protos.VehicleStateServiceOuterClass.FrontCargoControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.GetVehicleStateRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.GetVehicleStateResponse
import mobilegateway.protos.VehicleStateServiceOuterClass.HonkHornRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.HvacPower
import mobilegateway.protos.VehicleStateServiceOuterClass.KeepClimateStatus
import mobilegateway.protos.VehicleStateServiceOuterClass.LightAction
import mobilegateway.protos.VehicleStateServiceOuterClass.LightsControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.LockState
import mobilegateway.protos.VehicleStateServiceOuterClass.Model
import mobilegateway.protos.VehicleStateServiceOuterClass.PowerState
import mobilegateway.protos.VehicleStateServiceOuterClass.RearCargoControlRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.SetCabinTemperatureRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.SetChargeLimitRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.SetCreatureComfortModeRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.StrutType
import mobilegateway.protos.VehicleStateServiceOuterClass.UnattendedOccupantDetectionStatus
import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import mobilegateway.protos.VehicleStateServiceOuterClass.WakeupVehicleRequest
import mobilegateway.protos.VehicleStateServiceOuterClass.WarningState
import mobilegateway.protos.VehicleStateServiceOuterClass.WindowPositionStatus
import mobilegateway.protos.VehicleStateServiceOuterClass.WindowSwitchState
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

/** The account is valid but lists no vehicle, so there is nothing to read or command. */
class NoVehicleException : Exception("No vehicle on this Lucid account")

/** What the UI and (later) the watch need; deliberately excludes VIN, location and owner data. */
data class VehicleStatus(
    val vehicleId: String,
    val nickname: String,
    val socPercent: Int,
    /** Null when the car reports an unknown lock state: it is neither locked nor unlocked as far as we can tell. */
    val locked: Boolean?,
    val awake: Boolean,
    val chargeState: String,
    val hvacOn: Boolean,
    /** Driver-side cabin set point in Celsius; the car keeps one while climate is off too. 0 when not reported. */
    val setTempC: Double = 0.0,
    /** Cabin and outside air temperature in Celsius; null when the car sends no cabin block. */
    val insideC: Double? = null,
    val outsideC: Double? = null,
    /** Frunk and trunk open (ajar counts as open); null when the car reports no state for the lid. */
    val frunkOpen: Boolean? = null,
    val trunkOpen: Boolean? = null,
    /** False for a gas-strut frunk: the car can unlatch it but not close it. An unknown strut is treated as powered. */
    val frunkPowered: Boolean = true,
    /** Charging power in kW; 0 when not reported. Only meaningful while the charge state is CHARGING. */
    val chargeRateKw: Double = 0.0,
    /** The charge limit in percent; null when the car reports none. */
    val chargeLimit: Int? = null,
    /** Charge port open (ajar counts as open); null when the car reports no state for it. */
    val portOpen: Boolean? = null,
    /** Any of the four doors open (ajar counts as open). */
    val doorOpen: Boolean = false,
    /** The car's own tyre pressure warning (soft or hard) is on for any wheel. */
    val tireWarning: Boolean = false,
    /** Tyre pressures in bar, front left, front right, rear left, rear right; null unless all four are plausible. */
    val tiresBar: List<Double>? = null,
    /** Coolest and warmest battery cell in Celsius; null when the car sends no battery block. */
    val cellMinC: Double? = null,
    val cellMaxC: Double? = null,
    val odometerKm: Double? = null,
    /** Energy left in the battery in kWh, for working out efficiency (see [EnergyLog]). */
    val energyKwh: Double? = null,
    /** The car's battery health level, in whatever scale the car uses (not yet known); null when not reported. */
    val healthLevel: Double? = null,
    /** All four windows closed; null when the car reports no window state, so a close cannot be confirmed. */
    val windowsClosed: Boolean? = null,
    /** The car's unattended-occupant detection is in any warning stage: someone, or a pet, may be left inside. */
    val occupantAlert: Boolean = false,
    /** Pet mode (the car keeps the cabin comfortable while nobody is in it) is on. */
    val petMode: Boolean = false,
    /** Everything the car last reported, for the details screen; not sent to the watch. */
    val raw: Vehicle? = null,
    /** "AIR" or "GRAVITY"; empty when the car reports none or a model this app has no art for. */
    val model: String = "",
    val updatedMs: Long,
    /** When the phone got this answer from Lucid; a cached copy keeps the time of the real fetch. */
    val fetchedMs: Long = System.currentTimeMillis(),
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
        vehicle = reply.userVehicleDataList.firstOrNull() ?: throw NoVehicleException()
        return vehicle!!.toStatus().also { cached = System.currentTimeMillis() to it }
    }

    /** The last full vehicle: GetVehicleState returns the state but not the config (nickname, model, strut). */
    private var vehicle: Vehicle? = null

    /**
     * Fresh state for polling after a command. GetVehicleState showed no rate limit at 1 s, unlike GetUserVehicles
     * (docs/research/vehicle-state-findings.md), and does not wake the car. Falls back to [status] until a vehicle
     * has been listed, or if the reply carries no state.
     */
    suspend fun state(refreshToken: String): VehicleStatus {
        val v = vehicle ?: return status(refreshToken)
        ensureSession(refreshToken)
        val reply = call(
            "mobilegateway.protos.VehicleStateService/GetVehicleState",
            GetVehicleStateRequest.newBuilder().setVehicleId(v.vehicleId).build(),
            GetVehicleStateResponse.parser(),
        )
        if (!reply.hasState()) return status(refreshToken)
        vehicle = v.toBuilder().setState(reply.state).build()
        return vehicle!!.toStatus()
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

    suspend fun setFrunk(refreshToken: String, vehicleId: String, open: Boolean) = command(
        refreshToken, "FrontCargoControl",
        FrontCargoControlRequest.newBuilder().setVehicleId(vehicleId).setClosureState(doorState(open)).build(),
    )

    suspend fun setTrunk(refreshToken: String, vehicleId: String, open: Boolean) = command(
        refreshToken, "RearCargoControl",
        RearCargoControlRequest.newBuilder().setVehicleId(vehicleId).setClosureState(doorState(open)).build(),
    )

    suspend fun honk(refreshToken: String, vehicleId: String) = command(
        refreshToken, "HonkHorn", HonkHornRequest.newBuilder().setVehicleId(vehicleId).build(),
    )

    suspend fun flashLights(refreshToken: String, vehicleId: String) = command(
        refreshToken, "LightsControl",
        LightsControlRequest.newBuilder().setVehicleId(vehicleId).setAction(LightAction.LIGHT_ACTION_FLASH).build(),
    )

    /** Closes all four windows. Only closing is offered: the API's other choices (vent, open all) are left out. */
    suspend fun closeWindows(refreshToken: String, vehicleId: String) = command(
        refreshToken, "AllWindowControl",
        AllWindowControlRequest.newBuilder().setVehicleId(vehicleId)
            .setState(WindowSwitchState.WINDOW_SWITCH_STATE_AUTO_UP_ALL).build(),
    )

    suspend fun setPetMode(refreshToken: String, vehicleId: String, on: Boolean) = command(
        refreshToken, "SetCreatureComfortMode",
        SetCreatureComfortModeRequest.newBuilder().setVehicleId(vehicleId)
            .setMode(
                if (on) CreatureComfortMode.CREATURE_COMFORT_MODE_ON else CreatureComfortMode.CREATURE_COMFORT_MODE_OFF,
            ).build(),
    )

    suspend fun setCharging(refreshToken: String, vehicleId: String, start: Boolean) = command(
        refreshToken, "ChargeControl",
        ChargeControlRequest.newBuilder().setVehicleId(vehicleId)
            .setAction(if (start) ChargeAction.CHARGE_ACTION_START else ChargeAction.CHARGE_ACTION_STOP).build(),
    )

    suspend fun setChargeLimit(refreshToken: String, vehicleId: String, percent: Int) = command(
        refreshToken, "SetChargeLimit",
        SetChargeLimitRequest.newBuilder().setVehicleId(vehicleId).setLimitPercent(percent).build(),
    )

    suspend fun setChargePort(refreshToken: String, vehicleId: String, open: Boolean) = command(
        refreshToken, "ControlChargePort",
        ControlChargePortRequest.newBuilder().setVehicleId(vehicleId).setClosureState(doorState(open)).build(),
    )

    private fun doorState(open: Boolean) = if (open) DoorState.DOOR_STATE_OPEN else DoorState.DOOR_STATE_CLOSED

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

internal fun Vehicle.toStatus() = VehicleStatus(
    vehicleId = vehicleId,
    nickname = config.nickname,
    socPercent = state.battery.chargePercent.toInt(),
    locked = when (state.body.doorLocks) {
        LockState.LOCK_STATE_LOCKED -> true
        LockState.LOCK_STATE_UNLOCKED -> false
        else -> null
    },
    // sleep-charge (6) is awake: a plugged-in car reports live (docs/research/vehicle-state-findings.md). Sleep-update
    // (8) is asleep by the WearOS app's finding, not yet measured here; an unknown state counts as awake.
    awake = state.power != PowerState.POWER_STATE_SLEEP && state.power != PowerState.POWER_STATE_SLEEP_UPDATE,
    chargeState = state.charging.chargeState.name.removePrefix("CHARGE_STATE_"),
    hvacOn = state.hvac.power != HvacPower.HVAC_OFF,
    setTempC = state.hvac.frontLeftSetTemperature,
    insideC = state.cabin.interiorTemp.takeIf { state.hasCabin() },
    outsideC = state.cabin.exteriorTemp.takeIf { state.hasCabin() },
    frunkOpen = state.body.frontCargo.isOpen(),
    trunkOpen = state.body.rearCargo.isOpen(),
    frunkPowered = config.frunkStrut != StrutType.STRUT_TYPE_GAS,
    chargeRateKw = state.charging.chargeRateKwhPrecise,
    chargeLimit = state.charging.chargeLimitPercent.takeIf { it > 0 }?.let { Math.round(it).toInt() },
    portOpen = state.body.chargePort.isOpen(),
    doorOpen = with(state.body) {
        listOf(frontLeftDoor, frontRightDoor, rearLeftDoor, rearRightDoor).any { it.isOpen() == true }
    },
    tireWarning = with(state.chassis) {
        listOf(
            hardWarnLeftFront, hardWarnLeftRear, hardWarnRightFront, hardWarnRightRear,
            softWarnLeftFront, softWarnLeftRear, softWarnRightFront, softWarnRightRear,
        ).any { it == WarningState.WARNING_ON }
    },
    tiresBar = with(state.chassis) {
        listOf(
            frontLeftTirePressureBar, frontRightTirePressureBar, rearLeftTirePressureBar, rearRightTirePressureBar,
        ).takeIf { state.hasChassis() && it.all { p -> p in TIRE_RANGE_BAR } }
    },
    cellMinC = state.battery.minCellTemp.takeIf { state.hasBattery() },
    cellMaxC = state.battery.maxCellTemp.takeIf { state.hasBattery() },
    odometerKm = state.chassis.odometerKm.takeIf { state.hasChassis() && it > 0 },
    energyKwh = state.battery.kwhr.takeIf { state.hasBattery() && it > 0 },
    healthLevel = state.battery.batteryHealthLevel.takeIf { state.hasBattery() && it > 0 },
    windowsClosed = state.body.windowsClosed(),
    occupantAlert = when (state.body.unattendedOccupantDetectionStatus) {
        UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_STAGE_1,
        UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_STAGE_2,
        UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_STAGE_3,
        -> true
        else -> false
    },
    petMode = state.hvac.keepClimateStatus == KeepClimateStatus.KEEP_CLIMATE_STATUS_PET_MODE_ON,
    raw = this,
    model = when (config.model) {
        Model.MODEL_AIR -> "AIR"
        Model.MODEL_GRAVITY -> "GRAVITY"
        else -> ""
    },
    updatedMs = state.lastUpdatedMs,
)

/**
 * Whether every window is shut, from the four positions, or the car's "all windows" value when it sends no positions.
 * Any window known to be somewhere else makes it false; null when the car says nothing either way.
 */
internal fun BodyState.windowsClosed(): Boolean? {
    val w = windowPosition
    val known = listOf(w.leftFront, w.leftRear, w.rightFront, w.rightRear)
        .filter { it != WindowPositionStatus.WINDOW_POSITION_STATUS_UNKNOWN }
    if (known.isNotEmpty()) {
        return known.size == 4 && known.all {
            it == WindowPositionStatus.WINDOW_POSITION_STATUS_FULLY_CLOSED ||
                it == WindowPositionStatus.WINDOW_POSITION_STATUS_HARD_STOP_UP
        }
    }
    return when (allWindowsPosition) {
        AllWindowPosition.ALL_WINDOW_POSITION_CLOSED -> true
        AllWindowPosition.ALL_WINDOW_POSITION_OPEN -> false
        else -> null
    }
}

private fun DoorState.isOpen(): Boolean? = when (this) {
    DoorState.DOOR_STATE_OPEN, DoorState.DOOR_STATE_AJAR -> true
    DoorState.DOOR_STATE_CLOSED -> false
    else -> null
}
