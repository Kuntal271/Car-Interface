package dev.kuntal.cockpit.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Canbox data: doors, lights, climate, parking radar, steering, tyres. The real source (the head unit's vendor service, or a UART tap)
 * does not exist yet, so everything here is behind [CanboxSource] and the only implementation is [MockCanbox]. Every field the car
 * may never send is nullable: a null is hidden by the screens, never shown as zero.
 */

enum class Door(val label: String) {
    FrontLeft("Front left door"), FrontRight("Front right door"), RearLeft("Rear left door"), RearRight("Rear right door"),
    Trunk("Tailgate"), Bonnet("Bonnet"),
}

enum class Headlights(val label: String) { Off("Off"), Parking("Parking"), Low("Low beam"), High("High beam") }
enum class Ignition(val label: String) { Off("Off"), Accessory("Accessory"), On("On") }

data class Climate(
    val acOn: Boolean, val setC: Double?, val fan: Int, val fanMax: Int = 7,
    val face: Boolean = false, val feet: Boolean = false, val defrost: Boolean = false, val recirc: Boolean = false,
)

/** Parking radar: four sensors per bumper, left to right, level 0 (clear) .. 3 (closest). */
data class Radar(val front: List<Int> = List(4) { 0 }, val rear: List<Int> = List(4) { 0 }) {
    val frontMax: Int get() = front.maxOrNull() ?: 0
    val rearMax: Int get() = rear.maxOrNull() ?: 0
    val nearestFrontM: Double? get() = distanceM(frontMax)
    val nearestRearM: Double? get() = distanceM(rearMax)

    /** Where the nearest rear obstacle is: index 0..3 of the strongest sensor (first one on a tie), or null when clear. */
    val rearIndex: Int? get() = rear.indices.maxByOrNull { rear[it] }?.takeIf { rear[it] > 0 }

    companion object {
        /** A sensor level as a distance. The Canbox sends levels, not metres, so these are the nominal band edges. */
        fun distanceM(level: Int): Double? = when (level) { 3 -> 0.6; 2 -> 1.2; 1 -> 1.8; else -> null }
        fun place(i: Int?): String = when (i) { 0 -> "Left"; 1 -> "Centre-left"; 2 -> "Centre-right"; 3 -> "Right"; else -> "Clear" }
    }
}

data class CanboxState(
    val doorsOpen: Set<Door> = emptySet(),
    val headlights: Headlights? = null,
    val fog: Boolean? = null,
    val handbrake: Boolean? = null,
    val driverBelt: Boolean? = null,
    val ignition: Ignition? = null,
    val steeringDeg: Int? = null,                 // negative = left
    val odometerKm: Int? = null,
    val outsideC: Double? = null,
    val climate: Climate? = null,
    val tyresBar: List<Double>? = null,           // front left, front right, rear left, rear right
    val radar: Radar? = null,
    val reverse: Boolean = false,
    val batteryV: Double? = null,
    val locked: Boolean? = null,                  // central locking; null = the car does not say
) {
    /** "Rear left door open", "2 doors open", or null when everything is shut. */
    val doorSummary: String?
        get() = when (doorsOpen.size) { 0 -> null; 1 -> doorsOpen.first().label + " open"; else -> "${doorsOpen.size} doors open" }
}

interface CanboxSource {
    /** Emits when something changes. Never completes. */
    fun states(): Flow<CanboxState>
}

/**
 * Scripted, deterministic car: a pure function of time ([at]), so JVM tests and snapshot screenshots see exactly the same car.
 * A loop of [LOOP_MS]: the rear left door opens and closes, the climate set point and fan change, the car reverses towards a wall
 * (radar sweep), the tailgate opens. [seed] only changes the tyre pressures (a few hundredths of a bar) and the odometer.
 */
class MockCanbox(private val seed: Long = 1L, private val tickMs: Long = 500L) : CanboxSource {
    companion object {
        const val LOOP_MS = 90_000L

        /** Radar frames of the reverse manoeuvre: (time in loop, rear levels). */
        private val REVERSE = listOf(
            36_000L to listOf(0, 0, 0, 0), 39_000L to listOf(0, 1, 1, 0), 42_000L to listOf(1, 2, 2, 1),
            45_000L to listOf(1, 2, 3, 1), 48_000L to listOf(0, 1, 2, 0), 50_000L to listOf(0, 0, 1, 0), 52_000L to listOf(0, 0, 0, 0),
        )
    }

    private fun jitter(i: Int): Double = ((seed * 31 + i * 17) % 7 - 3) / 100.0       // -0.03 .. +0.03 bar

    fun at(tMs: Long): CanboxState {
        val t = ((tMs % LOOP_MS) + LOOP_MS) % LOOP_MS
        val doors = buildSet {
            if (t in 6_000 until 14_000) add(Door.RearLeft)
            if (t in 60_000 until 66_000) add(Door.Trunk)
        }
        val reversing = t in 36_000 until 52_000
        val rear = if (reversing) REVERSE.lastOrNull { it.first <= t }?.second ?: List(4) { 0 } else List(4) { 0 }
        val setC = when { t in 20_000 until 32_000 -> 21.5; else -> 22.0 }
        val fan = when { t in 26_000 until 32_000 -> 4; else -> 3 }
        val base = doubleArrayOf(2.3, 2.3, 2.4, 2.2)
        return CanboxState(
            doorsOpen = doors,
            headlights = Headlights.Low, fog = false, handbrake = false, driverBelt = true, ignition = Ignition.On,
            steeringDeg = (-4 + 12 * sin(t / 4000.0)).roundToInt(),
            odometerKm = 48_213 + (seed % 100).toInt(),
            outsideC = 31.0,
            climate = Climate(acOn = true, setC = setC, fan = fan, face = true, recirc = true),
            tyresBar = List(4) { ((base[it] + jitter(it)) * 100).roundToInt() / 100.0 },
            radar = Radar(front = if (reversing) List(4) { 0 } else listOf(0, 1, 1, 0), rear = rear),
            reverse = reversing,
            batteryV = 14.2,
            locked = false,
        )
    }

    override fun states(): Flow<CanboxState> = flow {
        var t = 0L
        while (true) { emit(at(t)); delay(tickMs); t += tickMs }
    }.distinctUntilChanged()
}
