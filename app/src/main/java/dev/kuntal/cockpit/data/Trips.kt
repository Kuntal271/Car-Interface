package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** One point of a trip's speed / boost history, every [TripRecord.STEP_SEC] seconds. */
data class TripPoint(val speedKmh: Double, val boostBar: Double?, val rpm: Int? = null)

enum class TripEventKind { PeakBoost, TopSpeed, LongestStop }
data class TripEvent(val kind: TripEventKind, val text: String, val atSec: Int)

data class TripRecord(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val avgKmh: Double,
    val topKmh: Double,
    val maxBoostBar: Double?,
    val fuelL: Double?,                       // estimated: there is no fuel-rate PID
    val points: List<TripPoint>,
    val events: List<TripEvent>,
    val demo: Boolean = false,
) {
    val durationSec: Int get() = ((endMs - startMs) / 1000).toInt()
    val avgLPer100: Double? get() = if (fuelL != null && distanceKm > 0.5) fuelL / distanceKm * 100.0 else null
    companion object { const val STEP_SEC = 5 }
}

interface TripStore {
    fun all(): List<TripRecord>                // newest first
    fun add(t: TripRecord)
    fun delete(id: Long)
}

/** Trip titles and durations as the design writes them: "Today · 18:12", "Thu 29 Oct · 19:30", "1 h 08". */
object TripFmt {
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    fun at(ms: Long, zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    fun title(startMs: Long, nowMs: Long, zone: ZoneId): String {
        val s = at(startMs, zone); val n = at(nowMs, zone)
        val day = if (s.toLocalDate() == n.toLocalDate()) "Today" else DAY.format(s)
        return "$day \u00B7 ${Fmt.clock(s.hour, s.minute)}"
    }
    fun range(startMs: Long, endMs: Long, zone: ZoneId): String {
        val s = at(startMs, zone); val e = at(endMs, zone)
        return "${Fmt.clock(s.hour, s.minute)} \u2013 ${Fmt.clock(e.hour, e.minute)}"
    }
    fun duration(sec: Int): String = "${sec / 3600} h ${((sec % 3600) / 60).toString().padStart(2, '0')}"
    fun sub(t: TripRecord, u: SpeedUnit): String = "${duration(t.durationSec)} \u00B7 avg ${Fmt.speed(t.avgKmh, u)} ${Units.label(u)}"
    fun clockAt(t: TripRecord, offsetSec: Int, zone: ZoneId): String { val e = at(t.startMs + offsetSec * 1000L, zone); return Fmt.clock(e.hour, e.minute) }
    /** The event line as the design words it; the rpm comes from the trip's own series when it has one. */
    fun eventText(t: TripRecord, e: TripEvent, boostLabel: (Double) -> String, speed: (Double) -> String): String = when (e.kind) {
        TripEventKind.PeakBoost -> t.points.getOrNull(e.atSec / TripRecord.STEP_SEC)?.rpm?.let { "Peak boost at ${Fmt.grouped(it)} rpm" } ?: t.maxBoostBar?.let { "Peak boost ${boostLabel(it)}" } ?: e.text
        TripEventKind.TopSpeed -> "Top speed ${speed(t.topKmh)}"
        TripEventKind.LongestStop -> e.text
    }
    fun isWeek(t: TripRecord, nowMs: Long, zone: ZoneId): Boolean = at(t.startMs, zone).toLocalDate().isAfter(at(nowMs, zone).toLocalDate().minusDays(7))
}

/** A readable history for demos: six trips built from a seed, newest first. */
class MockTripStore(private val nowMs: Long, private val zone: ZoneId = ZoneId.systemDefault(), seed: Long = 1L) : TripStore {
    private val trips = mutableListOf<TripRecord>().apply {
        // (days ago, start hour, start minute, duration min, distance in 0.1 km, avg km/h, top km/h)
        val rows = listOf(
            intArrayOf(0, 18, 12, 68, 428, 38, 131), intArrayOf(0, 9, 5, 41, 196, 29, 96), intArrayOf(1, 19, 30, 82, 601, 44, 118),
            intArrayOf(1, 8, 50, 38, 172, 27, 88), intArrayOf(2, 18, 40, 91, 470, 31, 104), intArrayOf(2, 9, 12, 44, 224, 30, 92),
        )
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        rows.forEachIndexed { n, r ->
            val start = today.minusDays(r[0].toLong()).atTime(r[1], r[2]).atZone(zone).toInstant().toEpochMilli()
            add(synth(n + 1L, start, r[3] * 60, r[4] / 10.0, r[5].toDouble(), r[6].toDouble(), seed + n, PEAKS[n]))
        }
    }
    override fun all(): List<TripRecord> = trips.toList()
    override fun add(t: TripRecord) { trips.add(0, t) }
    override fun delete(id: Long) { trips.removeAll { it.id == id } }

