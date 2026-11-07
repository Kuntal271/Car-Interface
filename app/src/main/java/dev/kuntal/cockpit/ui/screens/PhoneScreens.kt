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

/** Who the in-app demo call is with. Starting one places no real call. */
data class DemoCall(val contact: DemoContact)

private enum class PhoneTab(val label: String) { Recents("Recents"), Contacts("Contacts"), Saved("Saved") }

/**
 * Phone, demo mode: a dial pad, recent calls, contacts and saved numbers from invented data. The real phone is the unit's
 * vendor app and is launched by intent when Demo data is off. "Call" starts the in-app demo call screen only.
 */
@Composable
fun PhoneDemoScreen(call: DemoCall?, onCall: (DemoContact) -> Unit, onEnd: () -> Unit, onNotice: (String) -> Unit) {
    if (call != null) { PhoneCallScreen(call, onEnd); return }
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    var tab by rememberSaveable { mutableStateOf(PhoneTab.Recents) }
    var dialed by rememberSaveable { mutableStateOf(PhoneDemo.DIALED) }
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(25, 80).size(468.dp, 83.dp).card())
            Box(Modifier.at(25, 177).size(468.dp, 598.dp).card())
            Box(Modifier.at(518, 140).size(626.dp, 635.dp).card())
            Icon(Ic.Phone, 30, p.cyan, Modifier.at(46, 105))
            Txt("Pixel 8", 24, 28, p.text, Modifier.at(98, 95), cond, FontWeight.SemiBold)
            Txt("Hands-free \u00B7 connected", 19, 23, p.dim, Modifier.at(98, 124))
            Icon(Ic.Signal, 24, p.dim, Modifier.at(448, 98)); Icon(Ic.Batt, 24, p.dim, Modifier.at(404, 124))
            Txt("82%", 20, 24, p.text, Modifier.at(435, 126))
        }
        DemoMark(334, 100)
        Txt(dialed, 54, 49, p.text, Modifier.at(46, 197), cond, FontWeight.SemiBold, letterSpacing = 1.sp, tnum = true)
        Box(Modifier.at(414, 196).size(62.dp, 56.dp).background(p.sky.copy(alpha = .12f), RoundedCornerShape(16.dp)).tap { dialed = PhoneDemo.key(dialed, '<') }, contentAlignment = Alignment.Center) { Icon(Ic.Close, 26, p.text) }
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        keys.forEachIndexed { i, (d, l) ->
            val x = 40 + 149.5f * (i % 3); val y = 275 + 97 * (i / 3)
            Box(Modifier.at(x.toInt(), y).size(139.dp, 84.dp).background(p.sky.copy(alpha = .12f), RoundedCornerShape(22.dp)).tap { dialed = PhoneDemo.key(dialed, if (d == "0" && false) '+' else d[0]) }, contentAlignment = Alignment.Center) {
                Txt(d, 44, 44, p.text, Modifier.at(0, -14), cond, FontWeight.Bold, align = TextAlign.Center)
                if (l.isNotEmpty()) Txt(l, 18, 22, p.dim, Modifier.at(0, 24), align = TextAlign.Center)
            }
        }
        PillBtn(40, 664, 438, "Call", { if (dialed.any { it.isDigit() }) onCall(PhoneDemo.contactFor(dialed)) else onNotice("Enter a number first") }, h = 96, ic = Ic.Phone, primary = true)

        TabPills(518, 80, PhoneTab.entries, tab, { it.label }) { tab = it }
        PillBtn(1004, 80, 140, "Search", { onNotice("Search is not part of the demo") }, h = 48, ic = Ic.Search)
        val rows: List<DemoCallEntry> = when (tab) {
            PhoneTab.Recents -> PhoneDemo.RECENTS
            PhoneTab.Contacts -> PhoneDemo.CONTACTS.map { DemoCallEntry(it, "Mobile", "") }
            PhoneTab.Saved -> PhoneDemo.SAVED.map { DemoCallEntry(it, "Saved", "") }
        }
        rows.take(7).forEachIndexed { i, e ->
            val y = 146 + 80 * i
            val ini = e.contact.initials
            Box(Modifier.at(538, y + 14).size(52.dp, 52.dp).background(p.s2, CircleShape).pill(border = p.blue), contentAlignment = Alignment.Center) {
                if (ini.isEmpty()) Icon(Ic.User, 26, p.dim) else Txt(ini, 24, 28, p.text, family = cond, weight = FontWeight.ExtraBold)
            }
            Row(Modifier.at(608, y + 14), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(e.contact.name, 23, 28, p.text, weight = FontWeight.SemiBold)
                if (e.missed) Row(Modifier.size(80.dp, 28.dp).background(p.warn, RoundedCornerShape(50)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Txt("Missed", 18, 22, p.onWarn, weight = FontWeight.Bold) }
            }
            Txt(e.detail, 18, 22, p.dim, Modifier.at(608, y + 42), weight = FontWeight.Normal)
            if (e.time.isNotEmpty()) TxtR(e.time, 18, 22, p.dim, 1050, y + 28, 120)
            Box(Modifier.at(1068, y + 12).size(56.dp, 56.dp).background(p.s2, CircleShape).pill(border = p.blue).tap { onCall(e.contact) }, contentAlignment = Alignment.Center) { Icon(Ic.Phone, 26, p.blue) }
            if (i < rows.size - 1 && i < 6) Box(Modifier.at(538, y + 79).size(586.dp, 1.dp).background(p.hairSoft))
        }
    }
}

