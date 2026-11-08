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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Radar
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.TabPills
import dev.kuntal.cockpit.ui.components.ToggleSwitch
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.TxtR
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.WaitingCard
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.drawCarTop
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.rememberCarArt
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

private enum class ParkView(val label: String) { Rear("Rear"), Around("360\u00B0"), Front("Front") }

/**
 * Parking: a camera-style view with distance guidelines, an obstacle marker and the sensor readout, from a radar source (a
 * labelled mock until the Canbox radar frames are read). There is no camera stream here: the unit's own camera app owns the
 * camera, so the "feed" is a drawn stand-in and the button opens the real camera app.
 */
@Composable
fun ParkingScreen(radar: State<Radar?>, reverse: State<Boolean>, onExit: () -> Unit, onOpenCamera: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val have by remember { derivedStateOf { radar.value != null } }
    if (!have) {
        Box(Modifier.fillMaxSize()) {
            WaitingCard(24, 80, 1120, 696, Ic.Park, "Waiting for Canbox", "No parking sensor data yet. The radar and distances appear here once the unit shares them.")
            PillBtn(504, 560, 280, "Open the camera app", onOpenCamera, ic = Ic.Camera, primary = true)
            PillBtn(1000, 712, 156, "Back", onExit)
        }
        return
    }
    var view by rememberSaveable { mutableStateOf(ParkView.Rear) }
    var guides by rememberSaveable { mutableStateOf(true) }
    var beep by rememberSaveable { mutableStateOf(0.6f) }
    var bright by rememberSaveable { mutableStateOf(0.7f) }
    val art = rememberCarArt()

    Box(Modifier.fillMaxSize()) {
        // camera stand-in: card, drawn stripes, guidelines
        StaticLayer {
            Box(Modifier.at(24, 80).size(850.dp, 696.dp).card())
            Canvas(Modifier.at(25, 81).size(848.dp, 694.dp)) {
                val r = 24.dp.toPx()
                val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                clipPath(clip) {
                    drawRect(Brush.verticalGradient(0f to p.bgTop, .6f to p.bg, 1f to p.s1))
                    val d = 1.dp.toPx(); val step = 22f * d * 1.4142f
                    var x = -size.height
                    while (x < size.width) { drawLine(p.blue.copy(alpha = .10f), Offset(x, 0f), Offset(x + size.height, size.height), 2.8f * d); x += step }
                }
            }
            Txt("Rear view", 19, 21, p.dim, Modifier.at(767, 102))
            Txt("Parking", 48, 48, p.text, Modifier.at(898, 84), cond, FontWeight.SemiBold)
            Box(Modifier.at(898, 208).size(358.dp, 288.dp).card())
            Box(Modifier.at(898, 508).size(358.dp, 192.dp).card())
            Txt("Guidelines", 21, 25, p.text, Modifier.at(922, 529), weight = FontWeight.SemiBold)
            Txt("Beep volume", 21, 25, p.text, Modifier.at(922, 585), weight = FontWeight.SemiBold)
            Txt("Brightness", 21, 25, p.text, Modifier.at(922, 641), weight = FontWeight.SemiBold)
            Box(Modifier.at(922, 570).size(310.dp, 1.dp).background(p.hairSoft))
            Box(Modifier.at(922, 626).size(310.dp, 1.dp).background(p.hairSoft))
        }
        Guidelines(guides)
        // live badge (the stand-in says so: this is not a camera image)
        Row(Modifier.at(44, 98).height(32.dp).pill(fill = p.bg.copy(alpha = .62f), border = p.cyan).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp)) { drawCircle(p.cyan) }
            Txt(if (DebugHooks.snapshot) "Rear camera \u00B7 live" else "Camera view \u00B7 demo", 18, 22, p.cyan, weight = FontWeight.SemiBold)
        }
        DemoMark(280, 98)

        // obstacle marker
        val obj by remember { derivedStateOf { radar.value?.let { r -> r.rearIndex?.let { i -> i to r.nearestRearM } } } }
        obj?.let { (idx, d) ->
            val cx = 24f + 425f
            val bx = cx + floatArrayOf(-186f, -93f, 93f, 186f)[idx]
            val cy = 80f + (yFor(d ?: 1.8) - 27f)
            Box(Modifier.at((bx - 65).toInt(), (cy - 31).toInt()).size(130.dp, 62.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(p.warn.copy(alpha = .12f)); drawRect(p.warn, style = Stroke(2.5f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))))
                }
                Row(Modifier.at(15, 14).size(100.dp, 34.dp).pill(fill = p.warn, border = Color.Transparent), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Txt("Object", 22, 27, p.onWarn, weight = FontWeight.SemiBold) }
            }
        }

        // right panel
        val rev by remember { derivedStateOf { reverse.value } }
        Box(Modifier.at(1212, 90).size(44.dp, 44.dp).pill(fill = if (rev) p.accent else Color.Transparent, border = if (rev) Color.Transparent else p.hair), contentAlignment = Alignment.Center) {
            Txt("R", 26, 30, if (rev) p.onAccent else p.dim, weight = FontWeight.SemiBold)
        }
        TabPills(898, 148, ParkView.entries, view, { it.label }) { view = it }

        val near by remember { derivedStateOf { val r = radar.value; when (view) { ParkView.Rear -> r?.nearestRearM; ParkView.Front -> r?.nearestFrontM; ParkView.Around -> listOfNotNull(r?.nearestRearM, r?.nearestFrontM).minOrNull() } } }
        Txt(when (view) { ParkView.Rear -> "Rear sensors"; ParkView.Front -> "Front sensors"; ParkView.Around -> "All sensors" }, 19, 21, p.dim, Modifier.at(916, 227))
        if ((near ?: 9.0) < 0.8) Row(Modifier.at(1165, 222).size(73.dp, 32.dp).background(p.warn, androidx.compose.foundation.shape.RoundedCornerShape(50)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Txt("Close", 18, 22, p.onWarn, weight = FontWeight.SemiBold) }
        Canvas(Modifier.at(0, 0).fillMaxSize()) {
            val u = 1.dp.toPx(); val r = radar.value ?: return@Canvas
            withTransform({ scale(u, u, Offset.Zero); translate(916f + 8.8f, 260f); scale(0.3788f, 0.3788f, Offset.Zero); translate(-30f, -32f) }) {
                drawCarTop(art, CanboxState(radar = r), p, front = view != ParkView.Rear, rear = view != ParkView.Front)
            }
        }
        ValueUnit(
            1044, 386,
            value = { Txt(near?.let { Fmt.dec(it, 1) } ?: Fmt.DASH, 72, 65, p.text, family = cond, weight = FontWeight.SemiBold) },
            unit = { Txt("m", 23, 28, p.dim, family = cond) },
        )
        val place by remember { derivedStateOf { if (view == ParkView.Front) "Front" else Radar.place(radar.value?.rearIndex) } }
        Txt(if (near == null) "Clear" else place, 19, 21, p.dim, Modifier.at(1044, 397))

        ToggleSwitch(1144, 519, guides) { guides = !guides }
        SegSlider(1064, 586, 100, 22, 10, frac = { beep }, knob = true) { beep = it }
        TxtR(Math.round(beep * 100).toString(), 32, 36, p.text, 1220, 585, 60, cond, FontWeight.SemiBold, tnum = true); Txt("%", 21, 25, p.dim, Modifier.at(1224, 591))
        SegSlider(1064, 643, 100, 22, 10, frac = { bright }, knob = true) { bright = it }
        TxtR(Math.round(bright * 100).toString(), 32, 36, p.text, 1220, 641, 60, cond, FontWeight.SemiBold, tnum = true); Txt("%", 21, 25, p.dim, Modifier.at(1224, 647))

        PillBtn(898, 712, 358, if (rev) "Exit \u00B7 available in P" else "Exit", onExit, h = 64, enabled = !rev)
    }
}

