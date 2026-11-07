package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 270 degree tachometer, geometry from the design (700-unit viewBox scaled to the composable's size).
 * Layer 1 (cached): track, amber redline hatch, ticks, numerals, caption. Never redrawn after the first frame.
 * Layer 2 (per frame): the lit sweep arc and the tip, driven by [rpm], read in the draw phase only.
 */
@Composable
fun Tach(rpm: State<Float>, modifier: Modifier = Modifier.size(600.dp), maxRpm: Float = 7000f, redRpm: Float = 6000f) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val s = size.width / 700f
                val c = Offset(350f * s, 350f * s)
                val r1 = 262f * s; val rm = 292f * s; val sw = 60f * s
                val tl = Offset(c.x - rm, c.y - rm); val sz = Size(2 * rm, 2 * rm)
                val redDeg = redRpm / maxRpm * 270f
                val nStr = max(1, ((270f - redDeg) / 3.2f).roundToInt()); val pitch = (270f - redDeg) / nStr
                fun pol(r: Float, aDeg: Float): Offset {
                    val a = Math.toRadians(aDeg.toDouble())
                    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
                }
                val numStyle = TextStyle(fontFamily = CockpitFonts.Condensed, fontWeight = FontWeight.SemiBold, fontSize = (40f * s).toSp(), platformStyle = PlatformTextStyle(includeFontPadding = false))
                val steps = (maxRpm / 500f).roundToInt()
                val numerals = (0..steps step 2).map { k ->
                    val a = -135f + k * 500f / maxRpm * 270f
                    val layout = measurer.measure(
                        (k / 2).toString(),
                        numStyle.copy(color = if (k * 500f >= redRpm) p.warn else p.text),
                    )
                    val t = pol(r1 - 62f * s, a)
                    Triple(layout, t.x - layout.size.width / 2f, t.y + 13f * s - layout.firstBaseline)
                }
                val caption = measurer.measure(
                    "rpm x 1000",
                    TextStyle(fontFamily = CockpitFonts.Barlow, fontWeight = FontWeight.Medium, fontSize = (24f * s).toSp(), color = p.dim, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
                onDrawBehind {
                    drawArc(p.dim.copy(alpha = .13f), 135f, redDeg, false, tl, sz, style = Stroke(sw))
                    for (k in 0 until nStr) drawArc(p.warn.copy(alpha = .85f), 135f + redDeg + k * pitch, 1.3f, false, tl, sz, style = Stroke(sw))
                    for (k in 0..steps) {
                        val a = -135f + k * 500f / maxRpm * 270f
                        val major = k % 2 == 0
                        val red = k * 500f >= redRpm
                        drawLine(
                            if (red) p.warn else p.dim,
                            pol(r1 - 14f * s, a), pol(r1 - (if (major) 34f else 24f) * s, a),
                            strokeWidth = (if (major) 3f else 1.5f) * s, cap = StrokeCap.Round,
                        )
                    }
                    for ((layout, x, y) in numerals) drawText(layout, topLeft = Offset(x, y))
                    drawText(caption, topLeft = Offset(c.x - caption.size.width / 2f, 642f * s - caption.firstBaseline))
                }
            },
        )
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val s = size.width / 700f
                val c = Offset(350f * s, 350f * s)
                val rm = 292f * s; val sw = 60f * s
                val tl = Offset(c.x - rm, c.y - rm); val sz = Size(2 * rm, 2 * rm)
                val redFrac = redRpm / maxRpm * 270f / 360f
                val lo = lerp(Color.Black, p.blue, .62f)
                val sweep = Brush.sweepGradient(
                    0f to lo, redFrac * .72f to p.cyan, redFrac to p.cyan, redFrac to p.warn, 270f / 360f to p.warn, 1f to p.warn,
                    center = c,
                )
                val glow = Brush.radialGradient(
                    0f to Color.White.copy(alpha = .9f), .3f to p.cyan.copy(alpha = .45f), 1f to p.cyan.copy(alpha = 0f),
                    center = Offset(c.x, c.y - rm), radius = 46f * s,
                )
                onDrawBehind {
                    val v = (rpm.value / maxRpm).coerceIn(0f, 1f)
                    if (v > 0f) rotate(135f, c) { drawArc(sweep, 0f, v * 270f, false, tl, sz, style = Stroke(sw)) }
                    rotate(-135f + v * 270f, c) {
                        drawCircle(glow, 46f * s, Offset(c.x, c.y - rm))
                        drawCircle(Color.White, 12f * s, Offset(c.x, c.y - rm))
                    }
                }
            },
        )
    }
}
