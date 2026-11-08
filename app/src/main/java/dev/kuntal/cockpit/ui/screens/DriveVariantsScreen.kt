package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithCache
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.CruiseText
import dev.kuntal.cockpit.data.DriveVariant
import dev.kuntal.cockpit.data.FuelEstimate
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.History
import dev.kuntal.cockpit.data.PressureUnit
import dev.kuntal.cockpit.data.Run
import dev.kuntal.cockpit.data.Smoothness
import dev.kuntal.cockpit.data.SpeedUnit
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.data.ZeroTo100
import dev.kuntal.cockpit.ui.components.BaselineRow
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.IconLabelAt
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.TabPills
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Everything the Boost and Cruise faces keep between frames; filled from the live readings while the Drive screen is open. */
class DriveExtras(
    val history: History = History(51),                      // 25 s at 2 Hz
    val zero: ZeroTo100 = ZeroTo100(),
    val runs: MutableList<Run> = mutableStateListOf(),
    val fuel: FuelEstimate.Meter = FuelEstimate.Meter(),
    val smooth: Smoothness = Smoothness(),
) {
    var tick by mutableIntStateOf(0)                          // bumps when the history grows; only the chart reads it
    var litresPerHour by mutableStateOf<Double?>(null)
    var avg by mutableStateOf<Double?>(null)
    var score by mutableStateOf<Int?>(null)
    var zeroTimer by mutableStateOf<Double?>(null)
}

/**
 * The Drive screen and its two other faces. Cluster is the default (unchanged); Boost and Cruise open from the Boost card, a
 * horizontal swipe, or the tab strip that those two faces carry. A single 2 Hz sampler feeds the history while Drive is open.
 */
@Composable
fun DriveHost(state: State<VehicleUiState>, view: DriveVariant, onView: (DriveVariant) -> Unit, extras: DriveExtras) {
    LaunchedEffect(extras) {
        if (DebugHooks.snapshot) return@LaunchedEffect
        var n = 0
        while (true) {
            val s = state.value.sample
            if (s != null) {
                val lph = FuelEstimate.litresPerHour(s.rpm, s.mapKpa, s.intakeC)
                extras.fuel.add(s.tsMs, s.speedKmh, lph); extras.litresPerHour = lph
                extras.avg = extras.fuel.avgLPer100()
                s.speedKmh?.let { v -> extras.zero.accept(s.tsMs, v).let { t -> if (t != null) { extras.runs.add(0, Run(t)); while (extras.runs.size > 4) extras.runs.removeAt(extras.runs.size - 1) } } }
                extras.zeroTimer = extras.zero.elapsedSec(s.tsMs)
                if (n % 2 == 0) { extras.smooth.add(s.speedKmh); extras.score = extras.smooth.score() }
                extras.history.add(s.boostKpa?.let { it / 100.0 }, s.rpm); extras.tick++
            }
            n++; delay(500)
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(view) {
        var dx = 0f
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
            val i = view.ordinal
            if (dx < -160 && i < 2) onView(DriveVariant.entries[i + 1]) else if (dx > 160 && i > 0) onView(DriveVariant.entries[i - 1])
        }) { _, d -> dx += d }
    }) {
        when (view) {
            DriveVariant.Cluster -> { DriveScreen(state); Box(Modifier.at(652, 100).size(492.dp, 262.dp).tap { onView(DriveVariant.Boost) }) }
            DriveVariant.Boost -> DriveBoostScreen(state, extras, onView)
            DriveVariant.Cruise -> DriveCruiseScreen(state, extras, onView)
        }
    }
}

@Composable
private fun Tabs(view: DriveVariant, onView: (DriveVariant) -> Unit) = TabPills(24, 78, DriveVariant.entries, view, { it.label }) { onView(it) }

// ---------------------------------------------------------------------------------------------------------------- Boost

