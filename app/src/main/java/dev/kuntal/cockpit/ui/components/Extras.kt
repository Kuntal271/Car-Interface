package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/** A full-screen render node: what is inside is recorded once and replayed until something *inside* it changes. */
@Composable
fun StaticLayer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer()) { content() }
}

/** Dim label with its 22 px line icon in front (8 px gap), as every card label in the design. */
@Composable
fun IconLabelAt(x: Int, y: Int, ic: Ic, text: String, color: Color = LocalPalette.current.dim) {
    Row(Modifier.at(x, y), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(ic, 22, color)
        Txt(text, 19, 26, color)
    }
}

/**
 * The discreet marker every mock-backed screen carries. Hairline pill, dim text, a small dot in the data colour: it reads as a
 * status, not as an alarm (no amber, no red). Hidden in snapshot mode, because the design renders have no such marker.
 */
@Composable
fun DemoMark(x: Int, y: Int, text: String = "Demo data") {
    if (DebugHooks.snapshot) return
    val p = LocalPalette.current
    Row(
        Modifier.at(x, y).height(32.dp).pill(border = p.hair).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(p.sky) }
        Txt(text, 18, 22, p.dim, weight = FontWeight.SemiBold)
    }
}

/** The honest empty state: shown instead of numbers when there is no source (and no demo data). */
@Composable
fun WaitingCard(x: Int, y: Int, w: Int, h: Int, ic: Ic, title: String, body: String) {
    val p = LocalPalette.current
    Box(Modifier.at(x, y).size(w.dp, h.dp).card())
    Box(Modifier.at(x, y).size(w.dp, h.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(ic, 56, p.sky)
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
            Txt(title, 44, 48, p.text, family = CockpitFonts.Condensed, weight = FontWeight.SemiBold)
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Para(body, 21, 28, p.dim, minOf(w - 120, 640))
        }
    }
}

