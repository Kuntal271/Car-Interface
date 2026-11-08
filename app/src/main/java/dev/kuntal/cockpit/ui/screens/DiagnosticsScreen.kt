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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.DiagNote
import dev.kuntal.cockpit.data.DiagSource
import dev.kuntal.cockpit.data.Dtc
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.data.SourceKind
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.Para
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import dev.kuntal.cockpit.vehicle.Pid
import dev.kuntal.cockpit.vehicle.PollSchedule
import dev.kuntal.cockpit.vehicle.VehicleSample
import kotlinx.coroutines.delay

private class PidRow(val code: String, val name: String, val unit: String, val pid: Pid, val value: (VehicleSample) -> Double?, val fmt: (Double) -> String, val lo: Double, val hi: Double)

private fun rows(tu: dev.kuntal.cockpit.data.TempUnit): List<PidRow> = listOf(
    PidRow("010C", "Engine speed", "rpm", Pid.RPM, { it.rpm }, { Fmt.grouped(Math.round(it).toInt()) }, 800.0, 6500.0),
    PidRow("010D", "Vehicle speed", "km/h", Pid.SPEED, { it.speedKmh }, { Fmt.whole(it) }, 0.0, 140.0),
    PidRow("010B", "Manifold pressure", "kPa", Pid.MAP, { it.mapKpa }, { Fmt.whole(it) }, 20.0, 220.0),
    PidRow("0133", "Barometric", "kPa", Pid.BARO, { it.baroKpa }, { Fmt.whole(it) }, 90.0, 110.0),
    PidRow("010F", "Intake air temp", Units.label(tu), Pid.INTAKE_TEMP, { it.intakeC }, { Fmt.temp(it, tu) }, 20.0, 60.0),
    PidRow("0105", "Coolant temp", Units.label(tu), Pid.COOLANT_TEMP, { it.coolantC }, { Fmt.temp(it, tu) }, 40.0, 110.0),
    PidRow("0111", "Throttle position", "%", Pid.THROTTLE, { it.throttlePct }, { Fmt.whole(it) }, 0.0, 100.0),
    PidRow("0104", "Engine load", "%", Pid.ENGINE_LOAD, { it.loadPct }, { Fmt.whole(it) }, 0.0, 100.0),
    PidRow("012F", "Fuel level", "%", Pid.FUEL_LEVEL, { it.fuelPct }, { Fmt.whole(it) }, 0.0, 100.0),
    PidRow("0142", "Control module V", "V", Pid.MODULE_VOLTAGE, { it.batteryV }, { Fmt.dec(it, 1) }, 10.0, 16.0),
)

/** Last 30 readings of each row, one per second, filled while the screen is open. */
private class Trend(n: Int) { val v = Array(n) { DoubleArray(30) { Double.NaN } }; var count by mutableIntStateOf(0) }

/**
 * Diagnose: the live OBD adapter state and every PID being read (real data from the current source), the read-only command
 * guard, and a Faults card. Fault codes are mode 03, which this app never sends, so the card says "Not read"; the demo code
 * list behind it is invented and only available while Demo data is on.
 */