private const val CX = 342f           // arc centre on the page (svg 304,292 at offset 38,146)
private const val CY = 438f
private const val R_OUT = 278f
private const val R_IN = 226f
private const val A0 = 170.6f         // start of the first segment, degrees, clockwise from +x (y down)
private const val SEG_STEP = 4f
private const val SEG_W = 2.8f
private const val SEG_N = 50
private const val ZERO_IDX = 20
private const val ZERO_TICK_DEG = 249.4f     // the "0" tick; the scale runs 80 degrees per bar
private const val BAR_PER_SEG = 0.05

private fun polar(r: Float, deg: Float): Offset { val a = deg * PI.toFloat() / 180f; return Offset(CX + r * cos(a), CY + r * sin(a)) }

/** The 50 arc segments as paths in pixels. */
private fun segPaths(d: Float): List<Path> = List(SEG_N) { i ->
    val a = A0 + SEG_STEP * i
    Path().apply {
        val o0 = polar(R_OUT, a); val i1 = polar(R_IN, a + SEG_W)
        moveTo(o0.x * d, o0.y * d)
        arcTo(androidx.compose.ui.geometry.Rect((CX - R_OUT) * d, (CY - R_OUT) * d, (CX + R_OUT) * d, (CY + R_OUT) * d), a, SEG_W, false)
        lineTo(i1.x * d, i1.y * d)
        arcTo(androidx.compose.ui.geometry.Rect((CX - R_IN) * d, (CY - R_IN) * d, (CX + R_IN) * d, (CY + R_IN) * d), a + SEG_W, -SEG_W, false)
        close()
    }
}

