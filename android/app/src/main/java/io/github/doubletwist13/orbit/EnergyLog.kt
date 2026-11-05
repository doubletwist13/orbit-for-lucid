package io.github.doubletwist13.orbit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** One reading of the car's odometer and the energy left in its battery, kept to work out efficiency. */
data class EnergySample(val timeMs: Long, val odometerKm: Double, val energyKwh: Double, val charging: Boolean)

/**
 * A plain text log of [EnergySample]s, one per line, on the phone only. The Lucid API has no trip or efficiency data
 * (docs/research/), but distance and energy used are both in every status read, so efficiency can be worked out here.
 * A reading is kept only when the car moved or its energy changed, or a parked car would fill the log with copies.
 */
class EnergyLog(private val file: File, private val max: Int = 20_000) {
    private var cache: MutableList<EnergySample>? = null

    @Synchronized fun samples(): List<EnergySample> = load().toList()

    /** True when the reading was new and was kept. */
    @Synchronized fun add(s: EnergySample): Boolean {
        val list = load()
        val last = list.lastOrNull()
        if (last != null && last.odometerKm == s.odometerKm && last.energyKwh == s.energyKwh &&
            last.charging == s.charging) return false
        list += s
        if (list.size > max) {
            // drop the oldest quarter in one rewrite, not one line at a time
            list.subList(0, max / 4).clear()
            file.writeText(list.joinToString("") { line(it) })
        } else {
            file.appendText(line(s))
        }
        return true
    }

    private fun line(s: EnergySample) = "${s.timeMs},${s.odometerKm},${s.energyKwh},${if (s.charging) 1 else 0}\n"

    private fun load(): MutableList<EnergySample> = cache ?: run {
        val list = mutableListOf<EnergySample>()
        if (file.exists()) {
            file.forEachLine { l ->
                val p = l.split(',')
                // a line cut short by a crash is skipped, not fatal
                if (p.size == 4) {
                    val t = p[0].toLongOrNull()
                    val o = p[1].toDoubleOrNull()
                    val e = p[2].toDoubleOrNull()
                    if (t != null && o != null && e != null) list += EnergySample(t, o, e, p[3] == "1")
                }
            }
        }
        list.also { cache = it }
    }
}

