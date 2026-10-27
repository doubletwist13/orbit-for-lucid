package io.github.doubletwist13.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ControllerTest {
    private val status = VehicleStatus(
        vehicleId = "VIN-SECRET", nickname = "nick", socPercent = 75, locked = true, awake = true,
        chargeState = "NOT_CONNECTED", hvacOn = false, updatedMs = System.currentTimeMillis() - 5_000,
    )

    @Test fun replyCarriesDisplayDataOnly() {
        val r = watchReply(3, status, null)
        assertEquals(setOf("v", "id", "ok", "soc", "locked", "charge", "hvac", "awake", "age"), r.keys)
        assertEquals(75, r["soc"])
        assertFalse(r.values.any { it == "VIN-SECRET" || it == "nick" })
    }

    @Test fun socOutsideZeroToHundredIsNotSent() {
        assertFalse(watchReply(1, status.copy(socPercent = 255), null).containsKey("soc"))
        assertEquals(0, watchReply(1, status.copy(socPercent = 0), null)["soc"])
    }

    @Test fun setPointIsSentOnlyWhenTheCarReportsOne() {
        assertFalse(watchReply(1, status, null).containsKey("tset"))
        assertFalse(watchReply(1, status.copy(setTempC = 0.0), null).containsKey("tset"))
        assertEquals(22.5, watchReply(1, status.copy(setTempC = 22.5), null)["tset"])
    }

    @Test fun airTemperaturesAreSentOnlyWhenPlausible() {
        val r = watchReply(1, status.copy(insideC = 21.0, outsideC = 0.0), null)
        assertEquals(21.0, r["tin"])
        assertEquals(0.0, r["tout"]) // freezing is a real reading
        assertFalse(watchReply(1, status, null).containsKey("tin"))
        assertFalse(watchReply(1, status.copy(insideC = 999.0, outsideC = -273.0), null).containsKey("tout"))
    }

    @Test fun requestedTemperatureIsClampedAndDefaulted() {
        assertEquals(22.5, climateTemp(mapOf("temp" to 22.5)), 0.0)
        assertEquals(15.0, climateTemp(mapOf("temp" to 5.0)), 0.0)
        assertEquals(30.0, climateTemp(mapOf("temp" to 99)), 0.0)
        assertEquals(21.0, climateTemp(mapOf<String, Any>()), 0.0)
        assertEquals(21.0, climateTemp(mapOf("temp" to "hot")), 0.0)
        assertEquals(21.0, climateTemp(mapOf("temp" to Double.NaN)), 0.0)
    }

    @Test fun climateOnIsDoneOnlyAtTheRequestedSetPoint() {
        val on = status.copy(hvacOn = true, setTempC = 21.0)
        assertTrue(climateDone(on, true, 21.2))
        assertFalse(climateDone(on, true, 24.0)) // already on at the old set point: keep waiting
        assertTrue(climateDone(on.copy(setTempC = 0.0), true, 24.0)) // no set point reported: power alone
        assertTrue(climateDone(status, false, 21.0))
        assertFalse(climateDone(on, false, 21.0))
    }

    @Test fun pushIsAFlaggedStatusWithNoSecrets() {
        val r = pushReply(status)
        assertEquals(true, r["push"])
        assertEquals(75, r["soc"])
        assertFalse(r.values.any { it == "VIN-SECRET" || it == "nick" })
    }

    @Test fun pushBacksOffAfterARateLimitAndRecoversAfterAnythingElse() {
        assertEquals(2 * PUSH_INTERVAL_MS, nextPushDelay(PUSH_INTERVAL_MS, true))
        assertEquals(PUSH_MAX_MS, nextPushDelay(PUSH_MAX_MS, true)) // capped
        assertEquals(PUSH_INTERVAL_MS, nextPushDelay(4 * PUSH_INTERVAL_MS, false))
    }

    @Test fun agoTextMatchesTheWatchWording() {
        assertEquals("now", agoText(1_000, 30_000))
        assertEquals("5m", agoText(0, 5 * 60_000 + 59_000))
        assertEquals("3h", agoText(0, 3 * 3_600_000L))
        assertEquals("now", agoText(10_000, 0)) // a clock step backwards is not negative
    }

    @Test fun pendingReplyWithoutStatusHasNoStateFields() {
        assertEquals(setOf("v", "id", "ok", "pending"), watchReply(1, null, "Waking car").keys)
    }

    @Test fun errorsMapToProtocolCodes() {
        assertEquals("auth", errCode(LucidException(16, "x")))
        assertEquals("rate", errCode(LucidException(8, "x")))
        assertEquals("unknown", errCode(LucidException(2, "x")))
        assertEquals("offline", errCode(IOException("no route")))
        assertEquals("timeout", errCode(CommandFailed("timeout", "x")))
        assertEquals("unknown", errCode(IllegalStateException("raw text must not leak")))
    }
}
