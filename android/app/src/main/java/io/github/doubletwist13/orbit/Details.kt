package io.github.doubletwist13.orbit

import mobilegateway.protos.VehicleStateServiceOuterClass.Vehicle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One block of the details screen: a title and its label and value rows. */
data class DetailGroup(val title: String, val rows: List<Pair<String, String>>)

/**
 * Everything the car last reported that is worth reading, grouped by topic, as text. The watch gets a few of these;
 * this is the rest, for the details screen. Left out on purpose: the VIN, the nickname and other account data, and the
 * location, none of which this screen needs (docs/protocol.md keeps them off the watch too).
 */
internal fun vehicleDetails(v: Vehicle): List<DetailGroup> {
    val st = v.state
    val cfg = v.config
    return buildList {
        add(DetailGroup("Car", listOf(
            "Model" to "${cfg.model.nice()} ${cfg.variant.nice()}",
            "Edition" to cfg.edition.nice(),
            "Battery" to "${cfg.battery.nice()} / ${st.battery.batteryPackType.nice()}",
            "Cells" to st.battery.batteryCellType.nice(),
            "Wheels" to cfg.wheels.nice(),
            "Power state" to st.power.nice(),
            "Drive mode" to st.driveMode.nice(),
            "Gear" to st.gearPosition.nice(),
            "Last report" to time(st.lastUpdatedMs),
            "Software" to st.chassis.softwareVersion.ifEmpty { "--" },
            "Update" to "${st.softwareUpdate.updateAvailable.nice()} ${st.softwareUpdate.versionAvailable} " +
                "(${st.softwareUpdate.state.nice()}, ${st.softwareUpdate.percentComplete}%, " +
                "download ${st.softwareUpdate.downloadStatus.nice()})",
            "Faults" to st.faultState.mpbFaultStatus.nice(),
            "Low power mode" to st.lowPowerModeStatus.nice(),
            "Privacy mode" to st.privacyMode.nice(),
            "Keyless driving" to st.keylessDriving.nice(),
            "Cloud" to st.cloudConnection.nice(),
            "Network" to "LTE ${st.tcuInternet.lteType.nice()} ${st.tcuInternet.lteStatus.nice()}, " +
                "Wi-Fi ${st.tcuInternet.wifiStatus.nice()}",
            "TCU" to st.tcu.nice(),
        )))
        val b = st.battery
        add(DetailGroup("Battery", listOf(
            "Charge" to "${b.chargePercent.one()} %",
            "Energy" to "${b.kwhr.one()} of ${b.capacityKwhr.one()} kWh",
            // kilometres: 510 at 84% of a 377 mi (607 km) EPA rating; the car scales its rated range by charge
            "Current Range (EPA Rated)" to distance(b.remainingRange),
            "Unavailable range" to distance(b.unavailableRange),
            "Health level" to b.batteryHealthLevel.one(),
            "Health warning" to b.batteryHealth.nice(),
            "Low / critical" to "${b.lowChargeLevel.nice()} / ${b.criticalChargeLevel.nice()}",
            "Cell temp min" to temp(b.minCellTemp),
            "Cell temp max" to temp(b.maxCellTemp),
            "Preconditioning" to "${b.preconditioningStatus.nice()}, ${b.preconditioningTimeRemaining} min left",
            "BMU software" to "${b.bmuSoftwareVersionMajor}.${b.bmuSoftwareVersionMinor}.${b.bmuSoftwareVersionMicro}",
        )))
        val c = st.charging
        add(DetailGroup("Charging", listOf(
            "State" to c.chargeState.nice(),
            "Energy type" to c.energyType.nice(),
            "Power" to "${c.chargeRateKwhPrecise.one()} kW, port ${c.portPower.one()}",
            "Added per hour" to "${c.chargeRateMphPrecise.one()} mi/h",
            "Added per minute" to "${c.chargeRateMilesMinPrecise.one()} mi/min",
            "This session" to "${c.chargeSessionKwh.one()} kWh, ${c.chargeSessionMi.one()} mi",
            "Time left" to "${c.sessionMinutesRemaining} min",
            "Limit" to "${c.chargeLimitPercent.one()} % (${c.chargeLimit})",
            "Schedule" to "${c.scheduledCharge.nice()}, at ${scheduleTime(c.chargeScheduledTime)}",
            "Cable lock" to c.cableLock.nice(),
            "AC current limit" to "${c.activeSessionAcCurrentLimit} A (car ${c.energyAcCurrentLimit} A)",
            "Restart allowed" to c.chargingSessionRestartAllowed.nice(),
            "Notification" to c.chargeNotification.nice(),
        )))
        val h = st.hvac
        add(DetailGroup("Climate", listOf(
            "Power" to h.power.nice(),
            "Set point" to temp(h.frontLeftSetTemperature),
            "Interior" to temp(st.cabin.interiorTemp),
            "Exterior" to temp(st.cabin.exteriorTemp),
            "Defrost" to h.defrost.nice(),
            "Precondition" to h.preconditionStatus.nice(),
            "Keep climate" to "${h.keepClimateStatus.nice()} (${h.keepClimateCondition.nice()})",
            "Max A/C" to h.maxAcStatus.nice(),
            "Sync" to h.syncSet.nice(),
            "Rear window heat" to h.rearWindowHeatingStatus.nice(),
            "Steering heat" to "${h.steeringHeater.nice()} (${h.steeringHeaterLevel.nice()})",
            "Limited" to h.hvacLimited.nice(),
            "Driver seat" to "heat ${h.seats.driverHeatCushionZone2.nice()}, vent ${h.seats.driverVentCushion.nice()}",
            "Passenger seat" to "heat ${h.seats.frontPassengerHeatCushionZone2.nice()}, " +
                "vent ${h.seats.frontPassengerVentCushion.nice()}",
            "Rear seats" to "${h.seats.rearPassengerHeatLeft.nice()} / ${h.seats.rearPassengerHeatCenter.nice()} / " +
                h.seats.rearPassengerHeatRight.nice(),
        )))
        val body = st.body
        add(DetailGroup("Openings", listOf(
            "Locks" to body.doorLocks.nice(),
            "Front left door" to body.frontLeftDoor.nice(),
            "Front right door" to body.frontRightDoor.nice(),
            "Rear left door" to body.rearLeftDoor.nice(),
            "Rear right door" to body.rearRightDoor.nice(),
            "Frunk" to body.frontCargo.nice(),
            "Trunk" to body.rearCargo.nice(),
            "Charge port" to body.chargePort.nice(),
            "Windows" to body.allWindowsPosition.nice(),
            "Window FL / FR" to "${body.windowPosition.leftFront.nice()} / ${body.windowPosition.rightFront.nice()}",
            "Window RL / RR" to "${body.windowPosition.leftRear.nice()} / ${body.windowPosition.rightRear.nice()}",
            "Mirrors" to "${body.frontLeftMirrorFoldState.nice()} / ${body.frontRightMirrorFoldState.nice()}",
            "Suspension" to "${body.airSuspensionLevelStatus.nice()}, ride height ${body.rideHeightStatus.nice()}",
        )))
        val ch = st.chassis
        add(DetailGroup("Tires and chassis", listOf(
            "Odometer" to distance(ch.odometerKm),
            "Speed" to "${(ch.speed * 3.6).one()} km/h",
            "Tire FL" to pressure(ch.frontLeftTirePressureBar),
            "Tire FR" to pressure(ch.frontRightTirePressureBar),
            "Tire RL" to pressure(ch.rearLeftTirePressureBar),
            "Tire RR" to pressure(ch.rearRightTirePressureBar),
            "Tire reading" to time(ch.tirePressureLastUpdated),
            "Tire warnings (hard)" to "${ch.hardWarnLeftFront.flag()}${ch.hardWarnRightFront.flag()}" +
                "${ch.hardWarnLeftRear.flag()}${ch.hardWarnRightRear.flag()} (FL FR RL RR)",
            "Tire warnings (soft)" to "${ch.softWarnLeftFront.flag()}${ch.softWarnRightFront.flag()}" +
                "${ch.softWarnLeftRear.flag()}${ch.softWarnRightRear.flag()} (FL FR RL RR)",
            "Sensors defective" to "${ch.sensorDefectiveLeftFront.nice()}, ${ch.sensorDefectiveRightFront.nice()}, " +
                "${ch.sensorDefectiveLeftRear.nice()}, ${ch.sensorDefectiveRightRear.nice()}",
            "Headlights" to ch.headlights.nice(),
            "Hazards" to ch.hazardLights.nice(),
        )))
        add(DetailGroup("Security", listOf(
            "Alarm" to "${st.alarm.status.nice()} (mode ${st.alarm.mode.nice()})",
            "Sentry" to "${st.sentryState.enablementState.nice()}, threat ${st.sentryState.threatLevel.nice()}",
            "Walk-away lock" to body.walkawayLock.nice(),
            "Access" to body.accessTypeStatus.nice(),
            "Keyfob battery" to body.keyfobBatteryStatus.nice(),
            "Living object" to body.livingObjectDetectionStatus.nice(),
            "Unattended occupant" to body.unattendedOccupantDetectionStatus.nice(),
        )))
    }
}

