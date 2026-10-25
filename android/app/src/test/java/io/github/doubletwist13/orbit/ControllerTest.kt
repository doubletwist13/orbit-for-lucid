package io.github.doubletwist13.orbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
