package dev.kuntal.cockpit.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.ClockFormat
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.MotionMode
import dev.kuntal.cockpit.data.PressureUnit
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.data.SourceKind
import dev.kuntal.cockpit.data.SpeedUnit
import dev.kuntal.cockpit.data.TempUnit
import dev.kuntal.cockpit.data.ThemeId
import dev.kuntal.cockpit.data.Thresholds
import dev.kuntal.cockpit.perf.Perf
import dev.kuntal.cockpit.source.pairedAdapters
import dev.kuntal.cockpit.ui.components.Chips
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.Para
import dev.kuntal.cockpit.ui.components.SettingRow
import dev.kuntal.cockpit.ui.components.Stepper
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import androidx.compose.runtime.collectAsState

enum class SettingsPage(val label: String, val icon: Ic) {
    Display("Display", Ic.Sun), Connections("Connections", Ic.Bt), Vehicle("Vehicle", Ic.Car), About("About", Ic.Flag),
}

/** Only categories with something real behind them are listed; the rest of the design's list (Sound, Shortcuts, Storage...) arrives with its feature. */
@Composable
fun SettingsScreen(settings: Settings, link: Link, onChange: ((Settings) -> Settings) -> Unit, startPage: SettingsPage = SettingsPage.Display) {
    val p = LocalPalette.current
    var page by rememberSaveable { mutableStateOf(startPage) }
    Box(Modifier.fillMaxSize()) {
        // category list: 340 wide, 76 high rows, selected = solid accent pill
        Box(Modifier.at(24, 80).size(340.dp, 696.dp).card())
        SettingsPage.entries.forEachIndexed { i, c ->
            val on = c == page
            Box(Modifier.at(34, 86 + 76 * i).size(320.dp, 76.dp).pill(fill = if (on) p.accent else Color.Transparent, border = Color.Transparent).tap { page = c }) {
                Icon(c.icon, 28, if (on) p.onAccent else p.text, Modifier.at(20, 24))
                Txt(c.label, 22, 28, if (on) p.onAccent else p.text, Modifier.at(62, 24), weight = FontWeight.SemiBold)
                Icon(Ic.ChevR, 20, if (on) p.onAccent else p.dim, Modifier.at(276, 28))
            }
        }
        // detail card
        Box(Modifier.at(388, 80).size(756.dp, 696.dp).card())
        Txt(page.label, 32, 36, p.text, Modifier.at(412, 98), CockpitFonts.Condensed, FontWeight.SemiBold)
        when (page) {
            SettingsPage.Display -> DisplayPage(settings, onChange)
            SettingsPage.Connections -> ConnectionsPage(settings, link, onChange)
            SettingsPage.Vehicle -> VehiclePage(settings, onChange)
            SettingsPage.About -> AboutPage(settings, onChange)
        }
    }
}

private const val X = 412
private const val Y0 = 150

@Composable
private fun DisplayPage(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    SettingRow(X, Y0, "Theme", "Ford blue, or the original Midnight with lime data") {
        Chips(ThemeId.entries, s.theme, { it.label }) { t -> set { it.copy(theme = t) } }
    }
    SettingRow(X, Y0 + 72, "Clock") { Chips(ClockFormat.entries, s.clock, { it.label }) { v -> set { it.copy(clock = v) } } }
    SettingRow(X, Y0 + 144, "Motion", "Reduced snaps gauges instead of gliding", last = true) {
        Chips(MotionMode.entries, s.motion, { it.label }) { v -> set { it.copy(motion = v) } }
    }
}