/** A text pill button: [primary] is solid accent, otherwise the sky tint with a hairline. 56 high by default (touch floor). */
@Composable
fun PillBtn(
    x: Int, y: Int, w: Int, label: String, onClick: () -> Unit,
    h: Int = 56, ic: Ic? = null, primary: Boolean = false, enabled: Boolean = true, size: Int = 21,
) {
    val p = LocalPalette.current
    val fg = when { !enabled -> p.dim; primary -> p.onAccent; else -> p.text }
    Row(
        Modifier.at(x, y).size(w.dp, h.dp)
            .pill(fill = if (primary && enabled) p.accent else p.sky.copy(alpha = .12f), border = if (primary && enabled) Color.Transparent else p.hair)
            .then(if (enabled) Modifier.tap(onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ic != null) Icon(ic, 26, fg)
        Txt(label, size, size + 5, fg, weight = FontWeight.SemiBold)
    }
}

/** A row of text pills (tabs / filters). The pill is [h] high inside a 56 touch box; the selected one is solid accent. */
@Composable
fun <T> TabPills(x: Int, y: Int, options: List<T>, selected: T, label: (T) -> String, h: Int = 48, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.at(x - 2, y - (56 - h) / 2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (o in options) {
            val on = o == selected
            Box(Modifier.height(56.dp).padding(horizontal = 2.dp).tap { onSelect(o) }, contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(h.dp).pill(fill = if (on) p.accent else p.sky.copy(alpha = .12f), border = if (on) Color.Transparent else p.hair).padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Txt(label(o), 19, 23, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Text right-aligned so its right edge is at design x = [right] and its line box top at [y]. */
@Composable
fun TxtR(text: String, size: Int, h: Int, color: Color, right: Int, y: Int, w: Int = 300, family: androidx.compose.ui.text.font.FontFamily = CockpitFonts.Barlow, weight: FontWeight = FontWeight.Medium, tnum: Boolean = false) {
    Txt(text, size, h, color, Modifier.at(right - w, y).width(w.dp), family, weight, TextAlign.End, tnum = tnum)
}

/** Album-art placeholder: four hairline rings and a round data-colour disc on s2, clipped to a 22 px tile (no artwork source yet). */
@Composable
fun AlbumArt(modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val r22 = 22.dp.toPx()
        val tile = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r22))) }
        clipPath(tile) {
            drawRect(p.s2)
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val corner = size.width * 0.70710677f
            for (f in floatArrayOf(.22f, .36f, .52f, .70f)) drawCircle(p.blue.copy(alpha = .25f), (f + .005f) * corner, c, style = androidx.compose.ui.graphics.drawscope.Stroke(.01f * corner))
            drawCircle(p.cyan, size.width * .13f, c)
        }
    }
}

/**
 * Segmented slider: [n] segments lit up to [frac] (read in the draw phase only), an optional round knob, and a touch strip at
 * least 56 high. Dragging or tapping calls [onFrac] with 0..1. Pass no [onFrac] for a display-only bar.
 */
@Composable
fun SegSlider(x: Int, y: Int, w: Int, h: Int, n: Int, frac: () -> Float, knob: Boolean = false, onFrac: ((Float) -> Unit)? = null) {
    val p = LocalPalette.current
    val strip = maxOf(h, 56)
    val unlit = p.sky.copy(alpha = .18f)
    val pad = (strip - h) / 2
    var m = Modifier.at(x, y - pad).size(w.dp, strip.dp)
    if (onFrac != null) m = m.pointerInput(onFrac) {
        awaitEachGesture {
            val d = awaitFirstDown()
            onFrac((d.position.x / size.width).coerceIn(0f, 1f))
            drag(d.id) { ch -> onFrac((ch.position.x / size.width).coerceIn(0f, 1f)); ch.consume() }
        }
    }
    Canvas(m) {
        val gap = 3.dp.toPx(); val sw = (size.width - gap * (n - 1)) / n; val hh = h.dp.toPx(); val top = (size.height - hh) / 2
        val f = frac().coerceIn(0f, 1f); val lit = Math.round(f * n)
        for (i in 0 until n) drawRoundRect(if (i < lit) p.cyan else unlit, androidx.compose.ui.geometry.Offset(i * (sw + gap), top), androidx.compose.ui.geometry.Size(sw, hh), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        if (knob) {
            val c = androidx.compose.ui.geometry.Offset(f * size.width, size.height / 2)
            drawCircle(p.cyan.copy(alpha = .22f), 24.dp.toPx(), c); drawCircle(Color.White, 17.dp.toPx(), c)
        }
    }
}

/** A 44 high toggle / action pill with a small icon in front, inside a 56 high touch strip. */
@Composable
fun OptionChip(x: Int, y: Int, w: Int, label: String, ic: Ic?, on: Boolean, onClick: () -> Unit, h: Int = 44) {
    val p = LocalPalette.current
    Box(Modifier.at(x, y - (56 - h) / 2).size(w.dp, 56.dp).tap(onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.size(w.dp, h.dp).pill(fill = if (on) p.accent else p.sky.copy(alpha = .12f), border = if (on) Color.Transparent else p.hair),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ic != null) Icon(ic, 20, if (on) p.onAccent else p.text)
            Txt(label, 19, 23, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold)
        }
    }
}

/** On / off switch, 88 x 44 (Ford: a pill, white knob when on). Touch strip is 56 high. */
@Composable
fun ToggleSwitch(x: Int, y: Int, on: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.at(x, y - 6).size(88.dp, 56.dp).tap(onToggle), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(88.dp, 44.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(if (on) p.accent else p.sky.copy(alpha = .2f), cornerRadius = r)
            if (!on) drawRoundRect(p.hair, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(if (on) Color.White else p.text.copy(alpha = .78f), 16.dp.toPx(), androidx.compose.ui.geometry.Offset(if (on) size.width - 22.dp.toPx() else 22.dp.toPx(), size.height / 2))
        }
    }
}
