package dev.kuntal.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.kuntal.cockpit.data.HomeContent
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.screens.DriveScreen
import dev.kuntal.cockpit.ui.screens.HomeAction
import dev.kuntal.cockpit.ui.screens.HomeScreen
import dev.kuntal.cockpit.ui.shell.AppLauncher
import dev.kuntal.cockpit.ui.shell.Dest
import dev.kuntal.cockpit.ui.shell.Dock
import dev.kuntal.cockpit.ui.shell.StatusBar
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.DesignCanvas
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun CockpitApp(startOn: Dest = Dest.Home, vm: CockpitViewModel = viewModel()) {
    val state = vm.state.collectAsStateWithLifecycle()      // pass the State itself: screens read .value in leaf/draw scopes
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(startOn) }
    var notice by remember { mutableStateOf<String?>(null) }
    val content = remember { if (DebugHooks.snapshot) HomeContent.DEMO else HomeContent.EMPTY }

    fun launch(what: String, nice: String) { if (!AppLauncher.launch(ctx, what)) notice = "No $nice app found on this unit" }
    fun go(d: Dest) = when (d) {
        Dest.Media -> launch("media", "media")
        Dest.Phone -> launch("phone", "phone")
        Dest.Maps -> launch("maps", "navigation")
        else -> screen = d
    }

    DesignCanvas {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Dest.Home -> HomeScreen(state, content) { a ->
                    when (a) {
                        is HomeAction.Go -> go(a.dest)
                        is HomeAction.Launch -> launch(a.what, a.what)
                    }
                }
                Dest.Drive -> DriveScreen(state)
                else -> NotYet(screen)
            }
            StatusBar(state)
            Dock(screen) { go(it) }
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

@Composable
private fun NotYet(d: Dest) {
    val p = LocalPalette.current
    Txt(d.label, 96, 110, p.text, Modifier.at(24, 330).width(1120.dp), CockpitFonts.Condensed, FontWeight.SemiBold, TextAlign.Center)
    Txt("Not built yet", 24, 30, p.dim, Modifier.at(24, 450).width(1120.dp), align = TextAlign.Center)
}
