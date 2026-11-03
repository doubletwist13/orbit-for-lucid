package io.github.doubletwist13.orbit

import mobilegateway.protos.VehicleStateServiceOuterClass.BatteryState
import mobilegateway.protos.VehicleStateServiceOuterClass.ChassisState
import mobilegateway.protos.VehicleStateServiceOuterClass.Gps
import mobilegateway.protos.VehicleStateServiceOuterClass.Location
import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import mobilegateway.protos.VehicleStateServiceOuterClass.VehicleConfig
import mobilegateway.protos.VehicleStateServiceOuterClass.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsTest {
    private val vehicle = Vehicle.newBuilder().setVehicleId("VIN-SECRET")
        .setConfig(VehicleConfig.newBuilder().setVin("5YJ-SECRET").setNickname("nick"))
        .setState(
            VehicleState.newBuilder()
                .setBattery(
                    BatteryState.newBuilder().setChargePercent(85.0).setMinCellTemp(23.6).setRemainingRange(510.0),
                )
                .setChassis(ChassisState.newBuilder().setOdometerKm(12_345.6).setFrontLeftTirePressureBar(2.8))
                .setGps(Gps.newBuilder().setLocation(Location.newBuilder().setLatitude(12.345678))),
        ).build()

    private fun rows(group: String) = vehicleDetails(vehicle).first { it.title == group }.rows.toMap()

    @Test fun valuesAreFormattedWithBothUnits() {
        assertEquals("12,346 km (7,671 mi)", rows("Tires and chassis")["Odometer"])
        assertEquals("2.80 bar (40.6 psi)", rows("Tires and chassis")["Tire FL"])
        assertEquals("23.6 °C (74.5 °F)", rows("Battery")["Cell temp min"])
        assertEquals("85.0 %", rows("Battery")["Charge"])
        assertEquals("510 km (317 mi)", rows("Battery")["Current Range (EPA Rated)"]) // the car reports kilometres
    }

    @Test fun anUnsetEnumReadsAsUnknownNotAsAnError() {
        assertEquals("charge state unknown", rows("Charging")["State"])
    }

    @Test fun nothingIdentifyingTheCarOrItsPlaceIsShown() {
        val all = vehicleDetails(vehicle).flatMap { g -> g.rows.flatMap { listOf(it.first, it.second) } + g.title }
        assertFalse(all.any { it.contains("SECRET") || it.contains("nick") || it.contains("12.3456") })
        assertTrue(vehicleDetails(vehicle).none { it.title.contains("Location", ignoreCase = true) })
    }
}
