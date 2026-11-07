package dev.kuntal.cockpit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.absoluteOffset
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/** Place a child at design coordinates (x, y) of its parent box. */
fun Modifier.at(x: Int, y: Int): Modifier = absoluteOffset(x.dp, y.dp)

/** Text with the line box fixed to [h] design px (like the CSS boxes the screens were measured from); glyphs centred in it. */
@Composable
fun Txt(
    text: String, size: Int, h: Int, color: Color,
    modifier: Modifier = Modifier,
    family: FontFamily = CockpitFonts.Barlow,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = 0.012.em,       // Ford sets .012em on all text; numerals pass 0
    tnum: Boolean = false,                     // tabular figures for live values (digits keep one width, so numbers do not jitter)
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, fontFamily = family, fontWeight = weight, lineHeight = h.sp, textAlign = align,
            letterSpacing = letterSpacing, fontFeatureSettings = if (tnum) "tnum" else null,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
    )
}

/** Text whose string is computed from state. Only recomposes when the *string* changes, not on every telemetry sample. */
@Composable
fun LiveTxt(
    read: () -> String, size: Int, h: Int, color: Color,
    modifier: Modifier = Modifier,
    family: FontFamily = CockpitFonts.Barlow,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = 0.012.em,
    tnum: Boolean = false,
) {
    val r by rememberUpdatedState(read)
    val s by remember { derivedStateOf { r() } }
    Txt(s, size, h, color, modifier, family, weight, align, letterSpacing, tnum)
}

/** Glass card: Ford-surface fill, soft top sheen, 1 px hairline and a 1 px top highlight. Radius 24. */
@Composable
fun Modifier.card(fillAlpha: Float = 1f, radius: Dp = 24.dp, flat: Boolean = false): Modifier {
    val p = LocalPalette.current
    return drawBehind {
        val r = CornerRadius(radius.toPx())
        drawRoundRect(p.s1.copy(alpha = fillAlpha), cornerRadius = r)
        if (!flat) drawRoundRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = .06f), .45f to Color.White.copy(alpha = .016f)),
            cornerRadius = r,
        )
        val hw = 1.dp.toPx()
        drawRoundRect(
            p.hair, topLeft = Offset(hw / 2, hw / 2), size = Size(size.width - hw, size.height - hw),
            cornerRadius = CornerRadius(r.x - hw / 2), style = Stroke(hw),
        )
        clipRect(top = 0f, bottom = 26.dp.toPx()) {
            drawRoundRect(
                Color.White.copy(alpha = .08f), topLeft = Offset(0f, hw), size = Size(size.width, size.height),
                cornerRadius = r, style = Stroke(hw),
            )
        }
    }
}

/** Fully round glass chip. */
@Composable
fun Modifier.pill(fill: Color = Color.Transparent, border: Color? = null): Modifier {
    val p = LocalPalette.current
    return drawBehind {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(fill, cornerRadius = r)
        val hw = 1.dp.toPx()
        drawRoundRect(border ?: p.hair, topLeft = Offset(hw / 2, hw / 2), size = Size(size.width - hw, size.height - hw), cornerRadius = CornerRadius(r.x - hw / 2), style = Stroke(hw))
    }
}

/** Centre a child of fixed size inside the available space (used for badges that hug their text). */
fun Modifier.fixedSize(w: Int, h: Int): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = w.dp.roundToPx(), maxWidth = w.dp.roundToPx(), minHeight = h.dp.roundToPx(), maxHeight = h.dp.roundToPx()))
    layout(p.width, p.height) { p.place(0, 0) }
}

/** Place a text so its first baseline lands on design y = [y] (and its left edge on x). Pixel-faithful regardless of font metrics. */
fun Modifier.baselineAt(x: Int, y: Int): Modifier = layout { m, c ->
    val p = m.measure(c)
    val bl = p[FirstBaseline]
    layout(p.width, 0) { p.place(x.dp.roundToPx(), y.dp.roundToPx() - bl) }
}

/** A value and its unit on one shared baseline at design y = [baseline], with a 6 px gap (what the design's flex baseline row does). */
@Composable
fun ValueUnit(x: Int, baseline: Int, value: @Composable () -> Unit, unit: @Composable () -> Unit, gap: Int = 6) {
    Layout({ value(); unit() }, Modifier) { ms, _ ->
        val v = ms[0].measure(Constraints())
        val u = ms.getOrNull(1)?.measure(Constraints())          // a tile may have no unit
        val gp = if (u == null) 0 else gap.dp.roundToPx()
        layout(v.width + gp + (u?.width ?: 0), 0) {
            v.place(x.dp.roundToPx(), baseline.dp.roundToPx() - v[FirstBaseline])
            u?.place(x.dp.roundToPx() + v.width + gp, baseline.dp.roundToPx() - u[FirstBaseline])
        }
    }
}

/** Recents / app tile surface: s1 with a soft sheen and a soft hairline, radius 22. */
@Composable
fun Modifier.tile(): Modifier {
    val p = LocalPalette.current
    return drawBehind {
        val r = CornerRadius(22.dp.toPx())
        drawRoundRect(p.s1, cornerRadius = r)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .06f), Color.White.copy(alpha = .02f))), cornerRadius = r)
        val hw = 1.dp.toPx()
        drawRoundRect(p.hairSoft, topLeft = Offset(hw / 2, hw / 2), size = Size(size.width - hw, size.height - hw), cornerRadius = CornerRadius(r.x - hw / 2), style = Stroke(hw))
    }
}

/** Transport / action button: round, sky tint with a hairline; the primary one is solid Ford blue. */
@Composable
fun Modifier.roundBtn(primary: Boolean = false): Modifier {
    val p = LocalPalette.current
    return if (primary) pill(fill = p.accent, border = Color.Transparent) else pill(fill = p.sky.copy(alpha = .14f))
}

/**
 * Children laid out left to right on one shared baseline at design y = [baseline]. Anchored by their left edge at [x],
 * or by their right edge when [alignEnd] (x is then the right edge). [gap] design px between children.
 */
@Composable
fun BaselineRow(x: Int, baseline: Int, gap: Int = 6, alignEnd: Boolean = false, content: @Composable () -> Unit) {
    Layout(content, Modifier) { ms, _ ->
        val ps = ms.map { it.measure(Constraints()) }
        val gp = gap.dp.roundToPx()
        val total = ps.sumOf { it.width } + gp * (ps.size - 1)
        val x0 = if (alignEnd) x.dp.roundToPx() - total else x.dp.roundToPx()
        layout(total, 0) {
            var cx = x0
            for (pl in ps) { pl.place(cx, baseline.dp.roundToPx() - pl[FirstBaseline]); cx += pl.width + gp }
        }
    }
}

/** Tap target with press feedback (a 3 % squeeze) and no ripple. Min touch size is the caller's job (56 dp). */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) .97f else 1f, tween(90), label = "tap")
    return this.graphicsLayer { scaleX = sc; scaleY = sc }.clickable(interactionSource = src, indication = null, onClick = onClick)
}
