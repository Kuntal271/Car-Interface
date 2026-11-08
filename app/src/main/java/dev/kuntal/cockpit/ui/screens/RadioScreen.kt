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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.*
import dev.kuntal.cockpit.ui.components.*
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlinx.coroutines.delay

import androidx.compose.foundation.layout.offset

private enum class RadioSource(val label: String) { Bluetooth("Bluetooth"), Usb("USB"), Radio("Radio"), Streaming("Streaming") }

private fun scaleX(mhz: Double) = 88.4 + (mhz - 88.0) * 50.9

/**
 * Radio, demo mode: an FM tuner over [RadioState] with invented stations. The real radio is the unit's tuner app and is launched by
 * intent when Demo data is off; nothing here touches a tuner.
 */
@Composable
fun RadioDemoScreen(onOpenReal: () -> Unit, onNotice: (String) -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    var radio by remember { mutableStateOf(RadioState()) }
    var source by remember { mutableStateOf(RadioSource.Radio) }
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(24, 140).size(1120.dp, 307.dp).card())
            Txt("FM \u00B7 MHz", 19, 21, p.dim, Modifier.at(46, 154))
            Box(Modifier.at(414, 277).size(707.dp, 78.dp).background(p.sky.copy(alpha = .08f), RoundedCornerShape(18.dp)))
            Txt("RDS", 19, 23, p.dim, Modifier.at(430, 290))
            for (m in 88..108 step 2) Txt(m.toString(), 20, 24, p.text, Modifier.at((scaleX(m.toDouble()) - 20).toInt(), 362).width(40.dp), align = TextAlign.Center)
            Canvas(Modifier.fillMaxSize()) {
                val d = 1.dp.toPx()
                var f = 87.5; var k = 0
                while (f <= 108.6) {
                    val x = scaleX(f).toFloat() * d
                    val whole = k % 2 == 1                                        // k counts half-MHz steps from 87.5
                    val even = whole && ((f + 0.001).toInt() % 2 == 0)
                    val (top, w) = if (!whole) 402f to 1.2f else if (even) 388f to 2f else 394f to 2f
                    drawLine(p.sky, Offset(x, top * d), Offset(x, 424f * d), w * d)
                    f += 0.5; k++
                }
            }
        }
        DemoMark(800, 168)
        TabPills(24, 80, RadioSource.entries, source, { it.label }) { if (it == RadioSource.Radio) source = it else onNotice("${it.label} is not part of the demo") }
        TabPills(612, 84, listOf("FM", "AM"), "FM", { it }, h = 40) { if (it == "AM") onNotice("AM is not part of the demo") }
        Row(Modifier.at(791, 84).height(40.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) { Txt(if (radio.stereo) "Stereo" else "Mono", 18, 22, p.cyan, weight = FontWeight.SemiBold) }
        Row(Modifier.at(898, 84).height(40.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) { Txt("RDS", 18, 22, p.cyan, weight = FontWeight.SemiBold) }
        Icon(Ic.Signal, 24, p.cyan, Modifier.at(985, 92))
        Txt(if (radio.signalDb >= 30) "Strong" else "Weak", 19, 21, p.text, Modifier.at(1018, 94))
        BaselineRow(1144, 114, gap = 4, alignEnd = true) { Txt("${radio.signalDb}", 24, 29, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true); Txt("dB\u00B5V", 18, 22, p.dim, family = cond) }

        Txt(RadioDemo.mhzText(radio.tenths), 210, 172, p.text, Modifier.baselineAt(46, 338), cond, FontWeight.SemiBold, tnum = true)
        Txt(radio.station?.name?.ifEmpty { "FM " + RadioDemo.mhzText(radio.tenths) } ?: "No station", 54, 54, p.text, Modifier.at(414, 158).width(708.dp), cond, FontWeight.SemiBold)
        val idx = radio.presetIndex
        Txt(if (idx != null) "Preset ${idx + 1}" + (radio.station?.name?.takeIf { it.isNotEmpty() }?.let { " \u00B7 Bajao!".takeIf { _ -> it == "Red FM" } ?: "" } ?: "") else "Not saved", 26, 29, p.dim, Modifier.at(414, 224).width(708.dp), cond, FontWeight.SemiBold)
        Txt(radio.station?.let { "Now \u00B7 " + it.now } ?: "No RDS data", 20, 24, p.text, Modifier.at(430, 320).width(676.dp))
        // needle
        Canvas(Modifier.fillMaxSize()) {
            val d = 1.dp.toPx(); val x = scaleX(radio.mhz).toFloat() * d
            drawLine(p.cyan, Offset(x, 362 * d), Offset(x, 426 * d), 2 * d)
            for (pr in radio.presets) if (pr != null && pr != radio.tenths) { val px = scaleX(pr / 10.0).toFloat() * d; val t = androidx.compose.ui.graphics.Path().apply { moveTo(px, 426 * d); lineTo(px - 5 * d, 436 * d); lineTo(px + 5 * d, 436 * d); close() }; drawPath(t, p.blue) }
        }

        PillBtn(24, 460, 195, "Seek", { radio = radio.seek(false) }, h = 96, ic = Ic.Prev)
        for ((x, ic, dl) in listOf(Triple(232, Ic.Minus, -1), Triple(439, Ic.Plus, 1))) {
            Row(Modifier.at(x, 460).size(195.dp, 96.dp).pill(fill = p.sky.copy(alpha = .12f), border = p.hair).tap { radio = radio.tune(dl) }, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, 26, p.text); Txt("0.1", 26, 31, p.text, family = cond, weight = FontWeight.SemiBold); Txt("MHz", 19, 23, p.dim, family = cond)
            }
        }
        Row(Modifier.at(646, 460).size(195.dp, 96.dp).pill(fill = p.sky.copy(alpha = .12f), border = p.hair).tap { radio = radio.seek(true) }, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { Txt("Seek", 21, 26, p.text, weight = FontWeight.SemiBold); Icon(Ic.Next, 26, p.text) }
        PillBtn(854, 460, 290, "Save preset", { radio = radio.save() }, h = 96, ic = Ic.Plus)

        radio.presets.forEachIndexed { i, pr ->
            val cx = 24 + 188.65f * (i % 6); val cy = if (i < 6) 568 else 678
            val on = i == idx
            val st = pr?.let { t -> RadioDemo.STATIONS.firstOrNull { Math.round(it.mhz * 10).toInt() == t } }
            Box(Modifier.at(cx.toInt(), cy).size(177.dp, 97.dp).background(if (on) p.accent else p.sky.copy(alpha = .12f), RoundedCornerShape(22.dp)).then(if (pr != null) Modifier.tap { radio = radio.select(i) } else Modifier)) {
                Txt("P${i + 1}", 18, 18, if (on) p.onAccent else p.dim, Modifier.at(16, 8), weight = FontWeight.Bold)
                if (pr != null) BaselineRow(16, 62, gap = 6) { Txt(RadioDemo.mhzText(pr), 44, 39, if (on) p.onAccent else p.text, family = cond, weight = FontWeight.Bold, tnum = true); Txt("MHz", 18, 22, if (on) p.onAccent else p.dim, family = cond) }
                else Txt("\u2014", 44, 35, p.text, Modifier.at(16, 25), cond, FontWeight.Medium)
                Txt(if (pr == null) "Empty" else st?.name?.ifEmpty { "Unnamed" } ?: "Unnamed", 18, 20, if (on) p.onAccent else p.dim, Modifier.at(16, 66), weight = FontWeight.SemiBold)
            }
        }
    }
}
