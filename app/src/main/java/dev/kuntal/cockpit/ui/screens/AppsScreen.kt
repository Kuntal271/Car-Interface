package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.AppEntry
import dev.kuntal.cockpit.data.AppIndex
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.components.tile
import dev.kuntal.cockpit.ui.shell.InstalledApps
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import kotlinx.coroutines.withContext

/**
 * Apps: search + the installed apps as a 6-column grid (tiles 177 x 148, 12 px gaps), recent apps flagged with a cyan diamond.
 * The grid scrolls; geometry follows the design (content area 1120 x 628 below a 56 px header row).
 */
@Composable
fun AppsScreen(apps: List<AppEntry>, recents: List<String>, onLaunch: (AppEntry) -> Unit) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(apps) { AppIndex.Search(apps) }                      // sorted + keyed once per list, not per keystroke
    val shown = remember(index, query) { index.filter(query) }

    Box(Modifier.fillMaxSize()) {
        // search field: 520 x 56 pill
        Row(
            Modifier.at(24, 80).size(520.dp, 56.dp).pill(fill = p.s1),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(6.dp))
            Icon(Ic.Search, 26, p.dim)
            Box(Modifier.weightFill()) {
                if (query.isEmpty()) Txt("Search apps", 22, 28, p.dim)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 22.sp, fontFamily = CockpitFonts.Barlow, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(p.cyan), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(56.dp).tap { query = "" }, contentAlignment = Alignment.Center) { Icon(Ic.Close, 24, p.dim) }
        }
        // legend + count, right aligned to the 1144 edge
        Row(Modifier.at(744, 80).size(400.dp, 56.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Diamond(Modifier.size(10.dp))
                Txt("Recent", 19, 21, p.dim)
            }
            Txt(if (query.isEmpty()) "${apps.size} apps" else "${shown.size} of ${apps.size}", 19, 21, p.dim)
        }

        if (shown.isEmpty()) {
            Txt("No apps match \u201C$query\u201D", 24, 30, p.dim, Modifier.at(24, 400).width(1120.dp), align = TextAlign.Center)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6), modifier = Modifier.at(24, 148).size(1120.dp, 628.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.pkg + "/" + it.activity }) { a -> AppTile(a, recent = a.pkg in recents) { onLaunch(a) } }
            }
        }
    }
}

private fun Modifier.weightFill(): Modifier = this.width(340.dp)

@Composable
private fun AppTile(app: AppEntry, recent: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val icon by produceState(InstalledApps.cachedIcon(app.pkg), app.pkg) { if (value == null) value = withContext(InstalledApps.iconDispatcher) { InstalledApps.icon(ctx, app.pkg) } }
    Box(Modifier.fillMaxWidth().height(148.dp).tile().tap(onClick)) {
        icon?.let { Image(it, null, Modifier.at(60, 28).size(56.dp)) }
        Txt(app.label, 19, 23, p.text, Modifier.fillMaxWidth().at(0, 98), weight = FontWeight.SemiBold, align = TextAlign.Center)
        if (recent) Diamond(Modifier.at(14, 14).size(10.dp))
    }
}

/** Cyan "recent" marker: a 10 px square turned 45 degrees. */
@Composable
private fun Diamond(modifier: Modifier) {
    val c = LocalPalette.current.cyan
    Canvas(modifier) { rotate(45f) { drawRect(c, size = size) } }
}
