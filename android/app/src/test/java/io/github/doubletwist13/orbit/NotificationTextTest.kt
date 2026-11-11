package io.github.doubletwist13.orbit

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {
    private val status = VehicleStatus(
        vehicleId = "v1", nickname = "", socPercent = 79, locked = true, awake = true,
        chargeState = "NOT_CONNECTED", hvacOn = false, updatedMs = 0,
    )

    @Test fun showsChargeLockAndChargingPower() {
        assertEquals("79% 🔒", notificationText(true, status))
        val charging = status.copy(chargeState = "CHARGING", chargeRateKw = 7.2, locked = false)
        assertEquals("79% 🔓 ⚡7 kW", notificationText(true, charging))
    }

    @Test fun leavesOutWhatTheCarDoesNotReport() {
        val unknown = status.copy(locked = null, socPercent = 255)
        assertEquals("⚡", notificationText(true, unknown.copy(chargeState = "CHARGING")))
        assertEquals("79%", notificationText(true, status.copy(locked = null)))
    }

    @Test fun fallsBackToThePlainTextWithNothingToShow() {
        assertEquals(BRIDGE_IDLE_TEXT, notificationText(false, status))
        assertEquals(BRIDGE_IDLE_TEXT, notificationText(true, null))
        assertEquals(BRIDGE_IDLE_TEXT, notificationText(true, status.copy(locked = null, socPercent = 255)))
    }
}
