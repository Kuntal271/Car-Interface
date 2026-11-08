package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.BootAnim
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.MotionMode
import dev.kuntal.cockpit.data.Standby
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.FordMark
import dev.kuntal.cockpit.ui.components.FordOvalBlue
import dev.kuntal.cockpit.ui.components.FordPaths
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Standby: a big clock, the date and four quiet facts, after the idle timeout. Any touch wakes it. The clock drifts a few pixels
 * every minute so a still image never burns into the panel. Outside temperature and doors come from the Canbox (mock while Demo
 * data is on), the battery from OBD, the last trip from the trip history; anything unknown shows a dash.
 */
@Composable
fun StandbyScreen(state: State<VehicleUiState>, canbox: State<CanboxState?>, lastTripKm: Double?, onWake: () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    var now by remember { mutableStateOf(if (DebugHooks.snapshot) LocalDateTime.of(2026, 10, 30, 21, 47) else LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (!DebugHooks.snapshot) { now = LocalDateTime.now(); delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50) } }
    val (dx, dy) = if (DebugHooks.snapshot) 0 to 0 else Standby.driftPx(now.hour * 60 + now.minute)

    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onWake)) {
        // blink of the colon: toggled once a second by a state, never a per-frame animation
        var colon by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { while (!DebugHooks.snapshot) { delay(1_000); colon = !colon } }

        Box(Modifier.at(dx, dy).size(1280.dp, 800.dp)) {
            if (p.fordMark) Box(Modifier.at(534, 120)) { FordMark(76) }
            else Canvas(Modifier.at(604, 124).size(72.dp)) { rotate(45f) { drawRect(p.cyan, Offset(size.width * .18f, size.height * .18f), Size(size.width * .64f, size.height * .64f)) } }
            Txt(DateTimeFormatter.ofPattern("EEEE \u00B7 d MMMM", Locale.US).format(now), 19, 21, p.dim, Modifier.at(0, 256).width(1280.dp), align = TextAlign.Center)
            val hh = Fmt.clock(now.hour, now.minute, st.clock)
            val (h, m) = hh.substringBefore(':') to hh.substringAfter(':')
            Row(Modifier.at(0, 238).width(1280.dp), horizontalArrangement = Arrangement.Center) {
                Txt(h, 340, 279, p.text, family = cond, weight = FontWeight.Bold, letterSpacing = 4.08.sp)
                Txt(":", 340, 279, p.text.copy(alpha = if (colon) 1f else .25f), Modifier.padding(horizontal = 6.dp).offset(y = (-37).dp), cond, FontWeight.Bold, letterSpacing = 4.08.sp)
                Txt(m, 340, 279, p.text, family = cond, weight = FontWeight.Bold, letterSpacing = 4.08.sp)
            }
        }
        TxtR("Tap to wake", 19, 21, p.text.copy(alpha = .7f), 1190, 70, 300)
        if (canbox.value != null) DemoMark(90, 556)          // outside temperature and doors come from the mock Canbox

        // the strip of four facts
        Box(Modifier.at(90, 600).size(1100.dp, 1.dp).background(p.hair))
        for (x in listOf(351, 640, 928)) Box(Modifier.at(x, 601).size(1.dp, 109.dp).background(p.hairSoft))
        val outside = canbox.value?.outsideC ?: state.value.sample?.ambientC
        Fact(90, "Outside", outside?.let { Fmt.temp(it, st.tempUnit) } ?: Fmt.DASH, Units.label(st.tempUnit).takeIf { outside != null })
        Fact(379, "12 V battery", state.value.sample?.batteryV?.let { Fmt.dec(it, 1) } ?: Fmt.DASH, "V".takeIf { state.value.sample?.batteryV != null })
        val doors = canbox.value?.let { c -> c.doorSummary ?: if (c.locked == true) "Locked" else if (c.locked == false) "Unlocked" else "Closed" }
        Fact(668, "Doors", doors ?: Fmt.DASH, null)
        Fact(1190, "Last trip", lastTripKm?.let { Fmt.distance(it, st.speedUnit) } ?: Fmt.DASH, (if (st.speedUnit == dev.kuntal.cockpit.data.SpeedUnit.KMH) "km" else "mi").takeIf { lastTripKm != null }, alignEnd = true)
    }
}

@Composable
private fun Fact(x: Int, label: String, value: String, unit: String?, alignEnd: Boolean = false) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    if (alignEnd) {
        TxtR(label, 19, 21, p.dim, x, 620, 232)
        dev.kuntal.cockpit.ui.components.BaselineRow(x, 687, gap = 6, alignEnd = true) {
            Txt(value, 48, 43, p.text, family = cond, weight = FontWeight.Bold, tnum = true)
            if (unit != null) Txt(unit, 23, 28, p.dim, family = cond)
        }
    } else {
        Txt(label, 19, 21, p.dim, Modifier.at(x, 620))
        dev.kuntal.cockpit.ui.components.ValueUnit(x, 687, value = { Txt(value, 48, 43, p.text, family = cond, weight = FontWeight.Bold, tnum = true) }, unit = { if (unit != null) Txt(unit, 23, 28, p.dim, family = cond) })
    }
}

