package io.github.doubletwist13.orbit

import java.io.File
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