@Composable
fun DriveBoostScreen(state: State<VehicleUiState>, x: DriveExtras, onView: (DriveVariant) -> Unit) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    val pu = st.pressureUnit; val tu = st.tempUnit; val u = st.speedUnit
    val unlit = p.blue.copy(alpha = .24f)
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(24, 136).size(660.dp, 372.dp).card())
            Box(Modifier.at(24, 520).size(660.dp, 256.dp).card())
            Row(Modifier.at(451, 86).height(32.dp).pill(border = p.hair).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Txt("Source \u00B7 OBD MAP \u2212 BARO", 18, 22, p.dim, weight = FontWeight.SemiBold) }
            Canvas(Modifier.fillMaxSize()) {
                val d = 1.dp.toPx()
                for (path in segPaths(d)) drawPath(path, unlit)
                for (idx in intArrayOf(0, 10, 20, 30, 40, 50)) {          // ticks: sky below zero, cyan above
                    val a = A0 + SEG_STEP * idx - 1.2f
                    val a0 = polar(217f, a); val a1 = polar(201f, a)
                    drawLine(if (idx >= 30) p.cyan else p.sky, Offset(a0.x * d, a0.y * d), Offset(a1.x * d, a1.y * d), 3 * d)
                }
            }
            Txt("Last 25 seconds", 19, 21, p.dim, Modifier.at(48, 543))
            Txt("boost", 20, 24, p.dim, Modifier.at(531, 542), cond); Txt("rpm", 20, 24, p.dim, Modifier.at(631, 542), cond)
            Box(Modifier.at(495, 553).size(28.dp, 3.dp).background(p.cyan))
            Canvas(Modifier.at(595, 552).size(28.dp, 5.dp)) { val d = 1.dp.toPx(); drawLine(p.text, Offset(0f, 2.5f * d), Offset(28 * d, 2.5f * d), 2.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7 * d, 5 * d))) }
            // chart frame
            Canvas(Modifier.at(48, 574).size(612.dp, 182.dp)) {
                val d = 1.dp.toPx(); val grid = p.sky.copy(alpha = .2f)
                for (k in 0..5) { val yy = (608 - 574 + 24 * k) * d; drawLine(grid, Offset(52 * d, yy), Offset(556 * d, yy), d) }
                for (k in 0..5) { val xx = (100f + 100.8f * k - 48f) * d; drawLine(grid, Offset(xx, 154 * d), Offset(xx, 160 * d), d) }
                drawLine(p.dim, Offset(52 * d, 106 * d), Offset(556 * d, 106 * d), 1.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * d, 4 * d)))
            }
            val bars = listOf(1.5, 1.0, 0.5, 0.0, -0.5, -1.0); val rpms = listOf("7,500", "6,000", "4,500", "3,000", "1,500", "0")
            bars.forEachIndexed { k, b -> TxtR(if (b == 0.0) Units.label(pu).let { "0 $it" } else Fmt.boostTick(b * 100.0, pu), 18, 22, p.text, 92, 596 + 24 * k, 60, weight = FontWeight.Medium); Txt(rpms[k], 18, 22, p.text, Modifier.at(612, 596 + 24 * k)) }
            TxtR(Units.label(pu), 18, 22, p.text, 92, 572, 60); Txt("rpm", 18, 22, p.text, Modifier.at(612, 572))
            for (k in 0..5) Txt(if (k == 5) "0 s" else "\u2212${25 - 5 * k} s", 18, 22, p.text, Modifier.at((100 + 100.8f * k - 60f).toInt(), 734).width(120.dp), align = TextAlign.Center)
            // arc tick labels
            val tx = listOf(151 to 452, 170 to 336, 273 to 261, 377 to 261, 469 to 336, 490 to 452)
            listOf(-1.0, -0.5, 0.0, 0.5, 1.0, 1.5).forEachIndexed { k, b -> Txt(if (b == 0.0) "0" else Fmt.boostTick(b * 100.0, pu), 21, 26, p.text, Modifier.at(tx[k].first, tx[k].second), weight = FontWeight.SemiBold) }
            Txt("${Units.label(pu)} \u00B7 gauge", 24, 30, p.text, Modifier.at(242, 445).width(192.dp), weight = FontWeight.SemiBold, align = TextAlign.Center)
            // right: tile frames and the 0-100 card
            for ((cx, cy) in listOf(708 to 80, 932 to 80, 708 to 224, 932 to 224)) Box(Modifier.at(cx, cy).size(212.dp, 132.dp).card())
            Box(Modifier.at(708, 368).size(436.dp, 408.dp).card())
            Txt("Throttle", 19, 21, p.dim, Modifier.at(732, 100)); Txt("Engine load", 19, 21, p.dim, Modifier.at(956, 100))
            Txt("Manifold abs.", 19, 21, p.dim, Modifier.at(732, 244)); Txt("Intake air", 19, 21, p.dim, Modifier.at(956, 244))
            IconLabelAt(732, 391, Ic.Flag, "0\u2013100 ${Units.label(u)}")
        }
        Tabs(DriveVariant.Boost, onView)

        // arc: the lit segments, from the zero mark up (boost) or down (vacuum); the dim segments and ticks are in the static layer
        Box(Modifier.fillMaxSize().drawWithCache {
            val d = 1.dp.toPx(); val paths = segPaths(d)
            onDrawBehind {
                val bar = ((state.value.sample?.boostKpa ?: 0.0) / 100.0).coerceIn(-1.0, 1.5)
                val n = Math.round(Math.abs(bar) / BAR_PER_SEG).toInt()
                val litFrom = if (bar >= 0) ZERO_IDX else ZERO_IDX - n
                for (i in litFrom until litFrom + n) if (i in 0 until SEG_N) drawPath(paths[i], p.cyan)
                // peak marker: a small triangle just outside the arc
                val peak = ((state.value.trip.peakBoostKpa ?: return@onDrawBehind) / 100.0).coerceIn(-1.0, 1.5)
                val pa = ZERO_TICK_DEG + 80f * peak.toFloat()
                val tip = polar(R_OUT + 3, pa); val l = polar(R_OUT + 24, pa - 2.1f); val r = polar(R_OUT + 24, pa + 2.1f)
                drawPath(Path().apply { moveTo(tip.x * d, tip.y * d); lineTo(l.x * d, l.y * d); lineTo(r.x * d, r.y * d); close() }, p.cyan)
            }
        })
        Txt("Peak", 20, 24, p.text, Modifier.at(619, 230), weight = FontWeight.SemiBold)
        LiveTxt({ "${Fmt.boost(state.value.trip.peakBoostKpa ?: 0.0, pu)} ${Units.label(pu)}" }, 20, 24, p.text, Modifier.at(540, 254).width(122.dp), weight = FontWeight.SemiBold, align = TextAlign.End)
        LiveTxt({ Fmt.boost(state.value.sample?.boostKpa ?: 0.0, pu) }, 118, 140, p.text, Modifier.at(198, 305).width(280.dp), cond, FontWeight.Bold, TextAlign.Center, letterSpacing = 0.sp, tnum = true)

        // chart traces
        Canvas(Modifier.at(100, 574).size(504.dp, 182.dp)) {
            x.tick
            val d = 1.dp.toPx(); val h = x.history; val cap = h.cap
            val boost = Path(); val rpm = Path(); var b0 = false; var r0 = false
            for (i in 0 until h.size) {
                val xx = (504f * (cap - h.size + i) / (cap - 1)) * d
                val bv = h.boost(i); val rv = h.rpm(i)
                if (!bv.isNaN()) { val yy = (106 - bv.coerceIn(-1.0, 1.5) * 48 ).toFloat() * d; if (!b0) { boost.moveTo(xx, yy); b0 = true } else boost.lineTo(xx, yy) }
                if (!rv.isNaN()) { val yy = (154 - rv.coerceIn(0.0, 7500.0) / 7500.0 * 120).toFloat() * d; if (!r0) { rpm.moveTo(xx, yy); r0 = true } else rpm.lineTo(xx, yy) }
            }
            drawPath(rpm, p.text, style = Stroke(2.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7 * d, 5 * d))))
            drawPath(boost, p.cyan, style = Stroke(3 * d))
        }

        // tiles
        Stat(732, 172, "%", { Fmt.whole(state.value.sample?.throttlePct) }, 164, 179, { Fmt.lit(Fmt.fraction(state.value.sample?.throttlePct, 0.0, 100.0), 16) })
        Stat(956, 172, "%", { Fmt.whole(state.value.sample?.loadPct) }, 164, 179, { Fmt.lit(Fmt.fraction(state.value.sample?.loadPct, 0.0, 100.0), 16) })
        Stat(732, 316, "kPa", { Fmt.whole(state.value.sample?.mapKpa) }, 164, -1, null)
        Stat(956, 316, Units.label(tu), { Fmt.temp(state.value.sample?.intakeC, tu) }, 164, 323, { Fmt.lit(Fmt.fraction(state.value.sample?.intakeC, 0.0, 110.0), 16) })
        Row(Modifier.at(732, 321).width(164.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("baro", 20, 18, p.dim, family = cond, letterSpacing = 0.4.sp)
            LiveTxt({ Fmt.whole(state.value.sample?.baroKpa) }, 20, 18, p.text, family = cond, tnum = true)
            Txt("kPa", 20, 18, p.dim, family = cond)
        }

        // 0-100 card
        val armed = x.zero.phase != ZeroTo100.Phase.Waiting
        Box(Modifier.at(820, 388).width(300.dp).height(32.dp), contentAlignment = Alignment.CenterEnd) {      // right edge at the card padding whatever the label
            Row(Modifier.height(32.dp).pill(border = p.cyan.copy(alpha = if (armed) .5f else 0f)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveTxt({ when (x.zero.phase) { ZeroTo100.Phase.Armed -> "Armed"; ZeroTo100.Phase.Running -> "Running"; ZeroTo100.Phase.Waiting -> "Stop to arm" } }, 18, 22, if (armed) p.cyan else p.dim, weight = FontWeight.SemiBold)
            }
        }
        ValueUnit(732, 489, value = { LiveTxt({ Fmt.dec(x.zeroTimer ?: 0.0, 1) }, 64, 58, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) }, unit = { Txt("s", 23, 28, p.dim, family = cond) })
        BaselineRow(1120, 489, gap = 4, alignEnd = true) {
            Txt("Best", 22, 24, p.cyan, weight = FontWeight.SemiBold)
            LiveTxt({ state.value.trip.zeroTo100Best?.let { Fmt.dec(it, 1) } ?: Fmt.DASH }, 22, 24, p.cyan, weight = FontWeight.Black, tnum = true)
            Txt("s", 23, 28, p.cyan, family = cond)
        }
        val best by remember { derivedStateOf { state.value.trip.zeroTo100Best } }
        for (k in 0 until 4) {
            val y = 520 + 64 * k
            val shown = x.runs.getOrNull(k)
            val n = x.runs.size - k
            Txt(if (shown == null) "\u2013" else "Run $n \u00B7 ${if (shown.daysAgo == 0) "today" else if (shown.daysAgo == 1) "yesterday" else "${shown.daysAgo} days ago"}", 20, 24, p.text, Modifier.at(732, y + 1), weight = FontWeight.Medium)
            val isBest = shown != null && best != null && Math.abs(shown.seconds - best!!) < 0.05
            BaselineRow(1120, y + 25, gap = 2, alignEnd = true) {
                Txt(shown?.let { Fmt.dec(it.seconds, 1) } ?: Fmt.DASH, 30, 36, if (isBest) p.cyan else p.text, family = cond, weight = FontWeight.Bold, tnum = true)
                Txt("s", 23, 28, if (isBest) p.cyan else p.dim, family = cond)
            }
            if (k > 0) Box(Modifier.at(732, y - 12).size(388.dp, 1.dp).background(p.hairSoft))
        }
    }
}

