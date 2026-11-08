package dev.kuntal.cockpit.ui.shell

import android.content.Context
import dev.kuntal.cockpit.data.TripCsv
import dev.kuntal.cockpit.data.TripRecord
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes the trips as CSV into the app's own storage folder (no permission needed) and returns the line to show the owner. */
object TripExport {
    fun save(ctx: Context, trips: List<TripRecord>, zone: ZoneId = ZoneId.systemDefault()): String = try {
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "exports").apply { mkdirs() }
        val f = File(dir, "trips-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now(zone)) + ".csv")
        f.writeText(TripCsv.build(trips, zone))
        "Saved ${f.name} (${trips.size} trip${if (trips.size == 1) "" else "s"}) to the unit's storage"
    } catch (_: Exception) { "Could not save the CSV file" }
}
