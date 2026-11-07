package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Door
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Radar
import dev.kuntal.cockpit.data.TempUnit
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.drawCarTop
import dev.kuntal.cockpit.ui.components.rememberCarArt
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.IconLabelAt
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.SegBar
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.WaitingCard
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * Car: doors, lights, climate, tyres and parking radar from the Canbox (a labelled mock until the real source exists).
 * Every Canbox field may be missing; a missing one is hidden, never shown as zero. Layout is the measured design.
 */
@Composable
fun CarScreen(canbox: State<CanboxState?>, onParking: () -> Unit = {}) {
    val have by remember { derivedStateOf { canbox.value != null } }
    if (!have) {
        Box(Modifier.fillMaxSize()) {
            WaitingCard(24, 80, 1120, 696, Ic.Car, "Waiting for Canbox", "No Canbox data yet. Doors, climate, tyres and the parking radar appear here once the unit shares them.")
        }
        return
    }
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    val tu = st.tempUnit
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            // left column
            Box(Modifier.at(24, 80).size(318.dp, 124.dp).card())
            Txt("Odometer", 19, 21, p.dim, Modifier.at(48, 100))
            Box(Modifier.at(24, 216).size(318.dp, 411.dp).card())
            Box(Modifier.at(24, 639).size(318.dp, 137.dp).card())
            Txt("Data source", 19, 21, p.dim, Modifier.at(48, 659))
            androidx.compose.foundation.layout.Box(Modifier.at(48, 686)) {
                dev.kuntal.cockpit.ui.components.Para("Canbox or OBD, depending on the unit's dialect. Climate is view only; controls stay on the dash.", 18, 23, p.dim, 276)
            }
            // right column
            Box(Modifier.at(826, 80).size(318.dp, 124.dp).card())
            IconLabelAt(850, 100, Ic.Thermo, "Outside")
            Box(Modifier.at(826, 216).size(318.dp, 560.dp).card())
            IconLabelAt(850, 239, Ic.Snow, "Climate")
            Txt("Fan", 19, 21, p.dim, Modifier.at(850, 527))
            Txt("Airflow", 19, 21, p.dim, Modifier.at(850, 671))
            // centre title
            Txt("Vehicle \u00B7 top view", 28, 31, p.text, Modifier.at(366, 87), cond, FontWeight.SemiBold)
            DemoMark(560, 86)
        }

        // ---- live: odometer, status list, outside, climate, car, banner ----
        ValueUnit(
            48, 180,
            value = { LiveTxt({ canbox.value?.odometerKm?.let { Fmt.grouped(it) } ?: Fmt.DASH }, 56, 50, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) },
            unit = { Txt(if (st.speedUnit == dev.kuntal.cockpit.data.SpeedUnit.KMH) "km" else "mi", 23, 28, p.dim, family = cond) },
        )
        StatusList(canbox)

        ValueUnit(
            850, 185,
            value = { LiveTxt({ canbox.value?.outsideC?.let { Fmt.temp(it, tu) } ?: Fmt.DASH }, 56, 50, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) },
            unit = { Txt(Units.label(tu), 23, 28, p.dim, family = cond) },
        )
        Climate(canbox, tu)

        CarTopView(canbox)
        // open-door chip and banner
        val summary by remember { derivedStateOf { canbox.value?.doorSummary } }
        summary?.let { text ->
            val n = canbox.value?.doorsOpen?.size ?: 0
            Row(Modifier.at(723, 86).size(79.dp, 32.dp).background(p.warn, androidx.compose.foundation.shape.RoundedCornerShape(50)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Txt("$n open", 18, 22, p.onWarn, weight = FontWeight.SemiBold)
            }
            Row(Modifier.at(366, 720).size(436.dp, 56.dp).background(p.warn, androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Warn, 30, p.onWarn)
                Txt(text, 26, 31, p.onWarn, family = cond, weight = FontWeight.Bold)
            }
        }
        // the radar and the camera are the same thing on this car: a tap on the car opens Parking
        Box(Modifier.at(420, 140).size(330.dp, 560.dp).tap(onParking))
    }
}

private class Row6(val icon: Ic, val label: String, val value: (CanboxState) -> String?)

private val ROWS = listOf(
    Row6(Ic.Lights, "Headlights") { it.headlights?.label },
    Row6(Ic.Fog, "Fog lights") { c -> c.fog?.let { if (it) "On" else "Off" } },
    Row6(Ic.Brake, "Handbrake") { c -> c.handbrake?.let { if (it) "Applied" else "Released" } },
    Row6(Ic.Belt, "Seat belts") { c -> c.driverBelt?.let { if (it) "Driver \u2713" else "Driver unbuckled" } },
    Row6(Ic.Key, "Ignition") { it.ignition?.label },
    Row6(Ic.Steer, "Steering") { c -> c.steeringDeg?.let { d -> if (d == 0) "Straight" else "${Fmt.signed(d.toDouble(), 0).replace("+", "")}\u00B0 ${if (d < 0) "left" else "right"}" } },
)

/** The six status rows. Rows whose value the car never sent are left out and the rest share the card (flex 1, as in the design). */
@Composable
private fun StatusList(canbox: State<CanboxState?>) {
    val p = LocalPalette.current
    val present by remember { derivedStateOf { val c = canbox.value; ROWS.indices.filter { c != null && ROWS[it].value(c) != null } } }
    val n = present.size
    if (n == 0) return
    val pitch = (411f - 12f) / n
    present.forEachIndexed { slot, ri ->
        val r = ROWS[ri]
        val cy = 222f + pitch * (slot + .5f)
        Icon(r.icon, 26, p.sky, Modifier.at(48, (cy - 13).toInt()))
        Txt(r.label, 21, 25, p.text, Modifier.at(86, (cy - 13).toInt()), weight = FontWeight.SemiBold)
        LiveTxt({ canbox.value?.let { r.value(it) } ?: "" }, 18, 22, p.text, Modifier.at(192, (cy - 11).toInt()).width(126.dp), weight = FontWeight.Bold, align = TextAlign.End)
        if (slot < n - 1) Box(Modifier.at(48, (222f + pitch * (slot + 1)).toInt()).size(270.dp, 1.dp).background(p.hair))
    }
}

