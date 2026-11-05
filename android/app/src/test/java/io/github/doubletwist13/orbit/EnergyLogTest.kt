package io.github.doubletwist13.orbit

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EnergyLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun s(t: Long, km: Double, kwh: Double, charging: Boolean = false) = EnergySample(t, km, kwh, charging)

    @Test fun drivingAddsUpDistanceAndEnergy() {
        val e = efficiencySince(listOf(s(1, 100.0, 80.0), s(2, 105.0, 79.0), s(3, 110.0, 78.0)), 0)
        assertEquals(10.0, e.km, 1e-9)
        assertEquals(2.0, e.kwh, 1e-9)
        assertEquals(10 * 0.621371 / 2.0, e.milesPerKwh!!, 1e-9)
    }

    @Test fun aChargeBetweenReadingsIsNotCountedAsDriving() {
        // 20 km driven, a flagged charge, then 10 km more: only the driving counts. The pair that starts on a charging
        // reading is skipped too, so the first 5 km after the charge is lost rather than risk counting charge energy.
        val log = listOf(
            s(1, 100.0, 80.0), s(2, 110.0, 78.0), s(3, 120.0, 76.0),
            s(4, 120.0, 76.0, charging = true), s(5, 120.0, 90.0, charging = true),
            s(6, 125.0, 89.0), s(7, 130.0, 88.0),
        )
        assertEquals(25.0, efficiencySince(log, 0).km, 1e-9)
        assertEquals(5.0, efficiencySince(log, 0).kwh, 1e-9)
        // since the charge ended: only the last pair
        assertEquals(5.0, efficiencySince(log, lastChargeEndMs(log)).km, 1e-9)
    }

    @Test fun anUnflaggedEnergyJumpIsTreatedAsACharge() {
        val log = listOf(s(1, 100.0, 50.0), s(2, 110.0, 48.0), s(3, 110.0, 90.0), s(4, 120.0, 88.0))
        assertEquals(20.0, efficiencySince(log, 0).km, 1e-9)
        assertEquals(3L, lastChargeEndMs(log)) // the reading where the energy came back
    }

    @Test fun lastNKilometresWorksBackFromTheNewestReading() {
        val log = listOf(s(1, 0.0, 90.0), s(2, 10.0, 88.0), s(3, 20.0, 85.0), s(4, 30.0, 83.0))
        val e = efficiencyOverLast(log, 15.0)
        assertEquals(20.0, e.km, 1e-9) // stops after the pair that passes 15 km
        assertEquals(5.0, e.kwh, 1e-9)
    }

    @Test fun tooLittleDrivingGivesNoFigure() {
        assertNull(Efficiency(1.0, 0.3).milesPerKwh)
        assertEquals("not enough driving yet", Efficiency(0.0, 0.0).text())
        assertNotNull(Efficiency(10.0, 2.0).milesPerKwh)
    }

    @Test fun theLogKeepsOnlyChangesAndSurvivesARestart() {
        val f = tmp.newFile("energy.log")
        val log = EnergyLog(f)
        assertTrue(log.add(s(1, 100.0, 80.0)))
        assertFalse(log.add(s(2, 100.0, 80.0))) // a parked car: the same reading again
        assertTrue(log.add(s(3, 101.0, 79.9)))
        val again = EnergyLog(f).samples()
        assertEquals(listOf(s(1, 100.0, 80.0), s(3, 101.0, 79.9)), again)
    }

    @Test fun aDamagedLineIsSkippedNotFatal() {
        val f = tmp.newFile("energy.log")
        f.writeText("1,100.0,80.0,0\ngarbage\n2,101.0\n3,102.0,79.0,1\n")
        assertEquals(listOf(s(1, 100.0, 80.0), s(3, 102.0, 79.0, true)), EnergyLog(f).samples())
    }

    @Test fun eachCarGetsItsOwnLog() {
        val a = energyKey("car-a")
        val b = energyKey("car-b")
        assertEquals(a, energyKey("car-a"))
        assertTrue(a != b)
        EnergyLog(energyFile(tmp.root, a)).add(s(1, 100.0, 80.0))
        EnergyLog(energyFile(tmp.root, b)).add(s(1, 5000.0, 60.0))
        assertEquals(listOf(s(1, 100.0, 80.0)), EnergyLog(energyFile(tmp.root, a)).samples())
        assertEquals(listOf(s(1, 5000.0, 60.0)), EnergyLog(energyFile(tmp.root, b)).samples())
    }

    @Test fun theOldLogMovesToTheFirstCarOnly() {
        EnergyLog(energyFile(tmp.root, null)).add(s(1, 100.0, 80.0))
        val a = energyKey("car-a")
        adoptLegacyLog(tmp.root, a)
        assertFalse(energyFile(tmp.root, null).exists())
        assertEquals(listOf(s(1, 100.0, 80.0)), EnergyLog(energyFile(tmp.root, a)).samples())
        // a car that already has a log keeps it
        EnergyLog(energyFile(tmp.root, null)).add(s(2, 200.0, 70.0))
        adoptLegacyLog(tmp.root, a)
        assertEquals(listOf(s(1, 100.0, 80.0)), EnergyLog(energyFile(tmp.root, a)).samples())
    }

    @Test fun theStatusAndRowsPublishedTogetherAreForTheSameCar() {
        val state = MutableStateFlow(UiState())
        val tracker = EnergyTracker(tmp.root, null, {}, state)
        val a = VehicleStatus(
            vehicleId = "car-a", nickname = "a", socPercent = 80, locked = true, awake = true,
            chargeState = "NOT_CONNECTED", hvacOn = false, updatedMs = 0, odometerKm = 100.0, energyKwh = 80.0,
        )
        val b = a.copy(vehicleId = "car-b", nickname = "b", odometerKm = 5000.0, energyKwh = 60.0)
        // each car drives 10 km (6 mi) and 30 km (19 mi) on 2 kWh and 6 kWh
        tracker.record(a)
        tracker.record(a.copy(odometerKm = 110.0, energyKwh = 78.0))
        tracker.record(b)
        tracker.record(b.copy(odometerKm = 5030.0, energyKwh = 54.0))
        assertEquals("b", state.value.status!!.nickname)
        assertTrue(state.value.trips.last().second.endsWith("over 19 mi"))

        // back to car A: its status arrives with its own rows, even from a reading with no odometer or energy
        tracker.record(a.copy(odometerKm = null, energyKwh = null))
        assertEquals("a", state.value.status!!.nickname)
        assertTrue(state.value.trips.last().second.endsWith("over 6 mi"))
    }

    @Test fun anOverlongLogDropsItsOldestQuarter() {
        val f = tmp.newFile("energy.log")
        val log = EnergyLog(f, max = 8)
        for (i in 1..9) log.add(s(i.toLong(), i * 1.0, 80.0 - i))
        val kept = EnergyLog(f).samples()
        assertEquals(7, kept.size) // 9 readings, 2 dropped when it passed 8
        assertEquals(3L, kept.first().timeMs)
    }
}
