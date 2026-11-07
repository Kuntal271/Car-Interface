package dev.kuntal.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kuntal.cockpit.data.AppEntry
import dev.kuntal.cockpit.data.HomeContent
import dev.kuntal.cockpit.perf.Perf
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.SnapshotFixtures
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.kuntal.cockpit.data.SourceKind
import dev.kuntal.cockpit.data.Standby
import dev.kuntal.cockpit.ui.screens.BootScreen
import dev.kuntal.cockpit.ui.screens.QuickPanel
import dev.kuntal.cockpit.ui.screens.StandbyScreen
import dev.kuntal.cockpit.ui.shell.OverlayLayer
import dev.kuntal.cockpit.ui.shell.rememberBrightness
import dev.kuntal.cockpit.ui.shell.rememberOverlay
import dev.kuntal.cockpit.ui.shell.rememberQuickControls
import dev.kuntal.cockpit.ui.screens.AppsScreen
import dev.kuntal.cockpit.ui.screens.CarScreen
import dev.kuntal.cockpit.ui.screens.MediaScreen
import dev.kuntal.cockpit.ui.screens.DiagnosticsScreen
import dev.kuntal.cockpit.ui.screens.ParkingScreen
import dev.kuntal.cockpit.ui.screens.SoundScreen
import dev.kuntal.cockpit.ui.screens.TripsScreen
import dev.kuntal.cockpit.ui.shell.TripExport
import dev.kuntal.cockpit.ui.shell.rememberSystemVolume
import dev.kuntal.cockpit.ui.screens.DriveScreen
import dev.kuntal.cockpit.ui.screens.HomeAction
import dev.kuntal.cockpit.ui.screens.HomeScreen
import dev.kuntal.cockpit.ui.screens.SettingsPage
import dev.kuntal.cockpit.ui.screens.SettingsScreen
import dev.kuntal.cockpit.ui.shell.AppLauncher
import dev.kuntal.cockpit.ui.shell.Dest
import dev.kuntal.cockpit.ui.shell.Dock
import dev.kuntal.cockpit.ui.shell.InstalledApps
import dev.kuntal.cockpit.ui.shell.StatusBar
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.DesignCanvas
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings
import dev.kuntal.cockpit.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CockpitApp(startOn: Dest = Dest.Home, startPage: SettingsPage = SettingsPage.Display, boot: Boolean = false, vm: CockpitViewModel = viewModel()) {
    val state = vm.state.collectAsStateWithLifecycle()      // pass the State itself: screens read .value in leaf/draw scopes
    val canbox = vm.canbox.collectAsStateWithLifecycle()
    val radar = vm.radar.collectAsStateWithLifecycle()
    val reverse = remember { derivedStateOf { canbox.value?.reverse == true } }
    val data by vm.data.collectAsStateWithLifecycle()
    val volume = rememberSystemVolume()
    val nowMs = remember { if (DebugHooks.snapshot) SnapshotFixtures.NOW_MS else System.currentTimeMillis() }
    val settings by vm.settings.flow.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var screen by rememberSaveable {
        mutableStateOf(when {
            DebugHooks.snapshot && DebugHooks.screen.startsWith("overlay") -> Dest.Drive        // overlays are shot over the Drive screen
            boot && settings.bootSplash && !DebugHooks.snapshot -> Dest.Boot
            else -> startOn
        })
    }
    var returnTo by rememberSaveable { mutableStateOf(if (startOn == Dest.Boot || startOn == Dest.Standby) Dest.Home else startOn) }
    var quick by remember { mutableStateOf(DebugHooks.snapshot && DebugHooks.screen == "quick") }
    var night by remember { mutableStateOf(false) }
    val touch = remember { longArrayOf(SystemClock.elapsedRealtime()) }
    val brightness = rememberBrightness()
    val controls = rememberQuickControls()
    val forcedOverlay = remember { if (DebugHooks.snapshot) SnapshotFixtures.overlayFor(DebugHooks.screen) else null }
    val overlay = rememberOverlay(state, canbox, data.demo, volume, screen, quick, forcedOverlay, settings)
    var notice by remember { mutableStateOf<String?>(null) }
    val content = remember { if (DebugHooks.snapshot) HomeContent.DEMO else HomeContent.EMPTY }

    fun launch(what: String, nice: String) { if (!AppLauncher.launch(ctx, what)) notice = "No $nice app found on this unit" }
    fun sleep() { returnTo = screen; screen = Dest.Standby; quick = false }
    LaunchedEffect(settings.standbyMin) {
        while (true) {
            delay(10_000)
            if (screen != Dest.Standby && screen != Dest.Boot && Standby.shouldSleep(SystemClock.elapsedRealtime() - touch[0], settings.standbyMin, state.value.sample?.speedKmh)) sleep()
        }
    }
    fun go(d: Dest) = when (d) {
        Dest.Phone -> launch("phone", "phone")
        Dest.Maps -> launch("maps", "navigation")
        else -> screen = d
    }

    DesignCanvas(paletteFor(settings.theme)) {
        CompositionLocalProvider(LocalSettings provides settings) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); touch[0] = SystemClock.elapsedRealtime() } }
            }) {
                when (screen) {
                    Dest.Home -> HomeScreen(state, content) { a ->
                        when (a) {
                            is HomeAction.Go -> go(a.dest)
                            is HomeAction.Launch -> launch(a.what, a.what)
                        }
                    }
                    Dest.Drive -> DriveScreen(state)
                    Dest.Car -> CarScreen(canbox) { screen = Dest.Parking }
                    Dest.Parking -> ParkingScreen(radar, reverse, onExit = { screen = Dest.Car }, onOpenCamera = { launch("camera", "camera") })
                    Dest.Diagnostics -> DiagnosticsScreen(state, data.diag)
                    Dest.Sound -> SoundScreen(data.audio, volume) { launch("eq", "equalizer") }
                    Dest.Trips -> TripsScreen(state, data, nowMs, java.time.ZoneId.systemDefault(), { trips -> TripExport.save(ctx, trips) }) { notice = it }
                    Dest.Media -> {
                        val eq = data.audio?.state?.collectAsStateWithLifecycle()
                        MediaScreen(data.media, volume, eq?.value?.preset, onSound = { screen = Dest.Sound }, onRadio = { launch("radio", "radio") }, onOpenPlayer = { launch("media", "media") })
                    }
                    Dest.Apps -> {
                        val apps by produceState(emptyList<AppEntry>()) { value = withContext(Dispatchers.IO) { InstalledApps.query(ctx) } }
                        var recents by remember { mutableStateOf(InstalledApps.recents(ctx)) }
                        AppsScreen(apps, recents) { a ->
                            if (InstalledApps.launch(ctx, a)) { InstalledApps.remember(ctx, a.pkg); recents = InstalledApps.recents(ctx) }
                            else notice = "Could not open ${a.label}"
                        }
                    }
                    Dest.Settings -> {
                        val link by remember { derivedStateOf { state.value.link } }        // only a link change recomposes the page
                        SettingsScreen(settings, link, { change -> vm.settings.update(change) }, startPage)
                    }
                    Dest.Boot -> BootScreen { screen = returnTo.takeIf { it != Dest.Boot } ?: startOn.takeIf { it != Dest.Boot } ?: Dest.Home }
                    Dest.Standby -> StandbyScreen(state, canbox, remember(data) { data.trips.all().firstOrNull()?.distanceKm }) { touch[0] = SystemClock.elapsedRealtime(); screen = returnTo }
                    else -> NotYet(screen)
                }
                val chrome = screen != Dest.Boot && screen != Dest.Standby
                if (chrome) StatusBar(state) { quick = !quick }
                if (chrome && screen != Dest.Parking) Dock(screen) { quick = false; go(it) }      // the parking view uses the whole width (design: no dock)
                if (quick && chrome) QuickPanel(
                    brightness, volume, controls, night,
                    onNight = { night = !night }, onStandby = { sleep() }, onSettings = { quick = false; screen = Dest.Settings },
                    onDashcam = { quick = false; launch("dashcam", "dash cam") },
                    onSimulator = {
                        val real = when { settings.tcpHost.isNotBlank() -> SourceKind.ObdTcp; settings.btName.isNotBlank() -> SourceKind.ObdBluetooth; else -> null }
                        if (settings.source == SourceKind.Simulator) { if (real == null) notice = "Choose an OBD adapter in Settings first" else vm.settings.update { it.copy(source = real) } }
                        else vm.settings.update { it.copy(source = SourceKind.Simulator) }
                    },
                    onPower = { notice = "The power menu belongs to the unit, not the launcher" },
                    onNotice = { notice = it }, onClose = { quick = false },
                )
                OverlayLayer(overlay.current, data.demo, onDismiss = overlay.dismiss, onAnswer = overlay.dismiss, onDecline = overlay.dismiss)
                if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)))
                if (DebugHooks.perf || settings.perfOverlay) PerfOverlay()
                notice?.let { n ->
                    LaunchedEffect(n) { delay(2_500); notice = null }
                    Row(
                        Modifier.at(404, 700).size(360.dp, 56.dp).pill(fill = LocalPalette.current.s2, border = LocalPalette.current.hair),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) { Txt(n, 20, 24, LocalPalette.current.text, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun NotYet(d: Dest) {
    val p = LocalPalette.current
    Txt(d.label, 96, 110, p.text, Modifier.at(24, 330).width(1120.dp), CockpitFonts.Condensed, FontWeight.SemiBold, TextAlign.Center)
    Txt("Not built yet", 24, 30, p.dim, Modifier.at(24, 450).width(1120.dp), align = TextAlign.Center)
}

/** Debug overlay (launch with --ez perf true): the live frame statistics and the gate verdict. */
@Composable
private fun PerfOverlay() {
    val p = LocalPalette.current
    val sum by Perf.summary.collectAsStateWithLifecycle()
    val mode by Perf.mode.collectAsStateWithLifecycle()
    val ok = sum.meetsGate()
    val idle = sum.count == 0                                  // nothing measured yet is not a failure
    // In the 24 px margin under the cards: covers nothing.
    Row(
        Modifier.at(24, 777).size(1120.dp, 22.dp).background(p.bg),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            "p50 %.1f   p99 %.1f   max %.1f ms   >12 ms %.1f%%   missed %.1f%%   %s   %s".format(sum.p50, sum.p99, sum.max, sum.over12Pct, sum.missedPct, if (idle) "WAITING" else if (ok) "PASS" else "FAIL", mode),
            18, 22, if (idle) p.dim else if (ok) p.cyan else p.warn, weight = FontWeight.SemiBold,
        )
    }
}