@Composable
private fun VehiclePage(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    Txt("Units", 18, 22, p.dim, Modifier.at(X, 146))
    SettingRow(X, 168, "Speed and distance") { Chips(SpeedUnit.entries, s.speedUnit, { if (it == SpeedUnit.KMH) "km/h" else "mph" }) { v -> set { it.copy(speedUnit = v) } } }
    SettingRow(X, 240, "Pressure") { Chips(PressureUnit.entries, s.pressureUnit, { if (it == PressureUnit.KPA) "kPa" else it.name.lowercase() }) { v -> set { it.copy(pressureUnit = v) } } }
    SettingRow(X, 312, "Temperature") { Chips(TempUnit.entries, s.tempUnit, { if (it == TempUnit.C) "\u00B0C" else "\u00B0F" }) { v -> set { it.copy(tempUnit = v) } } }
    Txt("Gauges", 18, 22, p.dim, Modifier.at(X, 396))
    SettingRow(X, 418, "Redline") { Stepper(Fmt.grouped(s.redlineRpm), "rpm", { set { Thresholds.redline(it, -1) } }, { set { Thresholds.redline(it, +1) } }) }
    SettingRow(X, 490, "Tach scale") { Stepper(Fmt.grouped(s.tachMaxRpm), "rpm", { set { Thresholds.tachMax(it, -1) } }, { set { Thresholds.tachMax(it, +1) } }) }
    SettingRow(X, 562, "Shift light from") { Stepper(Fmt.grouped(s.shiftRpm), "rpm", { set { Thresholds.shift(it, -1) } }, { set { Thresholds.shift(it, +1) } }) }
    SettingRow(X, 634, "Speed scale", last = true) { Stepper(Fmt.speed(s.speedScaleKmh.toDouble(), s.speedUnit), if (s.speedUnit == SpeedUnit.KMH) "km/h" else "mph", { set { Thresholds.speedScale(it, -1) } }, { set { Thresholds.speedScale(it, +1) } }) }
}

@Composable
private fun ConnectionsPage(s: Settings, link: Link, set: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    // live status of whatever is selected
    val (statusText, statusColor) = when (link) {
        Link.Connected -> "Connected" to p.cyan
        Link.Simulated -> "Simulated drive" to p.cyan
        Link.Connecting -> "Connecting\u2026" to p.dim
        Link.Lost -> "Connection lost, retrying" to p.warn
        Link.Off -> "No data source selected" to p.dim
    }
    Row(Modifier.at(X + 330, 98).size(378.dp, 36.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Txt(statusText, 19, 23, statusColor, weight = FontWeight.SemiBold)
    }
    SourceRow(150, Ic.Gauge, "Simulator", "Drive-cycle replay for demos and testing", s.source == SourceKind.Simulator) { set { it.copy(source = SourceKind.Simulator) } }
    SourceRow(238, Ic.Bt, "OBD adapter \u00B7 Bluetooth", if (s.btName.isNotBlank()) s.btName else "Choose a paired ELM327 adapter", s.source == SourceKind.ObdBluetooth) { set { it.copy(source = SourceKind.ObdBluetooth) } }
    SourceRow(326, Ic.Wifi, "OBD adapter \u00B7 Wi-Fi", "${s.tcpHost}:${s.tcpPort}", s.source == SourceKind.ObdTcp, last = true) { set { it.copy(source = SourceKind.ObdTcp) } }

    when (s.source) {
        SourceKind.ObdBluetooth -> {
            Txt("Paired adapters", 18, 22, p.dim, Modifier.at(X, 428))
            val paired = remember(s.source) { pairedAdapters() }
            if (paired.isEmpty()) {
                Para("None found. Pair the adapter in Android's Bluetooth settings first, then come back here.", 19, 24, p.dim, 708, Modifier.at(X, 462))
            } else {
                paired.take(3).forEachIndexed { i, (name, addr) ->
                    val on = addr == s.btAddress
                    Row(
                        Modifier.at(X, 456 + 62 * i).size(708.dp, 56.dp).pill(fill = if (on) p.accent else p.sky.copy(alpha = .12f), border = if (on) Color.Transparent else p.hair).tap { set { it.copy(btAddress = addr, btName = name) } },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.width(18.dp))
                        Txt(name, 20, 24, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold)
                        Txt(addr, 17, 21, if (on) p.onAccent else p.dim)
                    }
                }
            }
            Row(
                Modifier.at(X, 650).size(300.dp, 56.dp).pill(fill = p.sky.copy(alpha = .12f)).tap {
                    runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Txt("Pair a new adapter", 19, 23, p.text, weight = FontWeight.SemiBold) }
        }
        SourceKind.ObdTcp -> {
            Txt("Adapter address", 18, 22, p.dim, Modifier.at(X, 428))
            TextBox(X, 456, 420, s.tcpHost, "Host or IP") { v -> set { it.copy(tcpHost = v) } }
            TextBox(X + 440, 456, 160, s.tcpPort.toString(), "Port", number = true) { v -> v.toIntOrNull()?.let { n -> set { it.copy(tcpPort = n) } } }
            Para("Join the adapter's own Wi-Fi network first. Most ELM327 adapters answer on 192.168.0.10, port 35000.", 18, 24, p.dim, 708, Modifier.at(X, 530))
        }
        else -> {
            Para("Replaying a built-in drive cycle. None of these numbers come from the car. Choose an OBD adapter to see real data.", 18, 24, p.dim, 708, Modifier.at(X, 428))
        }
    }
}

@Composable
private fun SourceRow(y: Int, ic: Ic, title: String, sub: String, selected: Boolean, last: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.at(X, y).size(708.dp, 88.dp).tap(onClick)) {
        Icon(ic, 32, if (selected) p.cyan else p.sky, Modifier.at(0, 28))
        Txt(title, 22, 27, p.text, Modifier.at(52, 16), weight = FontWeight.SemiBold)
        Txt(sub, 18, 22, p.dim, Modifier.at(52, 48), weight = FontWeight.Normal)
        Row(Modifier.at(558, 22).size(150.dp, 44.dp).pill(fill = if (selected) p.accent else p.sky.copy(alpha = .12f), border = if (selected) Color.Transparent else p.hair), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt(if (selected) "In use" else "Use", 18, 22, if (selected) p.onAccent else p.text, weight = FontWeight.SemiBold)
        }
        if (!last) Box(Modifier.at(0, 87).size(708.dp, 1.dp).background(p.hairSoft))
    }
}

