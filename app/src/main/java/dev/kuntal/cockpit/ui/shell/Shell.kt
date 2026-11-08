package dev.kuntal.cockpit.ui.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.kuntal.cockpit.DebugHooks
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Link
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Dest(val label: String, val icon: Ic, val dock: Boolean = true) {
    Home("Home", Ic.Home), Drive("Drive", Ic.Gauge), Car("Car", Ic.Car), Media("Media", Ic.Music),
    Phone("Phone", Ic.Phone), Maps("Maps", Ic.Nav), Apps("Apps", Ic.Grid), Settings("Settings", Ic.Sliders),
    // screens without a dock slot of their own (reached from Home, Drive, Car, Media and Settings)
    Trips("Trips", Ic.Route, dock = false), Parking("Parking", Ic.Park, dock = false), Diagnostics("Diagnose", Ic.Pulse, dock = false),
    Sound("Sound", Ic.Eq, dock = false), PhoneDemo("Phone", Ic.Phone, dock = false), RadioDemo("Radio", Ic.Radio, dock = false),
    Projection("Projection", Ic.Cast, dock = false),
    // full-screen states with no status bar or dock
    Boot("Boot", Ic.Power, dock = false), Standby("Standby", Ic.Moon, dock = false),
}

/** Which dock slot lights up for a screen (the design: Trips and Diagnose live under Apps, Sound and Radio under Media). */
fun Dest.dockSlot(): Dest? = when (this) {
    Dest.Trips, Dest.Diagnostics -> Dest.Apps
    Dest.Sound, Dest.RadioDemo -> Dest.Media
    Dest.PhoneDemo -> Dest.Phone
    Dest.Projection -> Dest.Maps
    else -> if (dock) this else null
}

private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** 1280 x 56 status bar. Positions are the measured design coordinates. */
@Composable
fun StatusBar(state: State<VehicleUiState>, onTap: () -> Unit = {}) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    // The face shows hours and minutes only, so `now` is truncated to the minute: an equal value writes nothing, invalidates nothing
    // and an idle screen draws no frame between two minute changes (it used to draw one every second).
    var now by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    var volume by remember { mutableStateOf(0) }
    var clockEpoch by remember { mutableStateOf(0) }              // bumped when the system clock or zone is set: re-aim the minute timer
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { clockEpoch++ } }
        ctx.registerReceiver(r, IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) })
        onDispose { ctx.unregisterReceiver(r) }
    }
    LaunchedEffect(clockEpoch) {
        while (true) {
            val t = LocalDateTime.now()
            now = t.truncatedTo(ChronoUnit.MINUTES)
            delay(60_000L - (t.second * 1_000L + t.nano / 1_000_000L) + 25)      // wake just after the next minute boundary
        }
    }
    LaunchedEffect(Unit) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (true) { volume = am.getStreamVolume(AudioManager.STREAM_MUSIC); delay(1_000) }      // an unchanged value costs no frame
    }
    val snap = DebugHooks.snapshot
    val st = LocalSettings.current
    Box(Modifier.at(0, 0).width(1280.dp).height(56.dp).background(Brush.verticalGradient(listOf(p.bg.copy(alpha = .94f), p.bg.copy(alpha = .5f)))).clickable(remember { MutableInteractionSource() }, null, onClick = onTap)) {
        Box(Modifier.at(0, 55).width(1280.dp).height(1.dp).background(p.hairSoft))
        Txt(if (snap) DebugHooks.CLOCK else Fmt.clock(now.hour, now.minute, st.clock), 36, 36, p.text, Modifier.baselineAt(28, 42), CockpitFonts.Condensed, FontWeight.SemiBold, letterSpacing = 0.03.em)
        Txt(
            if (snap) DebugHooks.DATE else DATE_FORMAT.format(now),
            19, 23, p.dim, Modifier.at(118, 16), letterSpacing = 0.02.em,
        )
        // connection indicators, centred on the bar: dot + label. Only OBD has a live source so far; the rest read "off" instead of pretending.
        val link by remember { derivedStateOf { state.value.link } }       // recompose on a link change, not on every sample
        val obdOn = link == Link.Connected || link == Link.Simulated
        Row(Modifier.at(0, 17).width(1280.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)) {
            LinkItem("OBD", if (link == Link.Simulated && !snap) "sim" else null, on = obdOn, warn = link == Link.Lost)
            LinkItem("Canbox", if (st.demoData && !snap) "sim" else null, on = snap || st.demoData)
            LinkItem("BT", null, on = snap)
            LinkItem("Android Auto", null, on = snap)
        }
        // readouts, right-aligned 28 px from the edge
        Row(Modifier.at(0, 16).width(1252.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.End)) {
            StatusReadout(Ic.Sun) { state.value.sample?.ambientC?.let { Fmt.temp(it, st.tempUnit) + "\u00B0" } ?: Fmt.DASH }
            StatusReadout(Ic.Batt) { state.value.sample?.batteryV?.let { Fmt.dec(it, 1) + " V" } ?: Fmt.DASH }
            StatusReadout(Ic.Vol) { (if (snap) DebugHooks.VOLUME else volume).toString() }
        }
    }
}

@Composable
private fun LinkItem(label: String, tag: String?, on: Boolean, warn: Boolean = false) {
    val p = LocalPalette.current
    val dot = when { warn -> p.warn; on -> p.cyan; else -> p.dim.copy(alpha = .45f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(12.dp)) { drawCircle(dot) }
        Txt(label, 18, 22, if (on) p.text else p.dim, weight = FontWeight.SemiBold)
        if (tag != null) Txt(tag, 18, 22, p.dim, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusReadout(ic: Ic, read: () -> String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(ic, 22, p.sky)
        LiveTxt(read, 20, 24, p.text, weight = FontWeight.SemiBold)
    }
}

/** 112 px right-edge dock (right-hand-drive market). Tile and spacing are the measured design values. */
@Composable
fun Dock(current: Dest, onSelect: (Dest) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.at(1168, 56).width(112.dp).height(744.dp).background(p.s1.copy(alpha = .72f))) {
        Box(Modifier.width(1.dp).fillMaxSize().background(p.hairSoft))
        Dest.entries.filter { it.dock }.forEachIndexed { i, d ->
            val top = 8 + 91 * i
            val on = d == current.dockSlot()
            Box(
                Modifier.at(11, top + 3).size(91.dp, 85.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(if (on) Modifier.background(p.accent) else Modifier)
                    .tap { onSelect(d) },
            ) {
                Icon(d.icon, 32, if (on) p.onAccent else p.sky, Modifier.at(29, 12))
                Txt(
                    d.label, 18, 22, if (on) p.onAccent else p.dim,
                    Modifier.width(91.dp).at(0, 50), weight = FontWeight.SemiBold, align = TextAlign.Center,
                )
            }
        }
    }
}