@Composable
fun DiagnosticsScreen(state: State<VehicleUiState>, diag: DiagSource?) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    var showFaults by remember { mutableStateOf(false) }
    val rs = remember(st.tempUnit) { rows(st.tempUnit) }
    val trend = remember { Trend(rs.size) }
    val rate = remember { RateMeter() }
    LaunchedEffect(Unit) { snapshotFlow { state.value.sample?.tsMs }.collect { rate.update(it) } }
    if (dev.kuntal.cockpit.DebugHooks.snapshot) remember {
        // frozen fixture: a short, deterministic history so the trend column is not empty in screenshots
        state.value.sample?.let { s -> rs.forEachIndexed { i, r -> r.value(s)?.let { v -> for (k in 0 until 30) trend.v[i][k] = v + (r.hi - r.lo) * 0.05 * Math.sin(k * 0.55 + i * 1.7) } } }
        true
    }
    LaunchedEffect(Unit) {
        while (true) {
            state.value.sample?.let { s ->
                rs.forEachIndexed { i, r ->
                    val a = trend.v[i]; System.arraycopy(a, 1, a, 0, a.size - 1); a[a.size - 1] = r.value(s) ?: Double.NaN
                }
                trend.count++
            }
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Txt("Diagnose", 34, 34, p.text, Modifier.at(24, 85), cond, FontWeight.SemiBold)
            Row(Modifier.at(242, 86).height(32.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.Key, 16, p.cyan); Txt("Read-only", 18, 22, p.cyan, weight = FontWeight.SemiBold)
            }
            Box(Modifier.at(24, 136).size(352.dp, 308.dp).card())
            Txt("OBD adapter", 19, 21, p.dim, Modifier.at(48, 154))
            for ((i, k) in listOf("Type", "Protocol", "PIDs seen", "Poll rate", "Source").withIndex()) Txt(k, 20, 24, p.dim, Modifier.at(48, 200 + 50 * i + if (i == 4) 1 else 0))
            Box(Modifier.at(24, 456).size(352.dp, 320.dp).card())
            Icon(Ic.Key, 22, p.dim, Modifier.at(48, 475))
            Txt("Command guard", 19, 26, p.dim, Modifier.at(78, 476))
            for ((i, t) in listOf("Mode 03 \u00B7 blocked", "Mode 04 \u00B7 blocked", "Mode 22 \u00B7 blocked").withIndex()) {
                Row(Modifier.at(48, 636 + 44 * i).height(32.dp).pill(border = p.hair).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Txt(t, 18, 22, p.dim, weight = FontWeight.SemiBold) }
            }
            for (i in 0..3) Box(Modifier.at(400 + 189 * i, 80).size(177.dp, 120.dp).card())
            Txt("Battery", 19, 21, p.dim, Modifier.at(424, 100)); Txt("Coolant", 19, 21, p.dim, Modifier.at(613, 100)); Txt("Load", 19, 21, p.dim, Modifier.at(802, 100)); Txt("Faults", 19, 21, p.dim, Modifier.at(991, 100))
            Box(Modifier.at(400, 212).size(744.dp, 564.dp).card())
        }
        // guard text with the two allowed families picked out
        val guard = buildAnnotatedString {
            append("Only "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("AT") }
            append(" setup commands and "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Mode 01") }
            append(" live reads are ever sent. Everything else is blocked in code and in tests.")
        }
        BasicText(guard, Modifier.at(48, 514).width(304.dp), style = TextStyle(color = p.text, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal, fontFamily = CockpitFonts.Barlow, letterSpacing = 0.012.em, platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)))

        // adapter
        val link by remember { derivedStateOf { state.value.link } }
        val (linkText, linkOn) = when (link) { Link.Connected -> "Connected" to true; Link.Simulated -> "Simulated" to true; Link.Connecting -> "Connecting\u2026" to false; Link.Lost -> "Lost" to false; Link.Off -> "No source" to false }
        Row(Modifier.at(236, 148).width(140.dp).height(32.dp), horizontalArrangement = Arrangement.End) {
            Row(Modifier.height(32.dp).pill(border = (if (linkOn) p.cyan else p.warn).copy(alpha = .5f)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Txt(linkText, 18, 22, if (linkOn) p.cyan else p.warn, weight = FontWeight.SemiBold) }
        }
        val (typeT, protoT, srcT) = describe(st)
        Kv(0, typeT); Kv(1, protoT)
        LiveTxt({ state.value.sample?.let { s -> listOf(s.rpm, s.speedKmh, s.coolantC, s.intakeC, s.ambientC, s.oilC, s.mapKpa, s.baroKpa, s.throttlePct, s.loadPct, s.fuelPct, s.batteryV).count { it != null }.toString() } ?: Fmt.DASH }, 20, 24, p.text, Modifier.at(176, 300).width(176.dp), weight = FontWeight.SemiBold, align = TextAlign.End)
        LiveTxt({ rate.text() }, 20, 24, p.text, Modifier.at(176, 350).width(176.dp), weight = FontWeight.SemiBold, align = TextAlign.End)
        Kv(4, srcT)

        // summary
        ValueUnit(424, 171, value = { LiveTxt({ state.value.sample?.batteryV?.let { Fmt.dec(it, 1) } ?: Fmt.DASH }, 48, 43, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt("V", 23, 28, p.dim, family = cond) })
        ValueUnit(613, 171, value = { LiveTxt({ Fmt.temp(state.value.sample?.coolantC, st.tempUnit) }, 48, 43, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt(Units.label(st.tempUnit), 23, 28, p.dim, family = cond) })
        ValueUnit(802, 171, value = { LiveTxt({ Fmt.whole(state.value.sample?.loadPct) }, 48, 43, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { Txt("%", 23, 28, p.dim, family = cond) })
        Box(Modifier.at(967, 80).size(177.dp, 120.dp).then(if (diag != null) Modifier.tap { showFaults = !showFaults } else Modifier))
        Txt("Not read", 34, 31, p.text, Modifier.at(991, 124), cond, FontWeight.Bold)
        Txt(DiagNote.BLOCKED, 18, 22, p.dim, Modifier.at(991, 158), weight = FontWeight.Normal)

        if (showFaults && diag != null) FaultList(diag, onClose = { showFaults = false })
        else Table(state, rs, trend, rate)
    }
}

@Composable
private fun Kv(i: Int, v: String) {
    val p = LocalPalette.current
    Txt(v, 20, 24, p.text, Modifier.at(176, 200 + 50 * i + if (i == 4) 1 else 0).width(176.dp), weight = FontWeight.SemiBold, align = TextAlign.End)
}

private fun describe(s: Settings): Triple<String, String, String> = when (s.source) {
    SourceKind.Simulator -> Triple("Simulator", "Built-in drive cycle", "No adapter")
    SourceKind.ObdTcp -> Triple("ELM327 \u00B7 Wi-Fi", "CAN 11-bit / 500 kbit/s", "${s.tcpHost}:${s.tcpPort}")
    SourceKind.ObdBluetooth -> Triple("ELM327 \u00B7 Bluetooth", "CAN 11-bit / 500 kbit/s", s.btName.ifBlank { "Not chosen" })
}

/** Updates per second, measured from the sample timestamps (each emitted sample is one poll cycle). */
private class RateMeter {
    private val ts = LongArray(24); private var n = 0; private var last = -1L
    var hz by mutableStateOf(if (dev.kuntal.cockpit.DebugHooks.snapshot) 12.0 else 0.0)
    fun update(t: Long?) {
        if (t == null || t == last) return
        last = t; ts[n % ts.size] = t; n++
        if (n >= 3) { val cnt = minOf(n, ts.size); val newest = ts[(n - 1) % ts.size]; val oldest = ts[(n - cnt) % ts.size]; hz = if (newest > oldest) (cnt - 1) * 1000.0 / (newest - oldest) else 0.0 }
    }
    fun text() = if (hz <= 0.0) Fmt.DASH else (if (hz >= 10) Fmt.whole(hz) else Fmt.dec(hz, 1)) + " Hz"
}

@Composable
private fun Table(state: State<VehicleUiState>, rs: List<PidRow>, trend: Trend, rate: RateMeter) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    Txt("PID", 18, 22, p.dim, Modifier.at(418, 230)); Txt("Parameter", 18, 22, p.dim, Modifier.at(490, 230)); TxtR("Value", 18, 22, p.dim, 788, 230, 90)
    Txt("Unit", 18, 22, p.dim, Modifier.at(809, 230)); Txt("Trend", 18, 22, p.dim, Modifier.at(885, 230)); TxtR("Hz", 18, 22, p.dim, 1130, 230, 50)
    Box(Modifier.at(414, 260).size(716.dp, 1.dp).background(p.hair))
    rs.forEachIndexed { i, r ->
        val y = 260 + 46 * i
        Txt(r.code, 18, 22, p.dim, Modifier.at(418, y + 12))
        Txt(r.name, 20, 24, p.text, Modifier.at(490, y + 11))
        LiveTxt({ state.value.sample?.let(r.value)?.let(r.fmt) ?: Fmt.DASH }, 28, 33, p.text, Modifier.at(698, y + 6).width(90.dp), cond, FontWeight.SemiBold, TextAlign.End, tnum = true)
        Txt(r.unit, 18, 22, p.dim, Modifier.at(809, y + 12))
        Canvas(Modifier.at(885, y + 8).size(150.dp, 30.dp)) {
            trend.count                                                    // redraw when a new point arrives
            val a = trend.v[i]; val d = 1.dp.toPx()
            val ok = a.filter { !it.isNaN() }
            if (ok.size < 2) return@Canvas
            val lo = minOf(r.lo, ok.min()); val hi = maxOf(r.hi, ok.max())
            val path = Path(); var started = false
            a.forEachIndexed { k, v -> if (v.isNaN()) return@forEachIndexed; val x = k / (a.size - 1f) * 150 * d; val y2 = (30 - 3 - ((v - lo) / (hi - lo)) * 24).toFloat() * d; if (!started) { path.moveTo(x, y2); started = true } else path.lineTo(x, y2) }
            if (r.pid in PollSchedule.FAST || r.pid in PollSchedule.MEDIUM) {
                val fill = Path().apply { addPath(path); lineTo(150 * d, 30 * d); lineTo(0f, 30 * d); close() }
                drawPath(fill, p.cyan.copy(alpha = .10f))
            }
            drawPath(path, p.cyan, style = Stroke(2 * d))
        }
        LiveTxt({ rateFor(r.pid, rate.hz) }, 18, 22, p.dim, Modifier.at(1070, y + 12).width(60.dp), align = TextAlign.End)
        if (i < rs.size - 1) Box(Modifier.at(414, y + 45).size(716.dp, 1.dp).background(p.hairSoft))
    }
}

/** Poll rate of one PID: the cycle rate for the fast group, a third of it for the medium group, a tenth for the slow one. */
private fun rateFor(pid: Pid, cycleHz: Double): String {
    if (cycleHz <= 0.0) return Fmt.DASH
    val hz = when (pid) { in PollSchedule.FAST -> cycleHz; in PollSchedule.MEDIUM -> cycleHz / 3; else -> cycleHz / 10 }
    return Fmt.whole(hz)
}

/** The invented demo fault list: tap a code for the freeze frame. */
@Composable
private fun FaultList(diag: DiagSource, onClose: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val codes = remember { diag.faults() ?: emptyList() }
    var sel by remember { mutableIntStateOf(0) }
    val cur: Dtc? = codes.getOrNull(sel)
    Txt("Fault codes", 26, 29, p.text, Modifier.at(424, 232), cond, FontWeight.SemiBold)
    DemoMark(560, 230)
    PillBtn(1000, 220, 120, "Close", onClose, h = 56)
    Para(DiagNote.EXPLAIN + " The app only ever sends setup commands and Mode 01 reads.", 19, 25, p.dim, 700, Modifier.at(424, 284))
    codes.forEachIndexed { i, c ->
        val on = i == sel
        Box(Modifier.at(414, 342 + 66 * i).size(716.dp, 60.dp).then(if (on) Modifier.background(p.accent, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) else Modifier).tap { sel = i }) {
            Txt(c.code, 26, 30, if (on) p.onAccent else p.text, Modifier.at(18, 15), cond, FontWeight.Bold)
            Txt(c.title, 21, 26, if (on) p.onAccent else p.text, Modifier.at(120, 17), weight = FontWeight.SemiBold)
            Txt(c.status.label, 18, 22, if (on) p.onAccent else p.dim, Modifier.at(560, 19).width(138.dp), align = TextAlign.End)
        }
    }
    if (cur != null) {
        Box(Modifier.at(414, 552).size(716.dp, 1.dp).background(p.hair))
        Para(cur.detail, 20, 26, p.text, 690, Modifier.at(424, 566))
        val f = cur.freeze
        if (f == null) Txt("No freeze frame stored for this code", 19, 24, p.dim, Modifier.at(424, 626))
        else {
            Txt("Freeze frame", 19, 21, p.dim, Modifier.at(424, 622))
            val cells = listOf("RPM" to Fmt.grouped(f.rpm), "Speed" to "${f.speedKmh} km/h", "Coolant" to "${f.coolantC} \u00B0C", "Load" to "${f.loadPct} %", "Boost" to "${f.boostKpa} kPa", "Fuel trim" to Fmt.signed(f.fuelTrimPct, 1) + " %")
            cells.forEachIndexed { i, (k, v) ->
                val x = 424 + 116 * i
                Txt(k, 18, 22, p.dim, Modifier.at(x, 658)); Txt(v, 24, 28, p.text, Modifier.at(x, 684), cond, FontWeight.SemiBold, tnum = true)
            }
        }
    }
}