/** The in-call face of the Phone demo: caller, a ticking timer, six toggles, a volume bar and End call. */
@Composable
private fun PhoneCallScreen(call: DemoCall, onEnd: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    var secs by remember { mutableIntStateOf(if (DebugHooks.snapshot) 42 else 0) }
    LaunchedEffect(Unit) { while (!DebugHooks.snapshot) { delay(1_000); secs++ } }
    var speaker by remember { mutableStateOf(true) }; var muted by remember { mutableStateOf(false) }; var hold by remember { mutableStateOf(false) }
    var vol by remember { mutableStateOf(0.6f) }
    val name = call.contact.name
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(24, 80).size(560.dp, 696.dp).card())
            Row(Modifier.at(48, 101).height(31.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Ic.Phone, 16, p.cyan); Txt("In call \u00B7 Pixel 8", 18, 22, p.cyan, weight = FontWeight.SemiBold) }
            Icon(Ic.Signal, 22, p.dim, Modifier.at(456, 114)); Txt("HD voice", 19, 23, p.dim, Modifier.at(486, 116))
            Box(Modifier.at(48, 262).size(250.dp, 250.dp).background(p.s2, RoundedCornerShape(36.dp)))
            Txt(call.contact.initials.ifEmpty { "?" }, 128, 128, p.text, Modifier.at(48, 262).size(250.dp, 250.dp), cond, FontWeight.ExtraBold, TextAlign.Center)
            Txt("Mobile", 19, 21, p.dim, Modifier.at(322, 275))
            Txt("Audio \u00B7 car speakers", 20, 29, p.dim, Modifier.at(48, 727))
            Txt("Mic level", 20, 24, p.dim, Modifier.at(433, 731)); Txt("\u221218 dB", 24, 29, p.text, Modifier.at(504, 727), cond, FontWeight.SemiBold)
            Box(Modifier.at(608, 572).size(536.dp, 96.dp).card())
            Icon(Ic.Vol, 30, p.cyan, Modifier.at(632, 605))
        }
        DemoMark(380, 98)
        val parts = name.split(' ').let { if (it.size == 2) it else listOf(name) }
        Txt(parts.getOrElse(0) { "" }, 76, 76, p.text, Modifier.at(322, 304), cond, FontWeight.SemiBold)
        if (parts.size > 1) Txt(parts[1], 76, 76, p.text, Modifier.at(322, 380), cond, FontWeight.SemiBold)
        Txt(call.contact.number, 28, 34, p.text, Modifier.at(322, 464), cond, FontWeight.SemiBold, tnum = true)
        LiveTxt({ PhoneDemo.timer(secs) }, 64, 58, p.text, Modifier.at(48, 641), cond, FontWeight.SemiBold, tnum = true)
        SegSlider(48, 708, 512, 14, 40, frac = { 0.75f })

        val tiles = listOf(
            Triple("Mute", Ic.Mute, muted) to { muted = !muted }, Triple("Keypad", Ic.Grid, false) to { },
            Triple("Speaker", Ic.Vol, speaker) to { speaker = !speaker }, Triple("Hold", Ic.Pause, hold) to { hold = !hold },
            Triple("Add call", Ic.Plus, false) to { }, Triple("Phone", Ic.Phone, false) to { },
        )
        tiles.forEachIndexed { i, (t, act) ->
            val (label, ic, on) = t
            Box(Modifier.at(608 + 274 * (i % 2), 80 + 164 * (i / 2)).size(262.dp, 152.dp).background(if (on) p.accent else p.sky.copy(alpha = .12f), RoundedCornerShape(26.dp)).tap(act), contentAlignment = Alignment.Center) {
                Column2 { Icon(ic, 40, if (on) p.onAccent else p.text); Txt(label, 20, 24, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold) }
            }
        }
        SegSlider(678, 609, 342, 22, 24, frac = { vol }, knob = true) { vol = it }
        BaselineRow(1120, 637, gap = 4, alignEnd = true) { Txt("${Math.round(vol * 30)}", 40, 39, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true); Txt("/ 30", 23, 28, p.dim, family = cond) }
        Row(Modifier.at(608, 680).size(536.dp, 96.dp).background(p.warn, RoundedCornerShape(50)).tap(onEnd), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Phone, 30, p.onWarn, Modifier.rotate(135f)); Txt("End call", 21, 26, p.onWarn, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Column2(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