    companion object {
        private val PEAKS = doubleArrayOf(1.04, .92, .98, .85, .90, .88)

        // The shape of the design's example trip (80 points, svg y: 111 = standing, 41 = top speed; boost 84.1 = 0 bar, 56 = +1.04 bar).
        private val TEMPLATE_Y_SPEED = intArrayOf(
            111, 111, 109, 107, 105, 103, 101, 99, 97, 95, 94, 92, 90, 88, 86, 84, 82, 80, 78, 76, 74, 72, 70, 68, 66, 64, 62, 60, 58, 56, 54, 52, 51, 49, 47, 45,
            43, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 44, 48, 51, 55, 58, 62, 65, 69, 72, 76, 79, 83, 86, 90, 94, 97, 100, 104, 108, 111, 111, 111,
            111, 111, 111, 111, 111, 111, 111, 111,
        )
        private val TEMPLATE_Y_BOOST = intArrayOf(
            84, 84, 83, 74, 66, 57, 56, 56, 64, 59, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56, 56,
            56, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 92, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102, 102,
            102, 101, 101, 101, 101, 101, 101, 101, 101, 101, 101, 101,
        )
        private fun lerp(a: IntArray, f: Double): Double {
            val x = f.coerceIn(0.0, 1.0) * (a.size - 1); val i = x.toInt().coerceAtMost(a.size - 2)
            return a[i] + (a[i + 1] - a[i]) * (x - i)
        }

        /**
         * A plausible speed / boost series for a trip: accelerate, cruise with wiggles, brake to a stop. Deterministic in [seed].
         * The peak boost, top speed and longest stop sit at fixed fractions of the trip (the design's 6, 31 and 59 minutes of 68).
         */
        fun synth(id: Long, startMs: Long, durSec: Int, km: Double, avg: Double, top: Double, seed: Long, peakBar: Double = 1.04): TripRecord {
            val n = max(8, durSec / TripRecord.STEP_SEC)
            val peakI = Math.round(n * 0.088).toInt(); val topI = Math.round(n * 0.456).toInt(); val stopI = Math.round(n * 0.868).toInt()
            val pts = List(n) { i ->
                val f = i / (n - 1.0)
                val v0 = lerp(TEMPLATE_Y_SPEED, f)
                var v = top * ((111.0 - v0) / 70.0).coerceIn(0.0, 1.0)
                if (seed != 1L && v > 5) v = (v + 2.5 * Math.sin(i * 0.9 + seed)).coerceIn(0.0, top)
                if (i == topI) v = top else if (v > top - .5) v = top - .5
                var boost = (84.1 - lerp(TEMPLATE_Y_BOOST, f)) / 27.0
                if (boost > 0) boost *= peakBar / 1.04
                var rpm = if (v < 5) 0 else 1_500 + (v * 22).toInt()
                if (i == peakI) { boost = peakBar; rpm = 4_800 } else if (boost > peakBar - .005) boost = peakBar - .005
                TripPoint(((v * 10).roundToInt() / 10.0), ((boost * 100).roundToInt() / 100.0), rpm)
            }
            val events = listOf(
                TripEvent(TripEventKind.PeakBoost, "Peak boost", peakI * TripRecord.STEP_SEC),
                TripEvent(TripEventKind.TopSpeed, "Top speed ${top.roundToInt()} km/h", topI * TripRecord.STEP_SEC),
                TripEvent(TripEventKind.LongestStop, "Longest stop \u00B7 9 min", stopI * TripRecord.STEP_SEC),
            )
            return TripRecord(id, startMs, startMs + durSec * 1000L, km, avg, top, pts.maxOfOrNull { it.boostBar ?: -9.0 }, km * 0.068, pts, events, demo = true)
        }
    }
}

/**
 * Trips on disk: one tab-separated line per trip in [file] (no JSON library needed, so it runs in JVM tests). Corrupt lines are
 * skipped, never fatal. Only trips from a real vehicle connection are meant to land here; the recorder never feeds it simulated data.
 */
class FileTripStore(private val file: File, private val maxTrips: Int = 200) : TripStore {
    override fun all(): List<TripRecord> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { decode(it) }.sortedByDescending { it.startMs }
    }

