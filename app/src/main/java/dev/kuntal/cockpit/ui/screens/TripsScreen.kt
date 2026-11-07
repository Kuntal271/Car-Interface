package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.CockpitData
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.SpeedUnit
import dev.kuntal.cockpit.data.TripEventKind
import dev.kuntal.cockpit.data.TripFmt
import dev.kuntal.cockpit.data.TripRecord
import dev.kuntal.cockpit.data.TripResets
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.TabPills
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.WaitingCard
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import java.time.ZoneId

private enum class TripFilter(val label: String) { All("All"), Week("Week") }

/**
 * Trips: Trip A / B counters, the history (real recorded trips, plus demo ones while Demo data is on) and the selected trip's
 * detail with a speed and boost chart. Fuel figures are estimates (no fuel-rate PID). Layout is the measured design.
 */
@Composable
fun TripsScreen(
    state: State<VehicleUiState>, data: CockpitData, nowMs: Long, zone: ZoneId,
    onExport: (List<TripRecord>) -> String, onNotice: (String) -> Unit,
) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    val u = st.speedUnit; val pu = st.pressureUnit
    var version by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(TripFilter.All) }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val all = remember(data, version) { data.trips.all() }
    val shown = remember(all, filter) { if (filter == TripFilter.All) all else all.filter { TripFmt.isWeek(it, nowMs, zone) } }
    val cur = shown.getOrNull(sel.coerceIn(0, (shown.size - 1).coerceAtLeast(0)))

    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Txt("Trips", 34, 34, p.text, Modifier.at(24, 85), cond, FontWeight.SemiBold)
            Box(Modifier.at(24, 136).size(229.dp, 138.dp).card())
            Box(Modifier.at(265, 136).size(229.dp, 138.dp).card())
            Txt("Trip A", 19, 21, p.dim, Modifier.at(48, 156))
            Txt("Trip B", 19, 21, p.dim, Modifier.at(289, 156))
            Box(Modifier.at(24, 286).size(470.dp, 490.dp).card())
            Box(Modifier.at(518, 80).size(626.dp, 265.dp).card())
            Box(Modifier.at(518, 357).size(626.dp, 193.dp).card())
            Box(Modifier.at(518, 562).size(626.dp, 138.dp).card())
            Txt("Speed & boost", 19, 21, p.dim, Modifier.at(542, 370))
        }
        TabPills(309, 78, TripFilter.entries, filter, { it.label }) { filter = it; sel = 0 }
        if (data.demo) DemoMark(110, 86)

        // Trip A (real: the OBD trip counters) and Trip B (demo only: there is no odometer-based source)
        Box(Modifier.at(160, 138).size(93.dp, 56.dp).tap { TripResets.a.set(true); onNotice("Trip A reset") })
        Txt("Reset", 19, 21, p.cyan, Modifier.at(180, 156))
        ValueUnit(
            48, 223,
            value = { LiveTxt({ Fmt.distance(state.value.trip.distanceKm, u) }, 38, 34, p.text, family = cond, weight = FontWeight.Bold, tnum = true) },
            unit = { Txt(if (u == SpeedUnit.KMH) "km" else "mi", 23, 28, p.dim, family = cond) },
        )
        LiveTxt({ TripFmt.duration(state.value.trip.elapsedSec.toInt()) + " \u00B7 since ignition" }, 20, 24, p.dim, Modifier.at(48, 230).width(181.dp), cond)
        if (data.demo) {
            Txt("Reset", 19, 21, p.cyan, Modifier.at(421, 156))
            ValueUnit(289, 223, value = { Txt("1,204", 38, 34, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(if (u == SpeedUnit.KMH) "km" else "mi", 23, 28, p.dim, family = cond) })
            Txt("since last refuel", 20, 24, p.dim, Modifier.at(289, 230), cond)
        } else {
            Txt(Fmt.DASH, 38, 34, p.dim, Modifier.at(289, 190), cond, FontWeight.Bold)
            Txt("No refuel counter yet", 20, 24, p.dim, Modifier.at(289, 230), cond)
        }

        if (shown.isEmpty()) {
            WaitingCard(24, 286, 470, 490, Ic.Route, "No trips yet", "Trips are recorded while the app is connected to the car's OBD adapter and the car moves.")
        } else {
            LazyColumn(Modifier.at(32, 292).size(454.dp, 478.dp)) {
                itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                    val on = t === cur
                    Box(
                        Modifier.size(454.dp, 80.dp).tap { sel = i; armed = false }.then(if (on) Modifier.background(p.accent, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) else Modifier),
                    ) {
                        val fg = if (on) p.onAccent else p.text; val sub = if (on) p.onAccent else p.dim
                        Txt(TripFmt.title(t.startMs, nowMs, zone), 22, 26, fg, Modifier.at(14, 15), weight = FontWeight.SemiBold)
                        Txt(TripFmt.sub(t, u), 18, 22, sub, Modifier.at(14, 41), weight = FontWeight.Normal)
                        TxtR(Fmt.distance(t.distanceKm, u), 34, 41, fg, 391, 19, 92, cond, FontWeight.SemiBold, tnum = true)
                        Txt(if (u == SpeedUnit.KMH) "km" else "mi", 23, 28, sub, Modifier.at(415, 25), family = cond)
                    }
                }
            }
        }

        if (cur != null) Detail(cur, nowMs, zone, u, st.pressureUnit, armed, onExport = {
            onNotice(onExport(listOf(cur)))
        }, onDelete = {
            if (!armed) { armed = true; onNotice("Tap Delete again to remove this trip") }
            else { data.trips.delete(cur.id); armed = false; sel = 0; version++ }
        }, onExportAll = { onNotice(onExport(shown)) })
        else {
            WaitingCard(518, 80, 626, 620, Ic.Route, "Nothing to show", "Pick a trip on the left once there is one.")
        }
    }
}