@Composable
private fun TextBox(x: Int, y: Int, w: Int, value: String, hint: String, number: Boolean = false, onValue: (String) -> Unit) {
    val p = LocalPalette.current
    var text by remember(value) { mutableStateOf(value) }
    Box(Modifier.at(x, y).size(w.dp, 56.dp).pill(fill = p.s1), contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) Txt(hint, 20, 26, p.dim, Modifier.padding(start = 22.dp))
        BasicTextField(
            // an empty box is mid-edit, not a new setting: only non-blank text is stored
            value = text, onValueChange = { text = it; if (it.isNotBlank()) onValue(it) }, singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 20.sp, fontFamily = CockpitFonts.Barlow, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(p.cyan),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Uri),
            modifier = Modifier.padding(horizontal = 22.dp).width((w - 44).dp),
        )
    }
}

@Composable
private fun AboutPage(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val info = remember { appInfo(ctx) }
    val perf by Perf.summary.collectAsState()
    SettingRow(X, Y0, "Version") { Txt(info, 21, 26, p.text, weight = FontWeight.SemiBold) }
    SettingRow(X, Y0 + 72, "Frame time", "p50 / p99, last ten seconds. Gate: p99 under 10 ms") {
        Txt(if (perf.count == 0) Fmt.DASH else "%.1f / %.1f ms".format(perf.p50, perf.p99), 21, 26, if (perf.count > 0 && perf.meetsGate()) p.cyan else p.text, weight = FontWeight.SemiBold)
    }
    SettingRow(X, Y0 + 144, "Frame-time overlay", "Live verdict on every screen, for testing") {
        Chips(listOf(false, true), s.perfOverlay, { if (it) "On" else "Off" }) { v -> set { it.copy(perfOverlay = v) } }
    }
    SettingRow(X, Y0 + 216, "Fonts") { Txt("Barlow, SIL Open Font License 1.1", 19, 24, p.dim) }
    SettingRow(X, Y0 + 288, "Vehicle access") { Txt("Read-only (OBD-II mode 01)", 19, 24, p.dim) }
    Para("Ford and the Blue Oval are trademarks of Ford Motor Company. This is an independent, non-commercial personal project, not affiliated with or endorsed by Ford.", 18, 26, p.dim, 708, Modifier.at(X, Y0 + 380))
}

private fun appInfo(ctx: Context): String {
    val pi = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (_: Exception) { return "" }
    val debug = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return "${pi.versionName} (${if (debug) "debug" else "release"})"
}
