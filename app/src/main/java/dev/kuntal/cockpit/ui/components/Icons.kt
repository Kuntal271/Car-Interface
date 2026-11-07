package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The design system's 24-unit line icons (1.8 stroke, round caps). Paths are the same strings the design site draws. */
enum class Ic(val d: String, val dots: List<Triple<Float, Float, Float>> = emptyList()) {
    Home("M3 11.5 12 4l9 7.5M5.5 10v10.5h13V10M10 20.5v-6h4v6"),
    Gauge("M3.5 17.5a9 9 0 1 1 17 0M12 14l4.5-5.5", listOf(Triple(12f, 14f, 1.4f))),
    Car("M5 17v-5l1.8-5.2A1.5 1.5 0 0 1 8.2 6h7.6a1.5 1.5 0 0 1 1.4.8L19 12v5M3.5 17h17M7 17v2.5M17 17v2.5M5.5 12h13M8 14.5h.01M16 14.5h.01"),
    Music("M9 18V5l11-2v13", listOf(Triple(6.5f, 18f, 2.5f), Triple(17.5f, 16f, 2.5f))),
    Phone("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A15 15 0 0 1 4 5a1 1 0 0 1 1-1z"),
    Nav("M4 11.5 20 4l-7.5 16-2-6.5z"),
    Grid("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"),
    Sliders("M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M9 14v6"),
    Sun("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1", listOf(Triple(12f, 12f, 4f))),
    Batt("M2.5 8h16v9h-16zM20.5 11v3M6 11.5v2M9.5 11.5v2M13 11.5v2"),
    Vol("M4 9.5h3.5L12 6v12l-4.5-3.5H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a8 8 0 0 1 0 11"),
    Bolt("M13 2.5 5 13.5h6l-1 8 8-11h-6z"),
    Thermo("M10 14.5V5a2 2 0 1 1 4 0v9.5a4 4 0 1 1-4 0zM12 9v8"),
    Fan(
        "M12 10.5C12 6 14 3.5 16.5 4.5c2 1 .5 4.5-4.5 6M13.5 12c4.5 0 7 2 6.5 4.5-.5 2.2-4 1.5-6.5-3M12 13.5c0 4.5-2 7-4.5 6-2-1-.5-4.5 4.5-6M10.5 12C6 12 3.5 10 4 7.5c.5-2.2 4-1.5 6.5 3",
        listOf(Triple(12f, 12f, 1.5f)),
    ),
    Fuel("M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15M4 20h11M14 9h2.5a1.5 1.5 0 0 1 1.5 1.5V16a1.5 1.5 0 0 0 3 0V9l-2.5-2.5M7 8h5"),
}

@Composable
fun Icon(ic: Ic, size: Int, color: Color, modifier: Modifier = Modifier) {
    val path = remember(ic) { PathParser().parsePathString(ic.d).toPath() }
    Canvas(modifier.size(size.dp)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, color, style = st)
            for ((cx, cy, r) in ic.dots) drawCircle(color, r, Offset(cx, cy), style = st)
        }
    }
}