@Composable
private fun Climate(canbox: State<CanboxState?>, tu: TempUnit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val c by remember { derivedStateOf { canbox.value?.climate } }
    val cl = c
    if (cl == null) {
        Txt("No climate frames from the Canbox", 19, 24, p.dim, Modifier.at(850, 300))
        return
    }
    LiveTxt({ canbox.value?.climate?.let { if (it.acOn) "A/C on" else "A/C off" } ?: "" }, 18, 22, if (cl.acOn) p.cyan else p.dim, Modifier.at(1020, 241).width(100.dp), weight = FontWeight.SemiBold, align = TextAlign.End)
    ValueUnit(
        850, 425,
        value = { LiveTxt({ canbox.value?.climate?.setC?.let { Fmt.dec(Units.temp(it, tu), 1) } ?: Fmt.DASH }, 72, 65, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) },
        unit = { Txt(Units.label(tu), 28, 34, p.dim, family = cond) },
    )
    LiveTxt({ canbox.value?.climate?.let { "${it.fan} / ${it.fanMax}" } ?: "" }, 19, 21, p.text, Modifier.at(1020, 527).width(100.dp), align = TextAlign.End)
    SegBar(7, 20.dp, Modifier.at(850, 555).width(270.dp), litCount = { canbox.value?.climate?.fan?.coerceIn(0, 7) ?: 0 })
    // airflow: view only indicators, lit = data colour
    val vents = listOf(Ic.User to { x: CanboxState -> x.climate?.face == true }, Ic.Chevd to { x: CanboxState -> x.climate?.feet == true },
        Ic.Defrost to { x: CanboxState -> x.climate?.defrost == true }, Ic.Recirc to { x: CanboxState -> x.climate?.recirc == true })
    vents.forEachIndexed { i, (ic, on) ->
        val cx = 850 + 29.25f + i * (58.5f + 12f)
        val lit by remember { derivedStateOf { canbox.value?.let(on) == true } }
        Icon(ic, 28, if (lit) p.cyan else p.dim, Modifier.at((cx - 14).toInt(), 712))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// top view: the design's SVG (viewBox -50 -78 420 736 in a 436 x 572 box at (366, 136)), drawn in design pixels

private const val S = 572f / 736f
private const val OX = 366f + (436f - 420f * S) / 2f
private const val OY = 136f
private fun sx(x: Float) = OX + (x + 50f) * S
private fun sy(y: Float) = OY + (y + 78f) * S

@Composable
private fun CarTopView(canbox: State<CanboxState?>) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val art = rememberCarArt()
    Canvas(Modifier.at(0, 0).fillMaxSize()) {
        val u = 1.dp.toPx()
        val c = canbox.value ?: return@Canvas
        withTransform({ scale(u, u, Offset.Zero); translate(OX, OY); scale(S, S, Offset.Zero); translate(50f, 78f) }) {
            drawCarTop(art, c, p)
            drawLine(p.text, Offset(266f, 242f), Offset(284f, 242f), 2f)          // driver marker line (the car is right-hand drive)
        }
    }
    // tyre pressures, radar distances and the driver label: text on top of the drawing
    val tyres by remember { derivedStateOf { canbox.value?.tyresBar } }
    tyres?.let { t ->
        val cells = listOf(Triple(36f, 122f, true), Triple(284f, 122f, false), Triple(36f, 406f, true), Triple(284f, 406f, false))
        cells.forEachIndexed { i, (x, y, end) ->
            val v = t.getOrNull(i) ?: return@forEachIndexed
            val px = sx(x); val by = sy(y)
            if (end) { TxtR(Fmt.dec(v, 1), 31, 37, p.text, px.toInt(), (by - 31).toInt(), 80, cond, FontWeight.Bold, tnum = true); TxtR("bar", 18, 22, p.text, px.toInt(), (by + 3).toInt(), 80) }
            else { Txt(Fmt.dec(v, 1), 31, 37, p.text, Modifier.at(px.toInt(), (by - 31).toInt()), cond, FontWeight.Bold, tnum = true); Txt("bar", 18, 22, p.text, Modifier.at(px.toInt(), (by + 3).toInt())) }
        }
        Txt("Driver", 18, 22, p.dim, Modifier.at(sx(292f).toInt(), (sy(249f) - 17).toInt()))
    }
    val rearM by remember { derivedStateOf { canbox.value?.radar?.nearestRearM } }
    val frontM by remember { derivedStateOf { canbox.value?.radar?.nearestFrontM } }
    rearM?.let { Row(Modifier.at(sx(196f).toInt() - 60, (sy(652f) - 32).toInt()).width(120.dp), horizontalArrangement = Arrangement.Center) { Txt(Fmt.dec(it, 1), 31, 37, p.text, family = cond, weight = FontWeight.Bold); Txt(" m", 18, 37, p.dim) } }
    frontM?.let { Row(Modifier.at(sx(160f).toInt() - 60, (sy(-38f) - 32).toInt()).width(120.dp), horizontalArrangement = Arrangement.Center) { Txt(Fmt.dec(it, 1), 31, 37, p.text, family = cond, weight = FontWeight.Bold); Txt(" m", 18, 37, p.dim) } }
}
