package dev.kuntal.cockpit.ui.shell

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.CanboxState
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.Overlay
import dev.kuntal.cockpit.data.OverlayQueue
import dev.kuntal.cockpit.data.OverlayRules
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged

/** Anything can post an overlay (a failed launch, a demo phone call, ...); the host decides what is visible by priority. */
object OverlayBus {
    val events = MutableSharedFlow<Overlay>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun post(o: Overlay) { events.tryEmit(o) }
}

/**
 * Decides which overlay is visible: the pure rules in [OverlayRules] watched on the live data, then [OverlayQueue] for priority and
 * expiry. Mock Canbox values never raise overlays (a simulated open door must not interrupt a real drive).
 */
@Composable
fun rememberOverlay(
    state: State<VehicleUiState>, canbox: State<CanboxState?>, demo: Boolean, volume: SystemVolume,
    screen: Dest, quickOpen: Boolean, forced: Overlay?, settings: Settings,
): OverlayCtl {
    val queue = remember { OverlayQueue() }
    val coolantDismissed = remember { booleanArrayOf(false) }
    val current = remember { mutableStateOf(forced) }
    val kick = remember { MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }   // not Compose state: bumping it must not recompose the app
    val screenNow by rememberUpdatedStateOf(screen)
    val quickNow by rememberUpdatedStateOf(quickOpen)
    fun now() = SystemClock.elapsedRealtime()
    fun refresh() { current.value = forced ?: queue.visible(now()) }
    fun show(o: Overlay) { queue.show(o, now()); kick.tryEmit(Unit); refresh() }
    fun clear(kind: String) { queue.clear(kind); kick.tryEmit(Unit); refresh() }

    fun dismiss() {
        if (current.value is Overlay.CoolantHigh) coolantDismissed[0] = true
        queue.dismiss(now()); kick.tryEmit(Unit); refresh()
    }
    LaunchedEffect(Unit) { OverlayBus.events.collect { show(it) } }
    LaunchedEffect(Unit) { kick.collectLatest { queue.nextExpiry()?.let { kotlinx.coroutines.delay((it - now()).coerceAtLeast(0) + 20); refresh() } } }

    LaunchedEffect(Unit) {
        snapshotFlow { volume.level.value }.drop(1).collect { v ->
            if (screenNow != Dest.Sound && screenNow != Dest.Media && !quickNow) show(Overlay.Volume(v, volume.max, volume.muted))
        }
    }
    LaunchedEffect(demo) {
        if (demo) { clear("door"); clear("climate"); return@LaunchedEffect }
        snapshotFlow { canbox.value?.climate?.setC }.drop(1).filterNotNull().collect { if (screenNow != Dest.Car) show(Overlay.ClimateChange(it)) }
    }
    LaunchedEffect(demo) {
        if (demo) return@LaunchedEffect
        snapshotFlow { OverlayRules.doorMoving(canbox.value?.doorsOpen ?: emptySet(), state.value.sample?.speedKmh) }.distinctUntilChanged().collect { if (it != null) show(it) else clear("door") }
    }
    LaunchedEffect(Unit) {
        var active = false
        snapshotFlow { state.value.sample?.coolantC }.collect { c ->
            val on = OverlayRules.coolantHigh(c, active)
            if (!on) coolantDismissed[0] = false
            if (on && !active && !coolantDismissed[0]) show(Overlay.CoolantHigh(Math.round(c ?: 0.0).toInt())) else if (!on) clear("coolant")
            active = on
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { state.value.sample?.let { OverlayRules.lowBattery(it.batteryV, it.rpm) to it.batteryV } }.distinctUntilChanged().collect { r ->
            if (r?.first == true) show(Overlay.LowBattery(r.second ?: 0.0)) else clear("battery")
        }
    }
    LaunchedEffect(Unit) {
        var prev: Double? = null
        snapshotFlow { state.value.sample?.fuelPct }.collect { f ->
            if (OverlayRules.lowFuel(prev, f) && f != null) show(Overlay.LowFuel(Math.round(f).toInt(), null))
            if (f != null) prev = f
        }
    }
    return OverlayCtl(current) { dismiss() }
}

class OverlayCtl(val current: State<Overlay?>, val dismiss: () -> Unit)

@Composable
private fun <T> rememberUpdatedStateOf(v: T): State<T> = androidx.compose.runtime.rememberUpdatedState(v)

/** Draws the visible overlay over everything else. Banners and OSDs let touches through; modal ones and the call card take them. */
@Composable
fun OverlayLayer(shown: State<Overlay?>, demo: Boolean, onDismiss: () -> Unit, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val o = shown.value ?: return
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    @Composable fun Dimmer(a: Float = .66f, tap: Boolean = true) {
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = a)).then(if (tap) Modifier.tap(onDismiss) else Modifier))
    }
    when (o) {
        is Overlay.Volume -> {
            Box(Modifier.at(300, 640).size(560.dp, 92.dp).card(s2 = true))
            Icon(if (o.muted) Ic.Mute else Ic.Vol, 34, p.cyan, Modifier.at(322, 666))
            Txt("Volume", 19, 21, p.dim, Modifier.at(372, 675))
            SegSlider(451, 675, 273, 22, 30, frac = { o.level / o.max.toFloat() }, knob = true)
            dev.kuntal.cockpit.ui.components.BaselineRow(740, 710, gap = 4) {
                Txt(o.level.toString(), 56, 50, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true)
                Txt("/ ${o.max}", 23, 28, p.dim, family = cond)
            }
        }
        is Overlay.ClimateChange -> {
            Box(Modifier.at(340, 84).size(520.dp, 103.dp).card(s2 = true))
            Icon(Ic.Fan, 36, p.cyan, Modifier.at(362, 115))
            Txt("Climate", 19, 21, p.dim, Modifier.at(410, 125))
            Txt(Fmt.dec(o.setC, 1), 72, 65, p.text, Modifier.at(568, 102), cond, FontWeight.SemiBold, tnum = true)
            Txt("\u00B0C", 28, 34, p.dim, Modifier.at(692, 135), cond)
            Icon(Ic.Chevu, 30, p.cyan, Modifier.at(808, 118))
        }
        is Overlay.DoorMoving -> Banner(68, 68, Ic.Warn) {
            Txt(o.door + " open", 30, 36, p.onWarn, family = cond, weight = FontWeight.Bold)
            Txt(" \u00B7 ${o.kmh} km/h" + if (demo) " \u00B7 demo" else "", 20, 24, p.onWarn.copy(alpha = .8f), weight = FontWeight.SemiBold)
        }
        is Overlay.LowBattery -> Banner(708, 68, Ic.Batt) {
            Txt("Battery ${Fmt.dec(o.volts, 1)} V", 30, 36, p.onWarn, family = cond, weight = FontWeight.Bold)
            Txt(" \u00B7 unit will sleep to protect the battery", 20, 24, p.onWarn.copy(alpha = .8f), weight = FontWeight.SemiBold)
        }
        is Overlay.LowFuel -> Banner(708, 68, Ic.Fuel) {
            Txt("Low fuel ${o.pct} %", 30, 36, p.onWarn, family = cond, weight = FontWeight.Bold)
            Txt(if (o.rangeKm != null) " \u00B7 about ${o.rangeKm} km left" else " \u00B7 find a station soon", 20, 24, p.onWarn.copy(alpha = .8f), weight = FontWeight.SemiBold)
        }
        is Overlay.ShiftUp -> Box(Modifier.at(24, 68).size(1120.dp, 84.dp).background(p.accent, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Txt("Shift up \u00B7 gear ${o.gear}", 56, 67, p.onAccent, family = cond, weight = FontWeight.SemiBold)
        }
        is Overlay.CoolantHigh -> {
            Dimmer()
            Box(Modifier.at(295, 230).size(690.dp, 340.dp).background(p.warn, RoundedCornerShape(26.dp)))
            Icon(Ic.Thermo, 56, p.onWarn, Modifier.at(612, 262))
            Txt("Coolant high", 54, 54, p.onWarn, Modifier.at(0, 331).width(1280.dp), cond, FontWeight.SemiBold, TextAlign.Center)
            Txt(o.tempC.toString(), 130, 117, p.onWarn, Modifier.at(0, 391).width(1280.dp), cond, FontWeight.SemiBold, TextAlign.Center, tnum = true)
            Txt("\u00B0C", 44, 40, p.onWarn, Modifier.at(714, 458), cond, FontWeight.SemiBold)
            Txt("Reduce load. Stop safely when possible.", 24, 29, p.onWarn, Modifier.at(0, 514).width(1280.dp), weight = FontWeight.SemiBold, align = TextAlign.Center)
        }
        is Overlay.IncomingCall -> {
            Dimmer(tap = false)
            Box(Modifier.at(652, 80).size(492.dp, 212.dp).card())
            Box(Modifier.at(676, 104).size(84.dp, 84.dp).background(p.s2, CircleShape), contentAlignment = Alignment.Center) { Txt(o.initials, 44, 53, p.cyan, family = cond, weight = FontWeight.ExtraBold) }
            Txt("Incoming call", 19, 21, p.dim, Modifier.at(776, 118))
            Txt(o.name, 34, 34, p.text, Modifier.at(776, 139), cond, FontWeight.SemiBold)
            PillBtn(676, 204, 216, "Answer", onAnswer, h = 64, primary = true)
            PillBtn(904, 204, 216, "Decline", onDecline, h = 64)
        }
        is Overlay.Reverse -> {
            Dimmer(tap = false)
            Box(Modifier.at(360, 345).size(560.dp, 96.dp).card(s2 = true))
            Box(Modifier.at(382, 363).size(65.dp, 60.dp).background(p.accent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Txt("R", 40, 48, p.onAccent, weight = FontWeight.SemiBold) }
            Txt("Reverse", 34, 34, p.text, Modifier.at(463, 365), cond, FontWeight.SemiBold)
            Txt("Opening rear camera", 19, 21, p.dim, Modifier.at(463, 399), letterSpacing = 0.76.sp)
            Icon(Ic.Camera, 40, p.cyan, Modifier.at(856, 373))
        }
        is Overlay.Notice -> Row(Modifier.at(404, 700).size(472.dp, 56.dp).background(p.s2, RoundedCornerShape(50)), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt(o.text, 20, 24, p.text, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Banner(y: Int, h: Int, ic: Ic, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.at(24, y).size(1120.dp, h.dp).background(p.warn, RoundedCornerShape(24.dp)))
    Icon(ic, 36, p.onWarn, Modifier.at(46, y + 16))
    dev.kuntal.cockpit.ui.components.BaselineRow(102, y + 48) { content() }
}
