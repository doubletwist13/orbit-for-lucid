package io.github.doubletwist13.orbit

import kotlinx.coroutines.test.runTest
import mobilegateway.protos.LoginSessionOuterClass.GetNewJWTTokenResponse
import mobilegateway.protos.LoginSessionOuterClass.GetUserVehiclesResponse
import mobilegateway.protos.LoginSessionOuterClass.LoginResponse
import mobilegateway.protos.LoginSessionOuterClass.SessionInfo
import mobilegateway.protos.VehicleStateServiceOuterClass.AllWindowPosition
import mobilegateway.protos.VehicleStateServiceOuterClass.BatteryPreconStatus
import mobilegateway.protos.VehicleStateServiceOuterClass.BatteryState
import mobilegateway.protos.VehicleStateServiceOuterClass.BodyState
import mobilegateway.protos.VehicleStateServiceOuterClass.ChargingState
import mobilegateway.protos.VehicleStateServiceOuterClass.ChassisState
import mobilegateway.protos.VehicleStateServiceOuterClass.DoorState
import mobilegateway.protos.VehicleStateServiceOuterClass.GetVehicleStateResponse
import mobilegateway.protos.VehicleStateServiceOuterClass.LockState
import mobilegateway.protos.VehicleStateServiceOuterClass.Model
import mobilegateway.protos.VehicleStateServiceOuterClass.PowerState
import mobilegateway.protos.VehicleStateServiceOuterClass.StrutType
import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import mobilegateway.protos.VehicleStateServiceOuterClass.VehicleConfig
import mobilegateway.protos.VehicleStateServiceOuterClass.UnattendedOccupantDetectionStatus
import mobilegateway.protos.VehicleStateServiceOuterClass.VehicleState
import mobilegateway.protos.VehicleStateServiceOuterClass.WarningState
import mobilegateway.protos.VehicleStateServiceOuterClass.WindowPositionState
import mobilegateway.protos.VehicleStateServiceOuterClass.WindowPositionStatus
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer

