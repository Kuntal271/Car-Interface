package dev.kuntal.cockpit.data

/*
 * Fault codes. The app NEVER reads them: a DTC request is OBD mode 03, which CommandGuard blocks (and a test proves it). This
 * file is therefore a mock-only, clearly labelled demo list so the screen can exist; a real source would need the owner to
 * decide to relax the guard, which is a safety decision and not made here.
 */

enum class DtcStatus(val label: String) { Stored("Stored"), Pending("Pending"), Permanent("Permanent") }

data class FreezeFrame(val rpm: Int, val speedKmh: Int, val coolantC: Int, val loadPct: Int, val boostKpa: Int, val fuelTrimPct: Double)

data class Dtc(val code: String, val title: String, val status: DtcStatus, val detail: String, val freeze: FreezeFrame?)

interface DiagSource {
    /** Null = this build has no way to read faults (the honest answer). */
    fun faults(): List<Dtc>?
}

/** Three invented codes, deterministic. Never real data. */
class MockDiagSource : DiagSource {
    override fun faults(): List<Dtc> = listOf(
        Dtc("P0299", "Turbocharger underboost", DtcStatus.Stored, "Boost stayed below target under load. Check hoses and the wastegate.",
            FreezeFrame(rpm = 4_200, speedKmh = 96, coolantC = 91, loadPct = 84, boostKpa = 128, fuelTrimPct = 3.1)),
        Dtc("P0171", "System too lean, bank 1", DtcStatus.Pending, "Fuel trim ran positive for several drive cycles.",
            FreezeFrame(rpm = 1_900, speedKmh = 52, coolantC = 90, loadPct = 31, boostKpa = 88, fuelTrimPct = 11.7)),
        Dtc("P0456", "Evap system leak, very small", DtcStatus.Stored, "Often a loose fuel cap.", null),
    )
}

/** The note shown on every fault view. */
object DiagNote {
    const val BLOCKED = "Mode 03 blocked"
    const val EXPLAIN = "This app never asks the car for fault codes. The list below is invented demo data."
}