    override fun add(t: TripRecord) {
        val keep = all().filter { it.id != t.id }
        val lines = (listOf(t) + keep).sortedByDescending { it.startMs }.take(maxTrips).map { encode(it) }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(lines.joinToString("\n") + "\n")
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    override fun delete(id: Long) {
        val keep = all().filter { it.id != id }
        file.writeText(keep.joinToString("\n") { encode(it) } + if (keep.isEmpty()) "" else "\n")
    }

    companion object {
        private fun d(v: Double?) = v?.toString() ?: "-"
        private fun clean(s: String) = s.replace('\t', ' ').replace(';', ' ').replace(',', ' ').replace('\n', ' ')
        fun encode(t: TripRecord): String = listOf(
            t.id, t.startMs, t.endMs, t.distanceKm, t.avgKmh, t.topKmh, d(t.maxBoostBar), d(t.fuelL),
            t.points.joinToString(";") { "${it.speedKmh},${d(it.boostBar)},${it.rpm ?: "-"}" },
            t.events.joinToString(";") { "${it.kind.name},${it.atSec},${clean(it.text)}" },
        ).joinToString("\t")

        fun decode(line: String): TripRecord? = try {
            val f = line.split('\t')
            if (f.size < 10) null else TripRecord(
                id = f[0].toLong(), startMs = f[1].toLong(), endMs = f[2].toLong(), distanceKm = f[3].toDouble(), avgKmh = f[4].toDouble(), topKmh = f[5].toDouble(),
                maxBoostBar = f[6].toDoubleOrNull(), fuelL = f[7].toDoubleOrNull(),
                points = f[8].split(';').filter { it.isNotEmpty() }.map { p -> val q = p.split(','); TripPoint(q[0].toDouble(), q.getOrNull(1)?.toDoubleOrNull(), q.getOrNull(2)?.toIntOrNull()) },
                events = f[9].split(';').filter { it.isNotEmpty() }.map { e -> val q = e.split(',', limit = 3); TripEvent(TripEventKind.valueOf(q[0]), q[2], q[1].toInt()) },
                demo = false,
            )
        } catch (_: Exception) { null }
    }
}

/** Real trips plus, when demo data is on, the mock history (marked demo). Newest first. */
class CompositeTripStore(private val real: TripStore, private val demo: TripStore?) : TripStore {
    override fun all(): List<TripRecord> = (real.all() + (demo?.all() ?: emptyList())).sortedByDescending { it.startMs }
    override fun add(t: TripRecord) = real.add(t)
    override fun delete(id: Long) { real.delete(id); demo?.delete(id) }
}

/**
 * Turns the live speed samples of a real connection into [TripRecord]s: a trip starts when the car moves, ends after
 * [STOP_END_SEC] of standstill, and is dropped if shorter than [MIN_KM]. The caller must not feed it simulated samples.
 */
class TripRecorder(private val nextId: () -> Long = { System.currentTimeMillis() }) {
    companion object { const val STOP_END_SEC = 180; const val MIN_KM = 0.3; const val MOVE_KMH = 3.0 }