private fun half(y: Float) = 70f + (y - 200f) * 0.78f

/** Distance (m) to the y of the design's guideline bars: 0.5 m = 520, 1 m = 390, 2 m = 270 (card coordinates). */
private fun yFor(m: Double): Float = when {
    m <= 0.5 -> 520f
    m <= 1.0 -> (520 + (m - 0.5) / 0.5 * (390 - 520)).toFloat()
    else -> (390 + (m - 1.0) / 1.0 * (270 - 390)).toFloat().coerceAtLeast(270f)
}

@Composable
private fun Guidelines(show: Boolean) {
    if (!show) return
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val cx = 425f
    Canvas(Modifier.at(24, 80).size(850.dp, 696.dp)) {
        val d = 1.dp.toPx()
        fun path(build: Path.() -> Unit) = Path().apply(build)
        // corridor edges (dashed) and the predicted path (solid, mirror symmetric)
        val edge = path { moveTo((cx - half(230f)) * d, 230f * d); lineTo((cx - half(660f)) * d, 660f * d); moveTo((cx + half(230f)) * d, 230f * d); lineTo((cx + half(660f)) * d, 660f * d) }
        drawPath(edge, p.sky, style = Stroke(5 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14 * d, 10 * d))))
        val traj = path {
            moveTo((cx - half(660f) * .72f) * d, 660f * d); quadraticTo((cx - half(450f) * .72f) * d, 430f * d, (cx - half(250f) * .72f) * d, 250f * d)
            moveTo((cx + half(660f) * .72f) * d, 660f * d); quadraticTo((cx + half(450f) * .72f) * d, 430f * d, (cx + half(250f) * .72f) * d, 250f * d)
        }
        drawPath(traj, p.cyan, style = Stroke(5 * d))
        for (y in floatArrayOf(270f, 390f, 520f)) { val h = half(y); drawLine(p.cyan, Offset((cx - h) * d, y * d), Offset((cx + h) * d, y * d), 8 * d) }
    }
    for ((y, label) in listOf(270f to "2.0", 390f to "1.0", 520f to "0.5")) {
        val h = half(y)
        Txt(label, 32, 38, p.text, Modifier.at((24 + cx + h + 14).toInt(), (80 + y - 24).toInt()), cond, FontWeight.Bold, tnum = true)
        Txt("m", 22, 26, p.dim, Modifier.at((24 + cx + h + 14 + 37).toInt(), (80 + y - 14).toInt()))
    }
}