class LucidClientTest {
    private fun fake(grpcStatus: Int = 0, message: ByteArray = ByteArray(0), seen: MutableList<String> = mutableListOf()) =
        LucidClient(
            deviceId = "dev", now = { 1000 },
            http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                seen += chain.request().url.encodedPath + " auth=" + chain.request().header("authorization")
                val body = ByteBuffer.allocate(5 + message.size).put(0).putInt(message.size).put(message).array()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(200).message("OK")
                    .header("grpc-status", grpcStatus.toString()).header("grpc-message", "token%20expired")
                    .body(body.toResponseBody("application/grpc".toMediaType())).build()
            }).build(),
        )

    /** Answers each call from [reply] (gRPC method name, 0-based count of calls to it) -> (grpc status, message). */
    private fun scripted(
        seen: MutableList<String>, reply: (String, Int) -> Pair<Int, ByteArray>,
    ) = LucidClient(
        deviceId = "dev", now = { 1000 },
        http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val method = chain.request().url.encodedPath.substringAfterLast('/')
            val (status, message) = reply(method, seen.count { it == method })
            seen += method
            val body = ByteBuffer.allocate(5 + message.size).put(0).putInt(message.size).put(message).array()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_2).code(200).message("OK")
                .header("grpc-status", status.toString()).body(body.toResponseBody("application/grpc".toMediaType()))
                .build()
        }).build(),
    )

    private fun session(expiry: Int) = SessionInfo.newBuilder().setIdToken("id").setExpiryTimeSec(expiry)
        .setRefreshToken("rt").build().let { GetNewJWTTokenResponse.newBuilder().setSessionInfo(it).build() }

    private val oneVehicle = GetUserVehiclesResponse.newBuilder()
        .addUserVehicleData(Vehicle.newBuilder().setVehicleId("v1")).build().toByteArray()

    @Test
    fun lidStrutAndModelAreReadFromTheVehicle() {
        val body = VehicleState.newBuilder().setBody(
            BodyState.newBuilder().setFrontCargo(DoorState.DOOR_STATE_AJAR).setRearCargo(DoorState.DOOR_STATE_CLOSED),
        )
        val s = Vehicle.newBuilder().setVehicleId("v1").setState(body)
            .setConfig(VehicleConfig.newBuilder().setModel(Model.MODEL_AIR).setFrunkStrut(StrutType.STRUT_TYPE_GAS))
            .build().toStatus()
        assertEquals(true, s.frunkOpen) // ajar counts as open
        assertEquals(false, s.trunkOpen)
        assertEquals(false, s.frunkPowered)
        assertEquals("AIR", s.model)
        val bare = Vehicle.newBuilder().setVehicleId("v1").build().toStatus()
        assertEquals(null, bare.frunkOpen) // no state reported
        assertEquals(true, bare.frunkPowered)
        assertEquals("", bare.model)
    }

    @Test
    fun doorsTiresAndChargePowerAreReadFromTheVehicle() {
        val state = VehicleState.newBuilder()
            .setBody(BodyState.newBuilder().setRearLeftDoor(DoorState.DOOR_STATE_OPEN))
            .setChassis(ChassisState.newBuilder().setSoftWarnRightRear(WarningState.WARNING_ON))
            .setCharging(ChargingState.newBuilder().setChargeRateKwhPrecise(48.4))
        val s = Vehicle.newBuilder().setVehicleId("v1").setState(state).build().toStatus()
        assertEquals(true, s.doorOpen)
        assertEquals(true, s.tireWarning)
        assertEquals(48.4, s.chargeRateKw, 0.0)
        val bare = Vehicle.newBuilder().setVehicleId("v1").build().toStatus()
        assertEquals(false, bare.doorOpen)
        assertEquals(false, bare.tireWarning)
    }

    @Test
    fun windowsAreClosedOnlyWhenEveryOneIsReportedShut() {
        val shut = WindowPositionStatus.WINDOW_POSITION_STATUS_FULLY_CLOSED
        val open = WindowPositionStatus.WINDOW_POSITION_STATUS_FULLY_OPEN
        fun closed(w: WindowPositionState.Builder?, all: AllWindowPosition? = null): Boolean? {
            val b = BodyState.newBuilder()
            w?.let { b.setWindowPosition(it) }
            all?.let { b.setAllWindowsPosition(it) }
            return b.build().windowsClosed()
        }
        fun four(a: WindowPositionStatus, b: WindowPositionStatus) = WindowPositionState.newBuilder()
            .setLeftFront(a).setRightFront(a).setLeftRear(b).setRightRear(b)
        assertEquals(true, closed(four(shut, shut)))
        assertEquals(false, closed(four(shut, open)))
        // a window that is not reported cannot be called shut
        assertEquals(false, closed(WindowPositionState.newBuilder().setLeftFront(shut)))
        // no positions at all: the car's "all windows" value decides, and without it nothing is known
        assertEquals(true, closed(null, AllWindowPosition.ALL_WINDOW_POSITION_CLOSED))
        assertEquals(false, closed(null, AllWindowPosition.ALL_WINDOW_POSITION_OPEN))
        assertEquals(null, closed(null))
    }

    @Test
    fun anyOccupantWarningStageIsAnAlert() {
        fun alert(st: UnattendedOccupantDetectionStatus) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(
                VehicleState.newBuilder().setBody(BodyState.newBuilder().setUnattendedOccupantDetectionStatus(st)),
            ).build().toStatus().occupantAlert
        assertEquals(false, alert(UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_NO_REQUEST))
        assertEquals(false, alert(UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_UNKNOWN))
        assertEquals(true, alert(UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_STAGE_1))
        assertEquals(true, alert(UnattendedOccupantDetectionStatus.UNATTENDED_OCCUPANT_DETECTION_STATUS_STAGE_3))
    }

    @Test
    fun detailsAreReadFromTheVehicleAndLeftOutWhenAbsent() {
        val state = VehicleState.newBuilder()
            .setChassis(
                ChassisState.newBuilder().setOdometerKm(12_345.6).setFrontLeftTirePressureBar(2.8)
                    .setFrontRightTirePressureBar(2.8).setRearLeftTirePressureBar(2.7).setRearRightTirePressureBar(2.7),
            )
            .setBattery(BatteryState.newBuilder().setMinCellTemp(23.6).setMaxCellTemp(26.4).setBatteryHealthLevel(97.4))
        val s = Vehicle.newBuilder().setVehicleId("v1").setState(state).build().toStatus()
        assertEquals(listOf(2.8, 2.8, 2.7, 2.7), s.tiresBar)
        assertEquals(23.6, s.cellMinC!!, 0.0)
        assertEquals(26.4, s.cellMaxC!!, 0.0)
        assertEquals(12_345.6, s.odometerKm!!, 0.0)
        assertEquals(97.4, s.healthLevel!!, 0.0)
        val bare = Vehicle.newBuilder().setVehicleId("v1").build().toStatus()
        assertEquals(null, bare.tiresBar)
        assertEquals(null, bare.cellMinC)
        assertEquals(null, bare.odometerKm)
        assertEquals(null, bare.healthLevel)
        // one wheel without a reading leaves the whole set out, so a pressure is never shown against the wrong wheel
        val partial = ChassisState.newBuilder().setFrontLeftTirePressureBar(2.8)
        assertEquals(
            null,
            Vehicle.newBuilder().setVehicleId("v1").setState(VehicleState.newBuilder().setChassis(partial)).build()
                .toStatus().tiresBar,
        )
    }

    @Test
    fun lockStateIsNullWhenTheCarReportsItUnknown() {
        fun locked(state: LockState) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(VehicleState.newBuilder().setBody(BodyState.newBuilder().setDoorLocks(state))).build()
            .toStatus().locked
        assertEquals(true, locked(LockState.LOCK_STATE_LOCKED))
        assertEquals(false, locked(LockState.LOCK_STATE_UNLOCKED))
        assertEquals(null, locked(LockState.LOCK_STATE_UNKNOWN))
    }

    @Test
    fun chargeLimitIsRoundedAndNullWhenTheCarReportsNone() {
        fun limit(percent: Double) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(VehicleState.newBuilder().setCharging(ChargingState.newBuilder().setChargeLimitPercent(percent)))
            .build().toStatus().chargeLimit
        assertEquals(80, limit(80.0))
        assertEquals(81, limit(80.6))
        assertEquals(null, limit(0.0)) // unset reads as zero
        assertEquals(null, Vehicle.newBuilder().setVehicleId("v1").build().toStatus().chargeLimit)
    }

    @Test
    fun batteryPreconditioningIsReadFromTheBatteryBlock() {
        fun status(p: BatteryPreconStatus, minutes: Int) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(
                VehicleState.newBuilder().setBattery(
                    BatteryState.newBuilder().setPreconditioningStatus(p).setPreconditioningTimeRemaining(minutes),
                ),
            ).build().toStatus()
        val on = status(BatteryPreconStatus.BATTERY_PRECON_ON, 12)
        assertEquals("ON", on.preconStatus)
        assertEquals(12, on.preconMinutes)
        assertEquals("OFF", status(BatteryPreconStatus.BATTERY_PRECON_OFF, 0).preconStatus)
        assertEquals(null, status(BatteryPreconStatus.BATTERY_PRECON_OFF, 0).preconMinutes) // zero is no time
        assertEquals("UNKNOWN", Vehicle.newBuilder().setVehicleId("v1").build().toStatus().preconStatus)
    }

    @Test
    fun acCurrentLimitsAreReadFromTheChargingBlockAndNullWhenUnset() {
        fun status(setting: Int, session: Int) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(
                VehicleState.newBuilder().setCharging(
                    ChargingState.newBuilder().setEnergyAcCurrentLimit(setting).setActiveSessionAcCurrentLimit(session),
                ),
            ).build().toStatus()
        val s = status(40, 32)
        assertEquals(40, s.acCharger) // energy_ac_current_limit: the charger's maximum
        assertEquals(32, s.acSet) // active_session_ac_current_limit: what was set
        assertEquals(null, status(0, 0).acCharger) // unset reads as zero
        assertEquals(null, status(0, 0).acSet)
    }

    @Test
    fun chargePortIsNullWhenTheCarReportsNoState() {
        fun port(state: DoorState) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(VehicleState.newBuilder().setBody(BodyState.newBuilder().setChargePort(state))).build()
            .toStatus().portOpen
        assertEquals(true, port(DoorState.DOOR_STATE_OPEN))
        assertEquals(true, port(DoorState.DOOR_STATE_AJAR))
        assertEquals(false, port(DoorState.DOOR_STATE_CLOSED))
        assertEquals(null, port(DoorState.DOOR_STATE_UNKNOWN))
    }

    @Test
    fun onlySleepAndSleepUpdateCountAsAsleep() {
        fun awake(p: Int) = Vehicle.newBuilder().setVehicleId("v1")
            .setState(VehicleState.newBuilder().setPowerValue(p)).build().toStatus().awake
        fun awake(p: PowerState) = awake(p.number)
        assertEquals(false, awake(PowerState.POWER_STATE_SLEEP))
        assertEquals(false, awake(PowerState.POWER_STATE_SLEEP_UPDATE))
        assertEquals(true, awake(PowerState.POWER_STATE_SLEEP_CHARGE)) // a plugged-in car reports live
        assertEquals(true, awake(PowerState.POWER_STATE_DRIVE))
        assertEquals(true, awake(99)) // a state this build does not know (the WearOS app saw 12 and 13)
    }

    @Test
    fun rateLimitedStatusRetriesThenSucceeds() = runTest {
        val seen = mutableListOf<String>()
        val status = scripted(seen) { method, n ->
            when {
                method == "GetNewJWTToken" -> 0 to session(2000).toByteArray()
                n == 0 -> 8 to ByteArray(0)
                else -> 0 to oneVehicle
            }
        }.status("rt")
        assertEquals("v1", status.vehicleId)
        assertEquals(listOf("GetNewJWTToken", "GetUserVehicles", "GetUserVehicles"), seen)
    }

    @Test
    fun stateReadsGetVehicleStateAndKeepsTheListedConfig() = runTest {
        val listed = GetUserVehiclesResponse.newBuilder().addUserVehicleData(
            Vehicle.newBuilder().setVehicleId("v1")
                .setConfig(VehicleConfig.newBuilder().setNickname("nick").setModel(Model.MODEL_AIR)),
        ).build().toByteArray()
        val newer = GetVehicleStateResponse.newBuilder().setVehicleId("v1").setState(
            VehicleState.newBuilder().setBody(BodyState.newBuilder().setFrontCargo(DoorState.DOOR_STATE_OPEN)),
        ).build().toByteArray()
        val seen = mutableListOf<String>()
        val client = scripted(seen) { method, _ ->
            0 to when (method) {
                "GetNewJWTToken" -> session(2000).toByteArray()
                "GetUserVehicles" -> listed
                else -> newer
            }
        }
        assertEquals(null, client.state("rt").frunkOpen) // nothing listed yet: falls back to the vehicle list
        val s = client.state("rt")
        assertEquals(true, s.frunkOpen)
        assertEquals("AIR", s.model) // the config is not in a GetVehicleState reply
        assertEquals("nick", s.nickname)
        assertEquals(listOf("GetNewJWTToken", "GetUserVehicles", "GetVehicleState"), seen)
    }

    @Test
    fun anAccountWithNoVehicleIsReportedNotCrashed() = runTest {
        val client = scripted(mutableListOf()) { method, _ ->
            0 to if (method == "GetNewJWTToken") session(2000).toByteArray() else ByteArray(0)
        }
        try {
            client.status("rt")
            fail("expected NoVehicleException")
        } catch (e: NoVehicleException) {
            assertEquals("No vehicle on this Lucid account", e.message)
        }
    }

    @Test
    fun secondStatusWithinTenSecondsUsesCache() = runTest {
        val seen = mutableListOf<String>()
        val client = scripted(seen) { method, _ ->
            0 to if (method == "GetNewJWTToken") session(2000).toByteArray() else oneVehicle
        }
        client.status("rt")
        assertTrue(client.status("rt").fromCache)
        assertEquals(listOf("GetNewJWTToken", "GetUserVehicles"), seen)
    }

    @Test
    fun tokenRefreshesOnlyWhenUnderSixtySecondsRemain() = runTest {
        for ((expiry, refreshes) in listOf(2000 to 1, 1030 to 2)) {
            val seen = mutableListOf<String>()
            val client = scripted(seen) { method, _ ->
                0 to if (method == "GetNewJWTToken") session(expiry).toByteArray() else ByteArray(0)
            }
            client.wake("rt", "v1")
            client.wake("rt", "v1")
            assertEquals("expiry $expiry", refreshes, seen.count { it == "GetNewJWTToken" })
        }
    }

    @Test
    fun loginReturnsRefreshTokenAndSendsNoAuthHeader() = runTest {
        val session = SessionInfo.newBuilder().setIdToken("id").setExpiryTimeSec(2000).setRefreshToken("rt").build()
        val seen = mutableListOf<String>()
        val rt = fake(message = LoginResponse.newBuilder().setSessionInfo(session).build().toByteArray(), seen = seen)
            .login("u", "p")
        assertEquals("rt", rt)
        assertEquals("/mobilegateway.protos.LoginSession/Login auth=null", seen.single())
    }

    @Test
    fun grpcErrorStatusIsDecodedAndFlaggedUnauthenticated() = runTest {
        try {
            fake(grpcStatus = 16).login("u", "p")
            fail("expected LucidException")
        } catch (e: LucidException) {
            assertTrue(e.unauthenticated)
            assertEquals("token expired [Login]", e.message)
        }
    }
}