    private var startMs = 0L
    private var lastMs = 0L
    private var lastKmh = 0.0
    private var km = 0.0
    private var top = 0.0
    private var maxBoost: Double? = null
    private var stopSec = 0.0
    private var longestStop = 0.0
    private var active = false
    private var nextPointMs = 0L
    private val points = ArrayList<TripPoint>()
    private var peakAt = 0
    private var topAt = 0

    /** Returns a finished trip when this sample closes one. */
    fun accept(s: VehicleSample): TripRecord? {
        val v = s.speedKmh ?: return null
        val t = s.tsMs
        if (!active) {
            if (v < MOVE_KMH) return null
            reset(); active = true; startMs = t; lastMs = t; lastKmh = v; nextPointMs = t
        }
        val dt = (t - lastMs).coerceAtLeast(0)
        if (dt > 30_000) { val r = finish(lastMs); active = false; return r }          // adapter was gone: close what we have
        km += (v + lastKmh) / 2.0 * dt / 3_600_000.0
        if (v >= MOVE_KMH) stopSec = 0.0 else { stopSec += dt / 1000.0; longestStop = max(longestStop, stopSec) }
        if (v > top) { top = v; topAt = ((t - startMs) / 1000).toInt() }
        s.boostKpa?.let { b -> val bar = b / 100.0; if (maxBoost.let { m -> m == null || bar > m }) { maxBoost = bar; peakAt = ((t - startMs) / 1000).toInt() } }
        if (t >= nextPointMs) { points.add(TripPoint(Math.round(v * 10) / 10.0, s.boostKpa?.let { Math.round(it) / 100.0 }, s.rpm?.roundToInt())); nextPointMs += TripRecord.STEP_SEC * 1000L }
        lastMs = t; lastKmh = v
        if (stopSec >= STOP_END_SEC) { val r = finish(t - (stopSec * 1000).toLong()); active = false; return r }
        return null
    }

    /** Close the trip now (ignition off, app stopping). */
    fun flush(): TripRecord? { if (!active) return null; val r = finish(lastMs); active = false; return r }

    private fun finish(endMs: Long): TripRecord? {
        if (km < MIN_KM) return null
        val durH = max(1L, endMs - startMs) / 3_600_000.0
        val ev = buildList {
            if (maxBoost != null) add(TripEvent(TripEventKind.PeakBoost, "Peak boost", peakAt))
            add(TripEvent(TripEventKind.TopSpeed, "Top speed ${top.roundToInt()} km/h", topAt))
            if (longestStop >= 60) add(TripEvent(TripEventKind.LongestStop, "Longest stop \u00B7 ${(longestStop / 60).roundToInt()} min", ((endMs - startMs) / 1000).toInt()))
        }
        return TripRecord(nextId(), startMs, endMs, km, km / durH, top, maxBoost, null, points.toList(), ev)
    }

    private fun reset() { km = 0.0; top = 0.0; maxBoost = null; stopSec = 0.0; longestStop = 0.0; points.clear(); peakAt = 0; topAt = 0 }
}

/** CSV for "Export": one row per trip, then nothing fancy. Units are fixed (km, km/h, bar) so the file means the same everywhere. */
object TripCsv {
    fun build(trips: List<TripRecord>, zone: ZoneId): String = buildString {
        append("start,end,distance_km,avg_kmh,top_kmh,max_boost_bar,fuel_l_est,demo\n")
        for (t in trips) append(listOf(
            TripFmt.at(t.startMs, zone).toString(), TripFmt.at(t.endMs, zone).toString(), Fmt.dec(t.distanceKm, 2), Fmt.dec(t.avgKmh, 1), Fmt.dec(t.topKmh, 1),
            t.maxBoostBar?.let { Fmt.dec(it, 2) } ?: "", t.fuelL?.let { Fmt.dec(it, 2) } ?: "", t.demo.toString(),
        ).joinToString(",")).append('\n')
    }
}
