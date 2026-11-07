package dev.kuntal.cockpit.data

import java.time.ZoneId

/**
 * Everything that has no real source yet, in one place. With [demo] on, each source is a deterministic mock (labelled as such on
 * screen); with it off the mock-backed ones are null and the screens show "Waiting for ..." instead of inventing numbers. The one
 * exception is [trips]: real recorded trips are always there, demo trips are added only with [demo].
 */
class CockpitData(
    val demo: Boolean,
    realTrips: TripStore,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    seed: Long = 1L,
    val canbox: CanboxSource? = if (demo) MockCanbox(seed) else null,
    val media: MediaSource? = if (demo) MockMediaSource() else null,
    val audio: AudioSettings? = if (demo) MockAudioSettings(AudioState(preset = "Punch", bands = AudioPresets.bands("Punch")!!)) else null,
    val diag: DiagSource? = if (demo) MockDiagSource() else null,
    val cruise: CruiseSource? = if (demo) MockCruise() else null,
) {
    val radar: RadarSource? = canbox?.let { CanboxRadar(it) }
    val trips: TripStore = CompositeTripStore(realTrips, if (demo) MockTripStore(nowMs, zone, seed) else null)
}

/** In-memory store for tests and snapshot mode. */
class MemoryTripStore : TripStore {
    private val l = mutableListOf<TripRecord>()
    override fun all() = l.sortedByDescending { it.startMs }
    override fun add(t: TripRecord) { l.removeAll { it.id == t.id }; l.add(t) }
    override fun delete(id: Long) { l.removeAll { it.id == id } }
}