/** An enum value as lower-case words. The generated names repeat their type's prefix; nothing here can strip it. */
private fun Enum<*>.nice(): String =
    name.lowercase(Locale.ROOT).replace('_', ' ').let { if (it == "unrecognized") "?" else it }

/**
 * The car's scheduled charge time as a local clock time. It is minutes after midnight UTC (an 11 PM schedule in US
 * Central standard time reads 300), so it is shown in the phone's zone as of [now]; a daylight saving change since the
 * schedule was set can shift it by an hour.
 */
internal fun scheduleTime(
    utcMinutes: Int,
    zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
    now: Long = System.currentTimeMillis(),
): String {
    val midnightUtc = now - Math.floorMod(now, 86_400_000L)
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone }
        .format(Date(midnightUtc + utcMinutes * 60_000L))
}

private fun Double.one() = String.format(Locale.US, "%.1f", this)

private fun temp(c: Double) = "${c.one()} °C (${(c * 1.8 + 32).one()} °F)"

private fun pressure(bar: Double) = String.format(Locale.US, "%.2f bar (%.1f psi)", bar, bar * 14.5038)

private fun distance(km: Double) = String.format(Locale.US, "%,.0f km (%,.0f mi)", km, km * 0.621371)

private fun time(ms: Long): String = if (ms <= 0) "--" else DateFormat.getDateTimeInstance(
    DateFormat.SHORT, DateFormat.MEDIUM,
).format(Date(ms))

private fun Enum<*>.flag(): String = if (name.endsWith("_ON")) "!" else "-"
