package dev.kuntal.cockpit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.screens.DriveScreen
import dev.kuntal.cockpit.ui.shell.Dest
import dev.kuntal.cockpit.ui.shell.Dock
import dev.kuntal.cockpit.ui.shell.StatusBar
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.DesignCanvas
import dev.kuntal.cockpit.ui.theme.LocalPalette
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width

@Composable
fun CockpitApp(startOn: Dest = Dest.Drive, vm: CockpitViewModel = viewModel()) {
    val state = vm.state.collectAsStateWithLifecycle()      // pass the State itself: screens read .value in leaf/draw scopes
    var screen by rememberSaveable { mutableStateOf(startOn) }
    DesignCanvas {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Dest.Drive -> DriveScreen(state)
                else -> NotYet(screen)
            }
            StatusBar(state)
            Dock(screen) { screen = it }
        }
    }
}

@Composable
private fun NotYet(d: Dest) {
    val p = LocalPalette.current
    Txt(d.label, 96, 110, p.text, Modifier.at(24, 330).width(1120.dp), CockpitFonts.Condensed, FontWeight.SemiBold, TextAlign.Center)
    Txt("Not built yet", 24, 30, p.dim, Modifier.at(24, 450).width(1120.dp), align = TextAlign.Center)
}
