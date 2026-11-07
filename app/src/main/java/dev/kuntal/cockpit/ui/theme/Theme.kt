package dev.kuntal.cockpit.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.kuntal.cockpit.R
import kotlin.math.min

/** One token set. Colours are never written into screens: they come from here (design spec v2). */
@Immutable
class Palette(
    val bg: Color, val s1: Color, val s2: Color, val s3: Color,
    val text: Color,        // white: measured values and titles
    val dim: Color,         // labels, units, annotation tags
    val blue: Color,        // Ford blue / cobalt: structure
    val sky: Color,         // lighter blue: icons, unlit-segment tint
    val cyan: Color,        // ice-cyan: data fills, links/actions, live indicators
    val accent: Color,      // selected controls
    val onAccent: Color,
    val warn: Color,        // amber: warnings only
    val onWarn: Color,
    val hair: Color,
    val hairSoft: Color,
    val bgTop: Color,       // top of the frame gradient
    val glowA: Color, val glowB: Color, val glowC: Color,   // the three soft glows behind the content
)

val FordPalette = Palette(
    bg = Color(0xFF050A14), s1 = Color(0xFF0C1628), s2 = Color(0xFF13213C), s3 = Color(0xFF1A2C50),
    text = Color(0xFFF4F8FF), dim = Color(0xFF93A7CE),
    blue = Color(0xFF2B72FF), sky = Color(0xFF6AA6FF), cyan = Color(0xFF5CD8FF),
    accent = Color(0xFF1F63F0), onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFFFB81C), onWarn = Color(0xFF1A1200),
    hair = Color(0x33 shl 24 or 0x96B4FF), hairSoft = Color(0x1A shl 24 or 0x96B4FF),
    bgTop = Color(0xFF071122),
    glowA = Color(0x57 shl 24 or 0x1F63F0), glowB = Color(0x75 shl 24 or 0x003478), glowC = Color(0x24 shl 24 or 0x2B72FF),
)

val LocalPalette = staticCompositionLocalOf { FordPalette }

object CockpitFonts {
    val Barlow = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )
    val Condensed = FontFamily(
        Font(R.font.barlowcondensed_medium, FontWeight.Medium),
        Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlowcondensed_bold, FontWeight.Bold),
    )
}

/** Design space: every screen is laid out on a 1280 x 800 grid where 1 dp = 1 design px, then scaled to fit the real display. */
const val DESIGN_W = 1280
const val DESIGN_H = 800

@Composable
fun DesignCanvas(palette: Palette = FordPalette, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        val scale = min(constraints.maxWidth / DESIGN_W.toFloat(), constraints.maxHeight / DESIGN_H.toFloat())
        CompositionLocalProvider(
            LocalDensity provides Density(density = scale, fontScale = 1f),   // sp == dp == design px, whatever the unit's dpi or font scale
            LocalPalette provides palette,
        ) {
            Box(Modifier.align(Alignment.Center).requiredSize(DESIGN_W.dp, DESIGN_H.dp).clipToBounds()) { FrameBackground(); content() }
        }
    }
}

/**
 * The frame behind every screen: a vertical gradient with three soft elliptical glows (the design's frame + ::before + ::after).
 * Drawn once into a display list; nothing here changes after the first frame.
 */
@Composable
private fun FrameBackground() {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val base = Brush.verticalGradient(0f to p.bgTop, .7f to p.bg, 1f to p.bg)
            fun glow(c: Color, cx: Float, cy: Float, rx: Float, ry: Float, stop: Float) = Triple(
                Brush.radialGradient(0f to c, stop to c.copy(alpha = 0f), 1f to c.copy(alpha = 0f), center = Offset(cx, cy), radius = rx),
                Offset(cx, cy), ry / rx,
            )
            val glows = listOf(
                glow(p.glowA, .10f * w, 1.12f * h, 1000f * w / DESIGN_W, 560f * w / DESIGN_W, .70f),
                glow(p.glowB, 1.04f * w, -.10f * h, 900f * w / DESIGN_W, 520f * w / DESIGN_W, .72f),
                glow(p.glowC, (-.10f + .35f) * w, (.30f + .35f) * h, .35f * w, .35f * h, 1f),
            )
            val radii = floatArrayOf(1000f * w / DESIGN_W, 900f * w / DESIGN_W, .35f * w)
            onDrawBehind {
                drawRect(base)
                glows.forEachIndexed { i, (brush, center, squash) ->
                    withTransform({ scale(1f, squash, pivot = center) }) { drawCircle(brush, radii[i], center) }
                }
            }
        },
    )
}
