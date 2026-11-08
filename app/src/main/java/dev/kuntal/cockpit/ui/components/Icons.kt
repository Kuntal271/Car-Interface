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
enum class Ic(val d: String, val dots: List<Triple<Float, Float, Float>> = emptyList(), val fill: String = "") {
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
    Search("M15 15l5.5 5.5", listOf(Triple(10.5f, 10.5f, 6f))),
    Bt("M7 7.5 17 16l-5 4.5V3.5L17 8 7 16.5"),
    Wifi("M3 9a14 14 0 0 1 18 0M6 12.5a9.5 9.5 0 0 1 12 0M9 16a5 5 0 0 1 6 0M12 19.5h.01"),
    ChevR("M9 5l7 7-7 7"),
    Check("M4 12.5l5 5L20 6.5"),
    Plus("M12 4v16M4 12h16"),
    Minus("M4 12h16"),
    Flag("M5 21V4M5 4h13l-2.5 4.5L18 13H5"),
    Close("M5 5l14 14M19 5 5 19"),
    Radio("M3 9h18v11H3zM7 9l11-5.5M13 13.5h5M13 16.5h5", listOf(Triple(8f, 14.5f, 2f))),
    Camera("M3 8h4l1.5-2.5h7L17 8h4v11H3z", listOf(Triple(12f, 13.5f, 3.5f))),
    Park("M4 4h16v16H4zM9.5 17V7.5H13a2.5 2.5 0 0 1 0 5H9.5"),
    Pulse("M2.5 12H7l2.5-6 5 12 2.5-6h4.5"),
    Route("M7 19h7a4 4 0 0 0 0-8h-4a4 4 0 0 1 0-8h7", listOf(Triple(5f, 19f, 2f), Triple(19f, 5f, 2f))),
    Eq("M5 4v16M12 4v16M19 4v16M3 9h4M10 15h4M17 8h4"),
    Snow("M12 3v18M4.2 7.5l15.6 9M19.8 7.5l-15.6 9M9 4.5l3 2.5 3-2.5M9 19.5l3-2.5 3 2.5"),
    Play("", fill = "M7 4.5v15l12.5-7.5z"),
    Pause("", fill = "M6.5 5h4v14h-4zM13.5 5h4v14h-4z"),
    Next("M18 5v14", fill = "M5 5l10 7-10 7z"),
    Prev("M6 5v14", fill = "M19 5 9 12l10 7z"),
    Fan(
        "M12 10.5C12 6 14 3.5 16.5 4.5c2 1 .5 4.5-4.5 6M13.5 12c4.5 0 7 2 6.5 4.5-.5 2.2-4 1.5-6.5-3M12 13.5c0 4.5-2 7-4.5 6-2-1-.5-4.5 4.5-6M10.5 12C6 12 3.5 10 4 7.5c.5-2.2 4-1.5 6.5 3",
        listOf(Triple(12f, 12f, 1.5f)),
    ),
    Fuel("M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15M4 20h11M14 9h2.5a1.5 1.5 0 0 1 1.5 1.5V16a1.5 1.5 0 0 0 3 0V9l-2.5-2.5M7 8h5"),
    Door("M5 20.500V5.500L14 3l5 2.500v15zM14 12.500h.01M3 20.500h18"),
    Warn("M12 3 22 20H2zM12 10v5M12 17.500h.01"),
    Clock("M12 7v5l3.500 2", listOf(Triple(12f, 12f, 9f))),
    Moon("M20 14.500A8.500 8.500 0 1 1 9.500 4 7 7 0 0 0 20 14.500z"),
    Signal("M4 20v-3M9 20v-7M14 20V9M19 20V4"),
    Repeat("M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3"),
    Shuffle("M3 7h3.500c3 0 4 10 8 10H21M18 14l3 3-3 3M3 17h3.500c1.500 0 2.500-2 3.500-4M14.500 7H21M18 4l3 3-3 3"),
    Rows("M8 6h13M8 12h13M8 18h13M3.500 6h.01M3.500 12h.01M3.500 18h.01"),
    User("M4 21a8 8 0 0 1 16 0", listOf(Triple(12f, 8f, 4f))),
    Mic("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5.500 11a6.500 6.500 0 0 0 13 0M12 17.500V21"),
    Tyre("M6 18.500v-8a6 6 0 0 1 12 0v8zM12 7.500v5M12 15.500h.01"),
    Lights("M9 5c5 0 8 3 8 7s-3 7-8 7zM3 8h3M3 12h3M3 16h3"),
    Belt("M8 21l1-9 3 2 3-2 1 9M9 12l6 3", listOf(Triple(12f, 5f, 2f))),
    Brake("M10 16V8h3a2.500 2.500 0 0 1 0 5h-3", listOf(Triple(12f, 12f, 9f))),
    Power("M12 3v8M6.500 6.500a8 8 0 1 0 11 0"),
    Cast("M3 6h18v11H3zM8 21h8M12 17v4M9.500 10.500l2.500 2.500 2.500-2.500"),
    Folder("M3 6.500h6.500l2 2.500H21V19H3z"),
    Picture("M5.5 5H18.5a2.5 2.5 0 0 1 2.5 2.5V16.5a2.5 2.5 0 0 1 -2.5 2.5H5.5a2.5 2.5 0 0 1 -2.5 -2.5V7.5a2.5 2.5 0 0 1 2.5 -2.5zM4 17l5-4.500 3.500 3L16 12l4.500 4.500", listOf(Triple(9f, 10f, 1.7f))),
    Fog("M10 5c5 0 8 3 8 7s-3 7-8 7zM2.500 8l5 1.200M2.500 12.500h5M2.500 17l5-1.200"),
    Storage("M5 14H19a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V16a2 2 0 0 1 2 -2zM5 14l2-8h10l2 8M7.500 17h.01M11 17h6"),
    Video("M3 6h13v12H3zM16 10.500l5-3v9l-5-3"),
    Trash("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13"),
    Export("M12 3v12M7 8l5-5 5 5M4 15v6h16v-6"),
    Steer("M3 12h7M14 12h7M12 14v7", listOf(Triple(12f, 12f, 9f), Triple(12f, 12f, 2f))),
    Recirc("M5 15a8 8 0 0 1 13-6M19 9V4M19 9h-5M19 9a8 8 0 0 1-13 6M5 15v5M5 15h5"),
    Defrost("M4 18c2-3 14-3 16 0M7 14c1.500-2 8.500-2 10 0M9 10c1-1.500 5-1.500 6 0M8 5c1.500 2 0 3 1.500 4M12 4c1.500 2 0 3 1.500 5M16 5c1.500 2 0 3 1.500 4"),
    Key("M12 12h9M18 12v3M21 12v2", listOf(Triple(8f, 12f, 4f))),
    Rear("M4 17V11l2-4h12l2 4v6M3.500 17h17M7 20v-3M17 20v-3M7.500 13.500h.01M16.500 13.500h.01M9 4l-2 2M15 4l2 2"),
    Mute("M4 9.500h3.500L12 6v12l-4.500-3.500H4zM16 9.500l5 5M21 9.500l-5 5"),
    Chevl("M15 5l-7 7 7 7"),
    Chevu("M5 15l7-7 7 7"),
    Chevd("M5 9l7 7 7-7"),
    Calendar("M4 6h16v14H4zM4 10h16M8 3v5M16 3v5"),
    Bell("M6 17V11a6 6 0 0 1 12 0v6l1.500 2h-15zM10 21h4"),
    Usb("M12 3v15M9.5 5.5 12 3l2.5 2.5M12 13l-4-2.5V8M12 15.500l4-2.500v-2M6.5 5.5h3v3h-3z", listOf(Triple(12f, 19.5f, 2f), Triple(16f, 9f, 1.8f))),
    Globe("M3 12h18M12 3c3 3.500 3 14.500 0 18M12 3c-3 3.500-3 14.500 0 18", listOf(Triple(12f, 12f, 9f))),
    Chat("M4 5h16v11H9l-5 4z"),
    Calc("M5 3h14v18H5zM8 7h8M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"),
    Cloud("M7 18a4.500 4.500 0 1 1 1-8.900A6 6 0 0 1 19.500 11 3.500 3.500 0 0 1 18 18z"),
}

/** Parsed once per process: every Icon used to parse its SVG path string again on each composition (each visit to a screen, 20-40 icons). */
private class IconPaths(val stroke: Path?, val fill: Path?)
private val parsed = java.util.concurrent.ConcurrentHashMap<Ic, IconPaths>()
private fun pathsOf(ic: Ic): IconPaths = parsed.getOrPut(ic) {
    IconPaths(
        if (ic.d.isEmpty()) null else PathParser().parsePathString(ic.d).toPath(),
        if (ic.fill.isEmpty()) null else PathParser().parsePathString(ic.fill).toPath(),
    )
}

@Composable
fun Icon(ic: Ic, size: Int, color: Color, modifier: Modifier = Modifier) {
    val paths = remember(ic) { pathsOf(ic) }
    val path = paths.stroke
    val filled = paths.fill
    Canvas(modifier.size(size.dp)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (filled != null) drawPath(filled, color)
            if (path != null) drawPath(path, color, style = st)
            for ((cx, cy, r) in ic.dots) drawCircle(color, r, Offset(cx, cy), style = st)
        }
    }
}
