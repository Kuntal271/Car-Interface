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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.*
import dev.kuntal.cockpit.ui.components.*
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * Maps / Projection, demo mode: the wireless Android Auto link as a simulated status card, the paired phones and the four
 * projection options. Nothing is connected; the "Android Auto" button opens the real maps / projection app by intent.
 */
@Composable
fun ProjectionDemoScreen(onLaunch: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val link = remember { ProjectionState() }
    var mode by rememberSaveable { mutableStateOf("Android Auto") }
    var t1 by rememberSaveable { mutableStateOf(true) }; var t2 by rememberSaveable { mutableStateOf(true) }
    var t3 by rememberSaveable { mutableStateOf(true) }; var t4 by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(24, 80).size(648.dp, 696.dp).card())
            Box(Modifier.at(698, 80).size(446.dp, 292.dp).card())
            Box(Modifier.at(698, 385).size(446.dp, 391.dp).card())
            Txt("Maps \u00B7 Projection", 34, 34, p.text, Modifier.at(48, 101), cond, FontWeight.SemiBold)
            // phone <-> cockpit sketch
            Box(Modifier.at(88, 230).size(112.dp, 192.dp).border2(p.blue, 22))
            Txt("AA", 44, 53, p.cyan, Modifier.at(88, 289).size(112.dp, 53.dp), cond, FontWeight.ExtraBold, TextAlign.Center)
            Box(Modifier.at(408, 252).size(202.dp, 150.dp).border2(p.blue, 14))
            Txt("Cockpit", 40, 48, p.text, Modifier.at(408, 287).size(202.dp, 48.dp), cond, FontWeight.ExtraBold, TextAlign.Center)
            Canvas(Modifier.at(210, 320).size(190.dp, 10.dp)) { val d = 1.dp.toPx(); for (k in 0 until 14) drawCircle(p.cyan, 2.2f * d, Offset((6 + k * 13) * d, 5 * d)) }
            Icon(Ic.Wifi, 34, p.cyan, Modifier.at(288, 292))
            for (k in 1..2) Box(Modifier.at(24 + 216 * k, 506).size(1.dp, 92.dp).background(p.hairSoft))
            Txt("Phone", 19, 21, p.dim, Modifier.at(48, 522)); Txt("Link", 19, 21, p.dim, Modifier.at(256, 520)); Txt("Throughput", 19, 21, p.dim, Modifier.at(463, 520))
            Txt("Paired phones", 19, 21, p.dim, Modifier.at(722, 100)); Txt("+ Add", 19, 21, p.cyan, Modifier.at(1073, 100))
        }
        DemoMark(400, 102)
        Row(Modifier.at(534, 102).height(32.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Txt(if (link.connected) "Connected" else "Not connected", 18, 22, p.cyan, weight = FontWeight.SemiBold) }
        Txt(link.phone, 40, 36, p.text, Modifier.at(48, 549), cond, FontWeight.Bold)
        BaselineRow(256, 583, gap = 4) { Txt("${link.ghz}", 40, 36, p.text, family = cond, weight = FontWeight.Bold); Txt("GHz", 23, 28, p.dim, family = cond) }
        BaselineRow(463, 583, gap = 4) { Txt("${link.mbps}", 40, 36, p.text, family = cond, weight = FontWeight.Bold); Txt("Mb/s", 23, 28, p.dim, family = cond) }
        PillBtn(48, 660, 398, "Android Auto", { mode = "Android Auto"; onLaunch() }, h = 96, ic = Ic.Nav, primary = mode == "Android Auto")
        PillBtn(458, 660, 192, "CarPlay", { mode = "CarPlay" }, h = 96, primary = mode == "CarPlay")

        val phones = listOf(Triple("Pixel 8", "Android Auto \u00B7 connected now", "Active"), Triple("iPhone 13", "CarPlay \u00B7 last used 3 days ago", ""), Triple("Moto G84", "Bluetooth only", ""))
        phones.forEachIndexed { i, (n, sub, tag) ->
            val y = 140 + 75 * i
            Box(Modifier.at(722, y + 2).size(34.dp, 34.dp).background(p.sky.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Icon(Ic.Phone, 20, if (i == 0) p.cyan else p.dim) }
            Txt(n, 22, 26, p.text, Modifier.at(768, y), weight = FontWeight.SemiBold)
            Txt(sub, 18, 22, p.dim, Modifier.at(768, y + 26), weight = FontWeight.Normal)
            if (tag.isNotEmpty()) Row(Modifier.at(1040, y + 8).size(80.dp, 32.dp).pill(border = p.cyan.copy(alpha = .5f)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Txt(tag, 18, 22, p.cyan, weight = FontWeight.SemiBold) }
            else Icon(Ic.ChevR, 22, p.dim, Modifier.at(1096, y + 6))
        }
        val opts = listOf(
            Triple("Launch on connect", "Open Android Auto as soon as the phone joins", t1) to { t1 = !t1 },
            Triple("Prefer wireless", "Use Wi-Fi 5 GHz when the phone supports it", t2) to { t2 = !t2 },
            Triple("Return to Cockpit", "Switch back when ignition goes off", t3) to { t3 = !t3 },
            Triple("Keep Drive gauges on top", "Show a slim rpm / boost strip over projection", t4) to { t4 = !t4 },
        )
        val ys = listOf(400, 488, 587, 664)
        opts.forEachIndexed { i, (o, act) ->
            val (n, sub, on) = o
            Txt(n, 22, 26, p.text, Modifier.at(722, ys[i]), weight = FontWeight.SemiBold)
            Para(sub, 18, 22, p.dim, 294, Modifier.at(722, ys[i] + 26), weight = FontWeight.Normal)
            ToggleSwitch(1032, ys[i] + 6, on, act)
        }
    }
}

private fun Modifier.border2(c: Color, r: Int): Modifier = this.then(Modifier.background(c.copy(alpha = .10f), RoundedCornerShape(r.dp))).drawBehindBorder(c, r)
private fun Modifier.drawBehindBorder(c: Color, r: Int): Modifier = this.drawBehind {
    drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
}