/**
 * Boot splash (design: boot.html, 3.6 s): the oval scales in, the ring sweeps round, the script wipes in, a sheen crosses, "Welcome"
 * rises and a bar fills. Transform and alpha only. Any touch skips it; Reduced motion shows the finished frame briefly instead.
 */
@Composable
fun BootScreen(onDone: () -> Unit) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    val frozen = DebugHooks.snapshot
    val reduced = st.motion == MotionMode.Reduced
    val t = remember { mutableLongStateOf(if (frozen || reduced) BootAnim.TOTAL_MS else 0L) }
    LaunchedEffect(Unit) {
        if (frozen) return@LaunchedEffect
        if (reduced) { delay(900); onDone(); return@LaunchedEffect }
        val t0 = withFrameNanos { it }
        while (true) {
            val ms = (withFrameNanos { it } - t0) / 1_000_000
            t.longValue = ms
            if (BootAnim.done(ms)) { delay(250); onDone(); break }
        }
    }
    val paths = remember { listOf(FordPaths.OVAL, FordPaths.RING, FordPaths.SCRIPT).map { PathParser().parsePathString(it).toPath() } }
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = { if (!frozen) onDone() })) {
        // halo behind the mark
        Canvas(Modifier.at(190, 42).size(900.dp, 620.dp)) {
            val d = 1.dp.toPx(); val a = BootAnim.oval(t.longValue)
            scale(1f, 310f / 450f, Offset(450 * d, 310 * d)) {
                drawCircle(Brush.radialGradient(0f to p.blue.copy(alpha = .42f * a), .55f to p.glowB.copy(alpha = .3f * a), 1f to Color.Transparent, center = Offset(450 * d, 310 * d), radius = 450 * d), 450 * d, Offset(450 * d, 310 * d))
            }
        }
        // the mark: 560 x 201.6 at (360, 177.7)
        Canvas(Modifier.at(360, 177).size(560.dp, 202.dp)) {
            val k = size.width / FordPaths.VB_W
            val tt = t.longValue
            if (!p.fordMark) {
                val c = Offset(size.width / 2, size.height / 2); val a = BootAnim.oval(tt)
                rotate(45f, c) { drawRect(p.cyan.copy(alpha = a), Offset(c.x - 70 * a, c.y - 70 * a), Size(140 * a, 140 * a)) }
                return@Canvas
            }
            scale(k, k, Offset.Zero) {
                val o = BootAnim.oval(tt)
                scale(.86f + .14f * o, .86f + .14f * o, Offset(FordPaths.VB_W / 2, FordPaths.VB_H / 2)) {
                    drawPath(paths[0], FordOvalBlue.copy(alpha = o))
                    // ring: revealed clockwise from 12 o'clock by a growing pie-shaped clip
                    val r = BootAnim.ring(tt)
                    if (r >= 1f) drawPath(paths[1], Color.White)
                    else if (r > 0f) {
                        val cx = FordPaths.VB_W / 2; val cy = FordPaths.VB_H / 2; val rad = FordPaths.VB_W
                        val pie = Path().apply {
                            moveTo(cx, cy)
                            arcTo(androidx.compose.ui.geometry.Rect(cx - rad, cy - rad, cx + rad, cy + rad), -90f, 360f * r, false)
                            close()
                        }
                        clipPath(pie) { drawPath(paths[1], Color.White) }
                    }
                    val sc = BootAnim.script(tt)
                    if (sc > 0f) clipRect(0f, 0f, FordPaths.VB_W * sc, FordPaths.VB_H) { drawPath(paths[2], Color.White) }
                    // sheen: a soft diagonal band crossing the oval once the script is in
                    val sh = (tt - 1_900L) / 800f
                    if (sh in 0f..1f) clipPath(paths[0]) {
                        val x = -40f + sh * (FordPaths.VB_W + 80f)
                        drawRect(Brush.horizontalGradient(0f to Color.Transparent, .5f to Color.White.copy(alpha = .28f), 1f to Color.Transparent, startX = x - 30f, endX = x + 30f), Offset(x - 30f, 0f), Size(60f, FordPaths.VB_H))
                    }
                }
            }
        }
        Box(Modifier.at(0, 421).width(1280.dp).graphicsLayer { val w = BootAnim.welcome(t.longValue); alpha = w; translationY = (1f - w) * 14.dp.toPx() }) {
            Txt("Welcome", 68, 68, Color.White, Modifier.width(1280.dp), cond, FontWeight.SemiBold, TextAlign.Center)
            Txt("EcoSport \u00B7 1.0 EcoBoost", 24, 29, p.text.copy(alpha = .8f), Modifier.at(0, 80).width(1280.dp), align = TextAlign.Center)
        }
        Canvas(Modifier.at(430, 578).size(420.dp, 6.dp)) {
            val r = 3.dp.toPx()
            drawRoundRect(p.sky.copy(alpha = .38f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            val f = BootAnim.loadBar(t.longValue)
            if (f > 0f) drawRoundRect(p.cyan, size = Size(size.width * f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        }
        Txt("Starting vehicle services", 20, 24, p.dim, Modifier.at(0, 598).width(1280.dp), align = TextAlign.Center)
    }
}
