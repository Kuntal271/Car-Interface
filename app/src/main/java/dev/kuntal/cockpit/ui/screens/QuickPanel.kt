package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.data.SourceKind
import dev.kuntal.cockpit.ui.components.BaselineRow
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.IconLabelAt
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.shell.Brightness
import dev.kuntal.cockpit.ui.shell.QuickControls
import dev.kuntal.cockpit.ui.shell.SystemVolume
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlinx.coroutines.delay

/**
 * Quick panel (design: quick.html): opened by tapping the status bar, over a dimmed copy of the current screen. Brightness is this
 * app's window brightness, volume is the music stream. Wi-Fi, Bluetooth and Hotspot show the real state and open the system page
 * (a launcher cannot switch them); Night dims the whole UI; Screen off starts Standby; Simulator flips the data source.
 */
@Composable
fun QuickPanel(
    brightness: Brightness, volume: SystemVolume, controls: QuickControls, night: Boolean,
    onNight: () -> Unit, onSimulator: () -> Unit, onStandby: () -> Unit, onSettings: () -> Unit, onDashcam: () -> Unit, onPower: () -> Unit,
    onNotice: (String) -> Unit, onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val cond = CockpitFonts.Condensed
    var wifi by remember { mutableStateOf(controls.wifiOn()) }
    var bt by remember { mutableStateOf(controls.btOn()) }
    LaunchedEffect(Unit) { while (true) { delay(2_000); wifi = controls.wifiOn(); bt = controls.btOn(); volume.refresh() } }

    Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = .8f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)) {
        Box(Modifier.at(24, 72).size(1120.dp, 316.dp).card(s2 = true).clickable(remember { MutableInteractionSource() }, null) {})
        IconLabelAt(48, 98, Ic.Sun, "Brightness")
        BaselineRow(518, 126, gap = 2, alignEnd = true) { LiveTxt({ brightness.pct.toString() }, 40, 39, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true); Txt("%", 23, 28, p.dim, family = cond) }
        SegSlider(48, 148, 470, 22, 22, frac = { brightness.pct / 100f }, knob = true) { brightness.set(Math.round(it * 100)) }
        IconLabelAt(48, 204, Ic.Vol, "Volume")
        BaselineRow(518, 232, gap = 2, alignEnd = true) { LiveTxt({ volume.level.value.toString() }, 40, 39, p.cyan, family = cond, weight = FontWeight.SemiBold, tnum = true); Txt(" / ${volume.max}", 23, 28, p.dim, family = cond) }
        SegSlider(48, 254, 470, 22, 22, frac = { volume.level.value / volume.max.toFloat() }, knob = true) { volume.set(Math.round(it * volume.max)) }
        PillBtn(48, 304, 250, "All settings", onSettings, h = 64, ic = Ic.Sliders)
        PillBtn(310, 304, 208, "Power menu", onPower, h = 64, ic = Ic.Power)

        val sim = st.source == SourceKind.Simulator
        val tiles = listOf(
            Triple("Wi-Fi", Ic.Wifi, wifi) to { if (!controls.openWifi()) onNotice("Wi-Fi settings are not available") },
            Triple("Bluetooth", Ic.Bt, bt) to { if (!controls.openBluetooth()) onNotice("Bluetooth settings are not available") },
            Triple("Hotspot", Ic.Signal, false) to { if (!controls.openHotspot()) onNotice("Hotspot settings are not available") },
            Triple("Mute", Ic.Mute, volume.muted) to { volume.toggleMute(restore = volume.max / 2) },
            Triple("Night", Ic.Moon, night) to onNight,
            Triple("Screen off", Ic.Power, false) to onStandby,
            Triple("Dash cam", Ic.Camera, false) to onDashcam,
            Triple("Simulator", Ic.Gauge, sim) to onSimulator,
        )
        tiles.forEachIndexed { i, (t, act) ->
            val (label, ic, on) = t
            val x = 542f + 147.5f * (i % 4); val y = 92 + 144 * (i / 4)
            Box(
                Modifier.at(x.toInt(), y).size(135.5.dp, 132.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (on) p.accent else p.sky.copy(alpha = .12f)).tap(act),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(ic, 34, if (on) p.onAccent else p.text)
                    Txt(label, 19, 23, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}
