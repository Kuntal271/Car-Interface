package dev.kuntal.cockpit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Door
import dev.kuntal.cockpit.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/*
 * The design's top-down car (ui.js UI.car + UI.radar), drawn in its own SVG units: x 30..290, y 32..620 for the body, bumpers
 * at y 38 (front) and 548 (rear). The caller sets the transform, so the same drawing serves the Car screen and the Parking card.
 */

private const val BODY = "M70 96Q70 40 124 40H196Q250 40 250 96V468Q250 490 228 490H92Q70 490 70 468Z"
private const val BONNET = "M90 64Q90 58 98 58H222Q230 58 230 64V158H90Z"
private const val WS = "M94 166H226L216 214H104Z"
private const val RW = "M104 404H216L224 438H96Z"
private const val MIRRORS = "M52 196l-14 7 6 10 12-4zM268 196l14 7-6 10-12-4z"

private fun pol(cx: Float, cy: Float, r: Float, deg: Float): Offset { val a = Math.toRadians(deg.toDouble()); return Offset(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat()) }

/** Radar arcs hugging a bumper at svg y = [y]; dir -1 = front (up), +1 = rear (down). One path per (sensor, band). */
private fun radarArcs(y: Float, dir: Int): List<List<Path>> {
    val xs = floatArrayOf(92f, 133f, 187f, 228f); val angs = floatArrayOf(-26f, -9f, 9f, 26f)
    return List(4) { i ->
        List(3) { k0 ->
            val k = k0 + 1; val r = 8f + k * 17f
            val a = (if (dir < 0) 0f else 180f) + angs[i] * (if (dir < 0) 1f else -1f)
            val p1 = pol(xs[i], y, r, a - 17f); val p2 = pol(xs[i], y, r, a + 17f)
            PathParser().parsePathString("M${p1.x} ${p1.y}A$r $r 0 0 1 ${p2.x} ${p2.y}").toPath()
        }
    }
}

class CarArt internal constructor() {
    internal val body = path(BODY); internal val bonnet = path(BONNET); internal val ws = path(WS); internal val rw = path(RW)
    internal val mirrors = path(MIRRORS); internal val centre = path("M160 62V156"); internal val spareTick = path("M160 484v-4")
    internal val front = radarArcs(38f, -1); internal val rear = radarArcs(548f, +1)
    private fun path(d: String) = PathParser().parsePathString(d).toPath()
}

@Composable
fun rememberCarArt(): CarArt = remember { CarArt() }

/** Draw the car in svg units. [front] / [rear] switch the two radar rows (the Parking card shows the rear only). */
fun DrawScope.drawCarTop(art: CarArt, c: CanboxState, p: Palette, front: Boolean = true, rear: Boolean = true) {
    fun rrect(x: Float, y: Float, w: Float, h: Float, rx: Float, fill: Color?, stroke: Color?, sw: Float, dash: Boolean = false) {
        if (fill != null) drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(rx))
        if (stroke != null) drawRoundRect(stroke, Offset(x, y), Size(w, h), CornerRadius(rx), style = Stroke(sw, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null))
    }
    for ((x, y) in listOf(52f to 84f, 246f to 84f, 52f to 368f, 246f to 368f)) rrect(x, y, 22f, 66f, 7f, p.s3, p.sky, 2.5f)
    drawPath(art.body, p.s1); drawPath(art.body, p.sky, style = Stroke(2.5f))
    rrect(82f, 46f, 30f, 6f, 2f, p.sky, null, 0f); rrect(208f, 46f, 30f, 6f, 2f, p.sky, null, 0f)
    val bonOpen = Door.Bonnet in c.doorsOpen
    withTransform({ if (bonOpen) translate(0f, -16f) }) {
        drawPath(art.bonnet, if (bonOpen) p.warn.copy(alpha = .35f) else p.blue.copy(alpha = .10f))
        drawPath(art.bonnet, if (bonOpen) p.warn else p.blue, style = Stroke(if (bonOpen) 2.5f else 1.5f))
        drawPath(art.centre, p.blue, style = Stroke(1.5f))
    }
    for (g in listOf(art.ws, art.rw)) { drawPath(g, p.sky.copy(alpha = .30f)); drawPath(g, p.sky, style = Stroke(1.5f)) }
    rrect(104f, 214f, 112f, 190f, 8f, p.blue.copy(alpha = .16f), p.blue, 1.5f)
    // doors: left side hinges swing out +62 degrees, right side -62; an open door leaves a dashed ghost where it sat
    fun door(open: Boolean, x: Float, y: Float, left: Boolean) {
        if (open) rrect(x, y, 10f, 104f, 2f, p.cyan.copy(alpha = .14f), p.cyan, 1.5f, dash = true)
        rotate(if (open) (if (left) 62f else -62f) else 0f, Offset(x + 5f, y)) {
            if (open) rrect(x, y, 10f, 104f, 2f, p.warn, p.warn, 1f) else rrect(x, y, 10f, 104f, 2f, p.blue, p.sky, 1f)
        }
    }
    door(Door.FrontLeft in c.doorsOpen, 60f, 190f, true); door(Door.RearLeft in c.doorsOpen, 60f, 300f, true)
    door(Door.FrontRight in c.doorsOpen, 250f, 190f, false); door(Door.RearRight in c.doorsOpen, 250f, 300f, false)
    drawPath(art.mirrors, p.s3); drawPath(art.mirrors, p.sky, style = Stroke(1.5f))
    val gateOpen = Door.Trunk in c.doorsOpen
    rotate(if (gateOpen) -70f else 0f, Offset(236f, 490f)) {
        rrect(84f, 456f, 152f, 34f, 6f, if (gateOpen) p.warn.copy(alpha = .35f) else p.blue.copy(alpha = .22f), if (gateOpen) p.warn else p.sky, if (gateOpen) 2.5f else 1.5f)
        drawCircle(p.s3, 30f, Offset(160f, 514f)); drawCircle(p.sky, 30f, Offset(160f, 514f), style = Stroke(3f))
        drawCircle(p.blue, 13f, Offset(160f, 514f), style = Stroke(2.5f)); drawPath(art.spareTick, p.sky, style = Stroke(3f))
    }
    c.radar?.let { r ->
        val unlit = p.blue.copy(alpha = .22f)
        val rearClose = (r.nearestRearM ?: 9.0) < 0.8             // under 0.8 m every lit rear arc turns amber
        fun row(arcs: List<List<Path>>, levels: List<Int>, warnAll: Boolean) {
            for (i in 0 until 4) for (k in 1..3) {
                val lit = k <= (levels.getOrNull(i) ?: 0)
                drawPath(arcs[i][k - 1], if (!lit) unlit else if (warnAll || k == 3) p.warn else p.cyan, style = Stroke(9f))
            }
        }
        if (front) row(art.front, r.front, false)
        if (rear) row(art.rear, r.rear, rearClose)
    }
}
