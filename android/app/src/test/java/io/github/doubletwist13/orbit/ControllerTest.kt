package io.github.doubletwist13.orbit

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test fun pushLoopFetchesAndSendsNowThenEveryInterval() = runTest {
        val sent = mutableListOf<Map<String, Any>>()
        backgroundScope.launch { pushLoop({ true }, { status }, { sent += it }, {}) }
        runCurrent()
        assertEquals(1, sent.size)
        assertEquals(true, sent[0]["push"])
        advanceTimeBy(PUSH_INTERVAL_MS - 1)
        runCurrent()
        assertEquals(1, sent.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, sent.size)
    }

    @Test fun pushLoopSkipsTheFetchWhileSwitchedOffOrLoggedOut() = runTest {
        var active = false
        var fetches = 0
        backgroundScope.launch { pushLoop({ active }, { fetches++; status }, {}, {}) }
        runCurrent()
        advanceTimeBy(PUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(0, fetches) // no Lucid call at all
        active = true
        advanceTimeBy(PUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(1, fetches)
    }

    @Test fun pushLoopLogsOutOnceOnAnUnauthenticatedAnswer() = runTest {
        var logouts = 0
        var sent = 0
        backgroundScope.launch {
            pushLoop({ logouts == 0 }, { throw LucidException(16, "x") }, { sent++ }, { logouts++ })
        }
        runCurrent()
        advanceTimeBy(3 * PUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(1, logouts)
        assertEquals(0, sent)
    }

    @Test fun pushLoopDoublesItsWaitAfterEachRateLimit() = runTest {
        var fetches = 0
        backgroundScope.launch { pushLoop({ true }, { fetches++; throw LucidException(8, "x") }, {}, {}) }
        runCurrent()
        assertEquals(1, fetches)
        advanceTimeBy(2 * PUSH_INTERVAL_MS - 1)
        runCurrent()
        assertEquals(1, fetches)
        advanceTimeBy(1) // 10 minutes after the first try
        runCurrent()
        assertEquals(2, fetches)
        advanceTimeBy(4 * PUSH_INTERVAL_MS - 1) // the next wait is 20 minutes
        runCurrent()
        assertEquals(2, fetches)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, fetches)
    }

    @Test fun pushLoopRetriesAtTheNormalIntervalAfterAnOtherFailure() = runTest {
        var fetches = 0
        var sent = 0
        backgroundScope.launch {
            pushLoop({ true }, { if (fetches++ == 0) throw IOException("no route") else status }, { sent++ }, {})
        }
        runCurrent()
        assertEquals(0, sent)
        advanceTimeBy(PUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(1, sent)
    }
}