/** Names a car's log by a hash of its id, as the id is account data. */
internal fun energyKey(vehicleId: String): String =
    MessageDigest.getInstance("SHA-256").digest(vehicleId.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

/** The log file for a car; a null [key] is the single log from before logs were kept per car. */
internal fun energyFile(dir: File, key: String?) = File(dir, if (key == null) "energy.log" else "energy-$key.log")

/** The first car seen takes over the pre-per-car log, unless it already has one. */
internal fun adoptLegacyLog(dir: File, key: String) {
    val to = energyFile(dir, key)
    if (!to.exists()) energyFile(dir, null).renameTo(to)
}

/**
 * Keeps the odometer and battery energy of each new reading, for the efficiency figures on the details screen, and
 * publishes the status with the figures for its car in one step. Reads can overlap (the push loop and a command),
 * so publishing apart from the log would let one car's status land beside another car's rows.
 * [key] is the car the log is for now (null for the pre-per-car log); [saveKey] remembers it across restarts.
 */
class EnergyTracker(
    private val dir: File,
    private var key: String?,
    private val saveKey: (String) -> Unit,
    private val state: MutableStateFlow<UiState>,
) {
    private var energy = EnergyLog(energyFile(dir, key))

    /** The id [idKey] was worked out for; it never changes between readings, so the hash is done once. */
    private var hashedId: String? = null
    private var idKey = ""

    @Synchronized fun record(s: VehicleStatus) {
        var changed = false
        if (s.vehicleId != hashedId) {
            hashedId = s.vehicleId
            idKey = energyKey(s.vehicleId)
        }
        // switch before the null checks, so the figures follow the car even if this reading has no odometer
        if (idKey != key) {
            if (key == null) adoptLegacyLog(dir, idKey)
            key = idKey
            saveKey(idKey)
            energy = EnergyLog(energyFile(dir, idKey))
            changed = true
        }
        val odometer = s.odometerKm
        val kwh = s.energyKwh
        if (odometer != null && kwh != null &&
            energy.add(EnergySample(System.currentTimeMillis(), odometer, kwh, s.chargeState == "CHARGING"))) {
            changed = true
        }
        // unchanged, the rows already in the state are for this car's log
        state.update { if (changed) it.copy(status = s, trips = tripRows()) else it.copy(status = s) }
    }

    @Synchronized fun tripRows(): List<Pair<String, String>> {
        val samples = energy.samples()
        if (samples.isEmpty()) return emptyList()
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
        return listOf(
            "Since last charge" to efficiencySince(samples, lastChargeEndMs(samples)).text(),
            "Last 30 mi" to efficiencyOverLast(samples, 30 / Efficiency.KM_TO_MI).text(),
            "All logged (${date.format(samples.first().timeMs)} on)" to efficiencySince(samples, 0).text(),
        )
    }
}

/** Distance driven and energy used over some span of readings. */
data class Efficiency(val km: Double, val kwh: Double) {
    val miles get() = km * KM_TO_MI

    /** Null until there is enough driving to say anything: a short hop or a tiny energy change is mostly noise. */
    val milesPerKwh get() = if (km >= MIN_KM && kwh >= MIN_KWH) miles / kwh else null

    fun text(): String {
        val e = milesPerKwh ?: return "not enough driving yet"
        return String.format(Locale.US, "%.1f mi/kWh (%.0f Wh/mi) over %.0f mi", e, 1000 / e, miles)
    }

    companion object {
        const val KM_TO_MI = 0.621371
        const val MIN_KM = 3.0
        const val MIN_KWH = 0.5
    }
}

/** Energy rising by more than this between two readings is a charge, even if no reading said "charging". */
private const val CHARGE_JUMP_KWH = 0.5

/**
 * A pair of consecutive readings counts as driving when the car moved and neither reading is a charge. A pair spanning
 * a charge (flagged, or the energy rose) is skipped, since the energy used there cannot be told from the energy added.
 */
private fun drive(a: EnergySample, b: EnergySample): Efficiency? {
    if (a.charging || b.charging || b.energyKwh - a.energyKwh > CHARGE_JUMP_KWH) return null
    if (b.odometerKm <= a.odometerKm) return null
    return Efficiency(b.odometerKm - a.odometerKm, a.energyKwh - b.energyKwh)
}

/** Driving since [fromMs]: every pair that ends after it. */
internal fun efficiencySince(samples: List<EnergySample>, fromMs: Long): Efficiency {
    var km = 0.0
    var kwh = 0.0
    for (i in 1 until samples.size) {
        if (samples[i].timeMs <= fromMs) continue
        drive(samples[i - 1], samples[i])?.let { km += it.km; kwh += it.kwh }
    }
    return Efficiency(km, kwh)
}

/** When the car last finished charging, as the time of the first reading after the last charge; 0 if never seen. */
internal fun lastChargeEndMs(samples: List<EnergySample>): Long {
    var end = 0L
    for (i in 1 until samples.size) {
        val a = samples[i - 1]
        val b = samples[i]
        if (a.charging || b.charging || b.energyKwh - a.energyKwh > CHARGE_JUMP_KWH) end = b.timeMs
    }
    return end
}

/** The most recent driving that adds up to [km], working back from the newest reading. */
internal fun efficiencyOverLast(samples: List<EnergySample>, km: Double): Efficiency {
    var dist = 0.0
    var energy = 0.0
    for (i in samples.size - 1 downTo 1) {
        val d = drive(samples[i - 1], samples[i]) ?: continue
        dist += d.km
        energy += d.kwh
        if (dist >= km) break
    }
    return Efficiency(dist, energy)
}
