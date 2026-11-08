package dev.kuntal.cockpit.data

import dev.kuntal.cockpit.vehicle.VehicleSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class TripsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 30, 21, 47).atZone(zone).toInstant().toEpochMilli()

    @Test fun mockHistoryMatchesTheDesignRows() {
        val t = MockTripStore(now, zone).all()
        assertEquals(6, t.size)
        assertEquals("Today \u00B7 18:12", TripFmt.title(t[0].startMs, now, zone))
        assertEquals("Thu 29 Oct \u00B7 19:30", TripFmt.title(t[2].startMs, now, zone))
        assertEquals("Wed 28 Oct \u00B7 09:12", TripFmt.title(t[5].startMs, now, zone))
        assertEquals(42.8, t[0].distanceKm, 0.001); assertEquals("1 h 08", TripFmt.duration(t[0].durationSec))
        assertEquals("1 h 08 \u00B7 avg 38 km/h", TripFmt.sub(t[0], SpeedUnit.KMH))
        assertEquals(6.8, t[0].avgLPer100!!, 0.05)               // the design's 2.9 L / 6.8 L per 100 km, estimated
        assertTrue(t.all { it.demo })
        assertEquals(t, MockTripStore(now, zone).all())          // deterministic
    }

    @Test fun weekFilter() {
        val t = MockTripStore(now, zone).all().first()
        assertTrue(TripFmt.isWeek(t, now, zone)); assertFalse(TripFmt.isWeek(t.copy(startMs = now - 9L * 86_400_000), now, zone))
    }

    @Test fun mockSeriesHasSpeedBoostAndEvents() {
        val t = MockTripStore(now, zone).all()[0]
        assertTrue(t.points.size > 100); assertEquals(131.0, t.points.maxOf { it.speedKmh }, 0.5)
        assertEquals(3, t.events.size); assertTrue(t.maxBoostBar!! > 0.9 && t.maxBoostBar!! <= 1.05)
    }

    @Test fun fileStoreRoundTripsAndSkipsCorruptLines() {
        val f = File.createTempFile("trips", ".tsv").also { it.deleteOnExit() }
        val s = FileTripStore(f)
        val a = MockTripStore(now, zone).all()[0].copy(id = 11, demo = false); val b = a.copy(id = 12, startMs = a.startMs - 86_400_000)
        s.add(a); s.add(b)
        f.appendText("garbage line\n12\tnotanumber\n")
        val back = s.all()
        assertEquals(listOf(11L, 12L), back.map { it.id })
        assertEquals(a, back[0]); assertEquals(a.points.size, back[0].points.size)
        s.delete(11); assertEquals(listOf(12L), s.all().map { it.id })
        s.add(b.copy(distanceKm = 1.0)); assertEquals(1, s.all().size)                  // same id replaces
        assertEquals(1.0, s.all()[0].distanceKm, 0.0)
    }

    @Test fun fileStoreKeepsOnlyTheNewestN() {
        val f = File.createTempFile("trips", ".tsv").also { it.deleteOnExit() }
        val s = FileTripStore(f, maxTrips = 3)
        val base = MockTripStore(now, zone).all()[0].copy(demo = false)
        for (i in 1..5) s.add(base.copy(id = i.toLong(), startMs = base.startMs + i * 1000))
        assertEquals(listOf(5L, 4L, 3L), s.all().map { it.id })
    }

    @Test fun missingFileIsEmpty() = assertTrue(FileTripStore(File("/nonexistent/dir/trips.tsv")).all().isEmpty())

    @Test fun compositeAddsDemoOnlyWhenAsked() {
        val real = MemoryTripStore()
        assertEquals(0, CompositeTripStore(real, null).all().size)
        assertEquals(6, CompositeTripStore(real, MockTripStore(now, zone)).all().size)
        CompositeTripStore(real, null).add(MockTripStore(now, zone).all()[0].copy(id = 99, demo = false))
        assertEquals(listOf(99L), real.all().map { it.id })
    }

    // ---- recorder ----
    private fun smp(tMs: Long, kmh: Double, mapKpa: Double? = null) = VehicleSample(tsMs = tMs, speedKmh = kmh, mapKpa = mapKpa, baroKpa = if (mapKpa != null) 100.0 else null)

    @Test fun recorderStartsOnMovementAndEndsAfterAStop() {
        val r = TripRecorder { 5 }
        var done: TripRecord? = null
        var t = 0L
        // 10 minutes at 60 km/h = 10 km, boost to +0.8 bar once
        while (t <= 600_000L) { r.accept(smp(t, 60.0, if (t == 300_000L) 180.0 else 95.0))?.let { done = it }; t += 1_000 }
        assertNull(done)
        // then standing still for 3 minutes ends the trip
        while (t <= 800_000L) { r.accept(smp(t, 0.0))?.let { done = it }; t += 1_000 }
        val d = done!!
        assertEquals(10.0, d.distanceKm, 0.2); assertEquals(60.0, d.topKmh, 0.0); assertEquals(0.8, d.maxBoostBar!!, 0.001)
        assertTrue(d.events.any { it.kind == TripEventKind.PeakBoost }); assertTrue(d.points.size >= 100); assertFalse(d.demo)
        assertEquals(5L, d.id)
    }

    @Test fun recorderDropsTinyTripsAndIdleNoise() {
        val r = TripRecorder()
        for (t in 0L..60_000L step 1_000) assertNull(r.accept(smp(t, 0.0)))
        for (t in 61_000L..70_000L step 1_000) r.accept(smp(t, 20.0))           // 10 s at 20 km/h = 55 m
        assertNull(r.flush())
    }

    @Test fun recorderClosesATripWhenTheAdapterGoesAway() {
        val r = TripRecorder()
        for (t in 0L..300_000L step 1_000) r.accept(smp(t, 50.0))
        assertNotNull(r.accept(smp(400_000, 50.0)))                              // 100 s of silence
        assertNull(r.flush())
    }

    @Test fun recorderIgnoresSamplesWithoutSpeed() = assertNull(TripRecorder().accept(VehicleSample(tsMs = 1)))
}