@Composable
private fun Detail(
    t: TripRecord, nowMs: Long, zone: ZoneId, u: SpeedUnit, pu: dev.kuntal.cockpit.data.PressureUnit, armed: Boolean,
    onExport: () -> Unit, onDelete: () -> Unit, onExportAll: () -> Unit,
) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    // header
    Txt(TripFmt.title(t.startMs, nowMs, zone).substringBefore(" \u00B7") + " \u00B7 " + TripFmt.range(t.startMs, t.endMs, zone), 26, 29, p.text, Modifier.at(542, 101), cond, FontWeight.SemiBold)
    Row(Modifier.at(924, 100).height(32.dp).width(196.dp).pill(border = p.hair), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Txt(if (t.demo) "Demo trip" else "Recorded by Cockpit", 18, 22, p.dim, weight = FontWeight.SemiBold)
    }
    val unitDist = if (u == SpeedUnit.KMH) "km" else "mi"
    Txt("Distance", 19, 21, p.dim, Modifier.at(560, 144)); Txt("Average", 19, 21, p.dim, Modifier.at(781, 144)); Txt("Top", 19, 21, p.dim, Modifier.at(960, 144))
    ValueUnit(560, 235, value = { Txt(Fmt.distance(t.distanceKm, u), 72, 65, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(unitDist, 23, 28, p.dim, family = cond) })
    ValueUnit(781, 235, value = { Txt(Fmt.speed(t.avgKmh, u), 40, 36, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(Units.label(u), 23, 28, p.dim, family = cond) })
    ValueUnit(960, 235, value = { Txt(Fmt.speed(t.topKmh, u), 40, 36, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(Units.label(u), 23, 28, p.dim, family = cond) })
    Box(Modifier.at(542, 252).size(578.dp, 1.dp).background(p.hairSoft))
    Txt("Max boost", 19, 21, p.dim, Modifier.at(560, 265)); Txt("Fuel \u00B7 est.", 19, 21, p.dim, Modifier.at(781, 265)); Txt("Avg \u00B7 est.", 19, 21, p.dim, Modifier.at(960, 265))
    ValueUnit(560, 328, value = { Txt(t.maxBoostBar?.let { Fmt.boost(it * 100.0, pu) } ?: Fmt.DASH, 40, 36, p.cyan, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(Units.label(pu), 23, 28, p.dim, family = cond) })
    ValueUnit(781, 328, value = { Txt(t.fuelL?.let { Fmt.dec(it, 1) } ?: Fmt.DASH, 40, 36, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt("L", 23, 28, p.dim, family = cond) })
    ValueUnit(960, 328, value = { Txt(t.avgLPer100?.let { Fmt.dec(it, 1) } ?: Fmt.DASH, 40, 36, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt("L/100", 23, 28, p.dim, family = cond) })

    Chart(t, u)

    // events
    val icons = mapOf(TripEventKind.PeakBoost to Ic.Bolt, TripEventKind.TopSpeed to Ic.Gauge, TripEventKind.LongestStop to Ic.Clock)
    t.events.take(3).forEachIndexed { i, e ->
        val cy = 588.7f + 42.3f * i
        Icon(icons.getValue(e.kind), 24, p.cyan, Modifier.at(542, (cy - 12).toInt()))
        Txt(TripFmt.eventText(t, e, { Fmt.boost(it * 100.0, pu) + " " + Units.label(pu) }, { Fmt.speed(it, u) + " " + Units.label(u) }), 21, 25, p.text, Modifier.at(584, (cy - 12.5f).toInt()))
        TxtR(TripFmt.clockAt(t, e.atSec, zone), 28, 34, p.text, 1120, (cy - 17).toInt(), 80, cond, FontWeight.Bold, tnum = true)
    }

    PillBtn(518, 712, 461, "Export CSV", onExport, h = 64, ic = Ic.Export)
    Row(Modifier.at(991, 712).size(153.dp, 64.dp).pill(fill = if (armed) p.warn else Color.Transparent, border = if (armed) Color.Transparent else p.hair).tap(onDelete), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.Trash, 24, if (armed) p.onWarn else p.text)
        Txt(if (armed) "Confirm" else "Delete", 21, 26, if (armed) p.onWarn else p.text, weight = FontWeight.SemiBold)
    }
}

private const val CX0 = 592f
private const val CW = 484f
private fun spY(kmh: Double) = (511.0 - kmh.coerceIn(0.0, 150.0) * 0.54).toFloat()
private fun bY(bar: Double) = (484.0 - bar.coerceIn(-1.0, 2.0) * 27.0).toFloat()

@Composable
private fun Chart(t: TripRecord, u: SpeedUnit) {
    val p = LocalPalette.current
    Txt("km/h", 18, 22, p.text, Modifier.at(542, 396)); Txt("bar", 18, 22, p.text, Modifier.at(1092, 396))
    listOf(150, 100, 50, 0).forEachIndexed { i, v ->
        TxtR(Fmt.speed(v.toDouble(), u), 18, 22, p.text, 576, 418 + 27 * i, 40)
        Txt(listOf("+2", "+1", "0", "\u22121")[i], 18, 22, p.text, Modifier.at(1092, 418 + 27 * i))
    }
    // legend
    Canvas(Modifier.at(0, 0).fillMaxSize()) {
        val d = 1.dp.toPx()
        drawLine(p.cyan, Offset(916 * d, 380.5f * d), Offset(946 * d, 380.5f * d), 3 * d)
        drawLine(p.text, Offset(1032.7f * d, 380.5f * d), Offset(1060.7f * d, 380.5f * d), 3 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * d, 5 * d)))
        for (y in listOf(430f, 457f, 484f)) drawLine(p.dim.copy(alpha = .3f), Offset(582 * d, y * d), Offset(1086 * d, y * d), d)
        drawLine(p.dim.copy(alpha = .6f), Offset(582 * d, 511 * d), Offset(1086 * d, 511 * d), 1.5f * d)
        val n = t.points.size
        if (n >= 2) {
            fun px(i: Int) = (CX0 + i.toFloat() / (n - 1) * CW) * d
            val area = Path().apply { moveTo(px(0), 511 * d); for (i in 0 until n) lineTo(px(i), spY(t.points[i].speedKmh) * d); lineTo(px(n - 1), 511 * d); close() }
            drawPath(area, p.cyan.copy(alpha = .16f))
            val line = Path().apply { for (i in 0 until n) { val y = spY(t.points[i].speedKmh) * d; if (i == 0) moveTo(px(i), y) else lineTo(px(i), y) } }
            drawPath(line, p.cyan, style = Stroke(2.5f * d))
            val bl = Path(); var started = false
            for (i in 0 until n) { val b = t.points[i].boostBar ?: continue; val y = bY(b) * d; if (!started) { bl.moveTo(px(i), y); started = true } else { bl.lineTo(px(i), y) } }
            if (started) drawPath(bl, p.text, style = Stroke(2.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * d, 5 * d))))
            // x ticks every few minutes, labelled with the clock time
            val durMin = t.durationSec / 60.0
            val step = (Math.ceil(durMin / 4.0 / 6.0) * 6).coerceAtLeast(6.0)
            var m = 0.0
            while (m < durMin - step / 3) { val x = (CX0 + (m / durMin * CW).toFloat()) * d; drawLine(p.dim.copy(alpha = .6f), Offset(x, 511 * d), Offset(x, 517 * d), 1.5f * d); m += step }
            drawLine(p.dim.copy(alpha = .6f), Offset((CX0 + CW) * d, 511 * d), Offset((CX0 + CW) * d, 517 * d), 1.5f * d)
        }
    }
    Txt("Speed", 19, 23, p.dim, Modifier.at(954, 369)); Txt("Boost", 19, 23, p.dim, Modifier.at(1069, 369))
    val zone = remember { ZoneId.systemDefault() }
    val durMin = t.durationSec / 60.0
    val step = (Math.ceil(durMin / 4.0 / 6.0) * 6).coerceAtLeast(6.0)
    var m = 0.0
    while (m < durMin - step / 3) {
        val x = CX0 + (m / durMin * CW).toFloat()
        Txt(TripFmt.clockAt(t, (m * 60).toInt(), zone), 18, 22, p.text, Modifier.at((x - 25).toInt(), 517).width(50.dp), align = TextAlign.Center); m += step
    }
    Txt(TripFmt.clockAt(t, t.durationSec, zone), 18, 22, p.text, Modifier.at((CX0 + CW - 25).toInt(), 517).width(50.dp), align = TextAlign.Center)
}
