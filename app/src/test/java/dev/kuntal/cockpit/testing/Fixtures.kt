package dev.kuntal.cockpit.testing

import dev.kuntal.cockpit.vehicle.VehicleSample

/** Test data builders. Defaults describe a plausible idle car so a test only states what it is about. */
fun sample(
    tsMs: Long = 0,
    speedKmh: Double? = 0.0,
    rpm: Double? = 850.0,
    coolantC: Double? = 90.0,
    mapKpa: Double? = 100.0,
    baroKpa: Double? = 100.0,
    fuelPct: Double? = 60.0,
) = VehicleSample(tsMs = tsMs, speedKmh = speedKmh, rpm = rpm, coolantC = coolantC, mapKpa = mapKpa, baroKpa = baroKpa, fuelPct = fuelPct)

/** Samples at a fixed rate from [fromMs] (inclusive) to [toMs] (exclusive) at a constant speed. */
fun cruise(kmh: Double, fromMs: Long, toMs: Long, stepMs: Long = 50, rpm: Double = 2000.0): List<VehicleSample> =
    (fromMs until toMs step stepMs).map { sample(tsMs = it, speedKmh = kmh, rpm = rpm) }

/** Distance in km that [kmh] covers in [ms]: the number a trip counter must come close to. */
fun kmCovered(kmh: Double, ms: Long): Double = kmh * ms / 3_600_000.0