/** A 60 px value with its unit on the shared baseline, and an optional 16-segment bar under it. */
@Composable
private fun Stat(x: Int, baseline: Int, unit: String, value: () -> String, w: Int, segY: Int, lit: (() -> Int)?) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    ValueUnit(x, baseline, value = { LiveTxt(value, 60, 54, p.text, family = cond, weight = FontWeight.Bold, letterSpacing = 0.7.sp, tnum = true) }, unit = { Txt(unit, 23, 28, p.dim, family = cond) })
    if (lit != null && segY > 0) SegSlider(x, segY, w, 11, 16, frac = { lit() / 16f })
}

// ---------------------------------------------------------------------------------------------------------------- Cruise

@Composable
fun DriveCruiseScreen(state: State<VehicleUiState>, x: DriveExtras, onView: (DriveVariant) -> Unit) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    val u = st.speedUnit; val tu = st.tempUnit
    val scale = st.speedScaleKmh
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            for ((cx, cy) in listOf(24 to 136, 24 to 462, 894 to 136, 894 to 462)) Box(Modifier.at(cx, cy).size(250.dp, 314.dp).card())
            Txt("Consumption is estimated \u00B7 no fuel-rate PID", 19, 23, p.dim, Modifier.at(777, 90))
            IconLabelAt(48, 156, Ic.Fuel, "Range"); IconLabelAt(48, 482, Ic.Thermo, "Coolant"); IconLabelAt(918, 156, Ic.Route, "Average"); IconLabelAt(918, 482, Ic.Pulse, "Smoothness")
            Txt("km", 23, 28, p.dim, Modifier.at(48, 298), cond); Txt("L/100 km", 23, 28, p.dim, Modifier.at(918, 298), cond)
            Txt("E", 20, 24, p.dim, Modifier.at(48, 406), cond); Txt("F", 20, 24, p.dim, Modifier.at(241, 406), cond)
            Txt("C", 20, 24, p.dim, Modifier.at(48, 732), cond); Txt("H", 20, 24, p.dim, Modifier.at(240, 732), cond)
            Txt("0", 20, 24, p.dim, Modifier.at(918, 732), cond); Txt("100", 20, 24, p.dim, Modifier.at(1092, 732), cond)
            Txt("Speed", 19, 21, p.dim, Modifier.at(298, 184).width(572.dp), align = TextAlign.Center)
            Txt(Units.label(u), 26, 31, p.dim, Modifier.at(304, 514).width(566.dp), cond, align = TextAlign.Center)
            for (k in 0..4) {
                val label = Fmt.whole(Units.speed(scale * k / 4.0, u))
                Txt(label, 18, 22, p.dim, Modifier.at(if (k == 0) 298 else if (k == 4) 870 - 31 else 298 + 143 * k - 20, 615).width(if (k == 0 || k == 4) 31.dp else 40.dp), align = if (k == 0) TextAlign.Start else if (k == 4) TextAlign.End else TextAlign.Center)
            }
        }
        Tabs(DriveVariant.Cruise, onView)

        // range / coolant / average / smoothness
        val avg by remember { androidx.compose.runtime.derivedStateOf { x.avg } }
        val range = { FuelEstimate.rangeKm(state.value.sample?.fuelPct, x.avg)?.let { Units.distance(it, u) } }
        LiveTxt({ range()?.let { Fmt.whole(it) } ?: Fmt.DASH }, 72, 65, p.text, Modifier.baselineAt(48, 294), cond, FontWeight.Bold, letterSpacing = 0.9.sp, tnum = true)
        SegSlider(48, 378, 202, 22, 12, frac = { (state.value.sample?.fuelPct ?: 0.0).toFloat() / 100f })
        LiveTxt({ Fmt.whole(state.value.sample?.fuelPct) + "%" }, 20, 24, p.text, Modifier.at(48, 406).width(202.dp), cond, FontWeight.SemiBold, TextAlign.Center)
        ValueUnit(48, 620, value = { LiveTxt({ Fmt.temp(state.value.sample?.coolantC, tu) }, 72, 65, p.text, family = cond, weight = FontWeight.Bold, letterSpacing = 0.9.sp, tnum = true) }, unit = { Txt(Units.label(tu), 23, 28, p.dim, family = cond) })
        LiveTxt({ CruiseText.coolant(state.value.sample?.coolantC) }, 23, 28, p.dim, Modifier.at(48, 625), cond)
        SegSlider(48, 704, 202, 22, 12, frac = { Fmt.fraction(state.value.sample?.coolantC, 50.0, 110.0).toFloat() })
        LiveTxt({ avg?.let { Fmt.dec(it, 1) } ?: Fmt.DASH }, 72, 65, p.text, Modifier.baselineAt(918, 294), cond, FontWeight.Bold, letterSpacing = 0.9.sp, tnum = true)
        LiveTxt({ "Trip A \u00B7 ${Fmt.distance(state.value.trip.distanceKm, u)} ${if (u == SpeedUnit.KMH) "km" else "mi"}" }, 20, 24, p.dim, Modifier.at(918, 406), cond, letterSpacing = 0.4.sp)
        ValueUnit(918, 620, value = { LiveTxt({ x.score?.toString() ?: Fmt.DASH }, 72, 65, p.text, family = cond, weight = FontWeight.Bold, letterSpacing = 0.9.sp, tnum = true) }, unit = { Txt("/ 100", 23, 28, p.dim, family = cond) })
        LiveTxt({ x.score?.let { CruiseText.smooth(it) } ?: "" }, 23, 28, p.dim, Modifier.at(918, 625), cond)
        SegSlider(918, 704, 202, 22, 12, frac = { (x.score ?: 0) / 100f })

        // the big speed
        LiveTxt({ Fmt.speed(state.value.sample?.speedKmh, u) }, 330, 297, p.text, Modifier.width(572.dp).baselineAt(298, 491), cond, FontWeight.SemiBold, TextAlign.Center, letterSpacing = 0.sp, tnum = true)
        SegSlider(298, 579, 572, 30, 44, frac = { ((state.value.sample?.speedKmh ?: 0.0) / scale).toFloat().coerceIn(0f, 1f) })

        // shift hint
        val hint by remember { androidx.compose.runtime.derivedStateOf { val s = state.value; if ((s.sample?.rpm ?: 0.0) >= st.shiftRpm - 400 && (s.gear ?: 9) in 1..5) s.gear!! + 1 else null } }
        hint?.let { g ->
            Row(Modifier.at(452, 671).size(263.dp, 56.dp).pill(border = p.cyan), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Chevu, 26, p.cyan); Txt("Shift up \u2192 gear $g", 26, 31, p.cyan, family = cond, weight = FontWeight.SemiBold)
            }
        }
    }
}
