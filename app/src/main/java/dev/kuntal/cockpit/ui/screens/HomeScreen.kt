package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.BoostBar
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.HomeContent
import dev.kuntal.cockpit.data.Range
import dev.kuntal.cockpit.data.Shortcut
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.BaselineRow
import dev.kuntal.cockpit.ui.components.FordMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.SegBar
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.roundBtn
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.components.tile
import dev.kuntal.cockpit.ui.shell.Dest
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/** Where a Home tap goes. Dock destinations stay in-app; the rest launch stock apps (wired in CockpitApp). */
sealed interface HomeAction {
    data class Go(val dest: Dest) : HomeAction
    data class Launch(val what: String) : HomeAction
}

/**
 * Home: media card, three shortcut tiles, recents, and the live vitals column. Layout is the measured design (1280 x 800 grid).
 * Numerals are placed by baseline (design-measured), everything reads telemetry in leaf lambdas.
 */
@Composable
fun HomeScreen(s: State<VehicleUiState>, content: HomeContent, onAction: (HomeAction) -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    Box(Modifier.fillMaxSize()) {
        // ---- header: oval + model, trip line (right aligned) ----
        FordMark(38, Modifier.at(24, 81))
        Txt("EcoSport", 34, 34, p.text, Modifier.baselineAt(146, 113), cond, FontWeight.SemiBold)
        BaselineRow(x = 704, baseline = 107, gap = 4, alignEnd = true) {
            LiveTxt({ (if ((s.value.sample?.rpm ?: 0.0) > 400.0) "Engine on" else "Engine off") + " \u00B7 Trip A" }, 19, 21, p.dim)
            LiveTxt({ Fmt.dec(s.value.trip.distanceKm, 1) + " km" }, 19, 21, p.text, weight = FontWeight.SemiBold)
        }

        // ---- media card ----
        Box(Modifier.at(24, 132).size(680.dp, 310.dp).card())
        AlbumArt(Modifier.at(44, 152).size(270.dp))
        val m = content.media
        Row(Modifier.at(338, 152).size(187.dp, 40.dp).pill(fill = p.blue.copy(alpha = .30f), border = Color.Transparent), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Txt(m?.source ?: "No source", 19, 23, p.text, weight = FontWeight.SemiBold)
        }
        Txt("Queue \u203A", 19, 21, p.cyan, Modifier.at(619, 162).width(65.dp).tap { onAction(HomeAction.Go(Dest.Media)) }, align = TextAlign.End)
        Txt(m?.title ?: "Nothing playing", 56, 56, p.text, Modifier.width(346.dp).baselineAt(338, 246), cond, FontWeight.SemiBold)
        Txt(
            if (m == null) "Open Media to choose a source" else "${m.artist} \u00B7 ${m.album}", 26, 26, p.dim,
            Modifier.width(346.dp).baselineAt(338, 293), cond, FontWeight.SemiBold,
        )
        SegBar(64, 10.dp, Modifier.at(338, 300).width(346.dp), litCount = { Fmt.lit(m?.progress ?: 0.0, 64) })
        Txt(clock(m?.elapsedSec ?: 0), 20, 24, p.text, Modifier.baselineAt(338, 336), cond, FontWeight.Medium, tnum = true)
        Txt(clock(m?.durationSec ?: 0), 20, 24, p.text, Modifier.width(346.dp).baselineAt(338, 336), cond, FontWeight.Medium, TextAlign.End, tnum = true)
        Box(Modifier.at(338, 350).size(107.dp, 64.dp).roundBtn()) { Icon(Ic.Prev, 30, p.text, Modifier.at(39, 15)) }
        Box(Modifier.at(457, 342).size(107.dp, 80.dp).roundBtn(primary = true)) { Icon(if (m?.playing == false) Ic.Play else Ic.Pause, 40, p.onAccent, Modifier.at(34, 18)) }
        Box(Modifier.at(577, 350).size(107.dp, 64.dp).roundBtn()) { Icon(Ic.Next, 30, p.text, Modifier.at(40, 15)) }

        // ---- shortcuts ----
        ShortcutTile(24, Ic.Nav, "Maps", content.maps) { onAction(HomeAction.Launch("maps")) }
        ShortcutTile(255, Ic.Radio, "Radio \u00B7 FM", content.radio) { onAction(HomeAction.Launch("radio")) }
        ShortcutTile(485, Ic.Snow, "Climate", content.climate) { onAction(HomeAction.Go(Dest.Car)) }

        // ---- recents ----
        Txt("Recent", 19, 20, p.dim, Modifier.at(24, 635))
        Recent(24, Ic.Route, "Trips") { onAction(HomeAction.Go(Dest.Drive)) }
        Recent(162, Ic.Park, "Parking") { onAction(HomeAction.Go(Dest.Car)) }
        Recent(301, Ic.Pulse, "Diagnose") { onAction(HomeAction.Go(Dest.Car)) }
        Recent(439, Ic.Eq, "Sound") { onAction(HomeAction.Go(Dest.Settings)) }
        Recent(578, Ic.Camera, "Dash cam") { onAction(HomeAction.Launch("camera")) }

        // ---- vitals column ----
        Box(Modifier.at(728, 80).size(416.dp, 250.dp).card().tap { onAction(HomeAction.Go(Dest.Drive)) })
        Txt("Speed", 19, 21, p.dim, Modifier.at(752, 105))
        Row(Modifier.at(1037, 100).size(83.dp, 32.dp).pill(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Txt("Gear", 18, 22, p.text, weight = FontWeight.SemiBold)
            LiveTxt({ Fmt.gear(s.value.gear) }, 18, 22, p.text, weight = FontWeight.SemiBold)
        }
        ValueUnit(
            752, 282,
            value = { LiveTxt({ Fmt.whole(s.value.sample?.speedKmh) }, 168, 168, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) },
            unit = { Txt("km/h", 23, 28, p.dim, family = cond) },
            gap = 20,
        )
        SegBar(40, 10.dp, Modifier.at(752, 300).width(368.dp), litCount = { Fmt.lit(Fmt.fraction(s.value.sample?.speedKmh, 0.0, 200.0), 40) })

        Box(Modifier.at(728, 342).size(416.dp, 170.dp).card())
        Txt("Engine", 19, 21, p.dim, Modifier.at(752, 370))
        BaselineRow(1120, 396, gap = 11, alignEnd = true) {
            LiveTxt({ Fmt.grouped(Math.round(s.value.sample?.rpm ?: 0.0).toInt()) }, 40, 40, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true)
            Txt("rpm", 23, 28, p.dim, family = cond)
        }
        SegBar(34, 8.dp, Modifier.at(752, 413).width(368.dp), litCount = { Fmt.lit(Fmt.fraction(s.value.sample?.rpm, 0.0, 7000.0), 34) })
        Txt("Boost", 19, 21, p.dim, Modifier.at(752, 441))
        BaselineRow(1120, 467, gap = 11, alignEnd = true) {
            LiveTxt({ Fmt.signed((s.value.sample?.boostKpa ?: 0.0) / 100.0, 2) }, 40, 40, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true)
            Txt("bar", 23, 28, p.dim, family = cond)
        }
        val segs = { BoostBar.segments((s.value.sample?.boostKpa ?: 0.0) / 100.0, 34) }
        SegBar(
            34, 8.dp, Modifier.at(752, 484).width(368.dp),
            litFrom = { segs().litFrom }, litCount = { segs().litCount }, vacuumFrom = { segs().vacFrom }, vacuumCount = { segs().vacCount },
        )

        Box(Modifier.at(728, 524).size(416.dp, 130.dp).card())
        IconLabel(752, 544, Ic.Fuel, "Fuel")
        ValueUnit(
            752, 627,
            value = { LiveTxt({ Fmt.whole(s.value.sample?.fuelPct) }, 56, 56, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true) },
            unit = { Txt("%", 23, 28, p.dim, family = cond) },
            gap = 0,
        )
        SegBar(20, 24.dp, Modifier.at(930, 563).width(190.dp), litCount = { Fmt.lit(Fmt.fraction(s.value.sample?.fuelPct, 0.0, 100.0), 20) })
        LiveTxt({ Range.km(s.value.sample?.fuelPct)?.let { "\u2248 $it km range" } ?: Fmt.DASH }, 19, 21, p.dim, Modifier.at(930, 595).width(190.dp), align = TextAlign.End)

        Vital(728, "Coolant", Ic.Thermo, "\u00B0C", { Fmt.whole(s.value.sample?.coolantC) }, 842) { Fmt.lit(Fmt.fraction(s.value.sample?.coolantC, 60.0, 120.0), 6) }
        Vital(942, "Engine load", Ic.Pulse, "%", { Fmt.whole(s.value.sample?.loadPct) }, 1056) { Fmt.lit(Fmt.fraction(s.value.sample?.loadPct, 0.0, 100.0), 6) }
    }
}

private fun clock(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
private fun IconLabel(x: Int, y: Int, ic: Ic, text: String) {
    val p = LocalPalette.current
    Row(Modifier.at(x, y), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(ic, 22, p.dim)
        Txt(text, 19, 26, p.dim)
    }
}

@Composable
private fun ShortcutTile(x: Int, ic: Ic, label: String, t: Shortcut, onClick: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    Box(Modifier.at(x, 454).size(219.dp, 170.dp).card().tap(onClick))
    IconLabel(x + 24, 472, ic, label)
    ValueUnit(
        x + 24, 548,
        value = { Txt(t.value, 54, 54, if (t.accent) p.cyan else p.text, family = cond, weight = FontWeight.Bold) },
        unit = { if (t.unit != null) Txt(t.unit, 23, 28, p.dim, family = cond) },
    )
    Txt(t.title, 26, 26, p.text, Modifier.width(171.dp).baselineAt(x + 24, 579), cond, FontWeight.SemiBold)
    Txt(t.subtitle, 19, 21, p.dim, Modifier.width(171.dp).at(x + 24, 582), letterSpacing = 0.04.em)
}

@Composable
private fun Recent(x: Int, ic: Ic, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.at(x, 666).size(126.dp, 110.dp).tile().tap(onClick)) {
        Icon(ic, 34, p.sky, Modifier.at(46, 21))
        Txt(label, 19, 23, p.text, Modifier.width(126.dp).at(0, 67), weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

@Composable
private fun Vital(x: Int, label: String, ic: Ic, unit: String, value: () -> String, barX: Int, lit: () -> Int) {
    val p = LocalPalette.current
    Box(Modifier.at(x, 666).size(202.dp, 110.dp).card())
    IconLabel(x + 24, 684, ic, label)
    ValueUnit(
        x + 24, 759,
        value = { LiveTxt(value, 56, 56, p.text, family = CockpitFonts.Condensed, weight = FontWeight.SemiBold, tnum = true) },
        unit = { Txt(unit, 23, 28, p.dim, family = CockpitFonts.Condensed) },
        gap = 0,
    )
    SegBar(6, 10.dp, Modifier.at(barX, 733).width(64.dp), litCount = lit)
}

/** Album-art placeholder: four hairline rings and a round cyan disc on s2, clipped to the 22 px tile. */
@Composable
private fun AlbumArt(modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val r22 = 22.dp.toPx()
        val tile = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r22))) }
        clipPath(tile) {
            drawRect(p.s2)
            val c = Offset(size.width / 2, size.height / 2)
            val corner = size.width * 0.70710677f                 // farthest-corner distance the design's radial gradient is sized to
            for (f in floatArrayOf(.22f, .36f, .52f, .70f)) {
                drawCircle(p.blue.copy(alpha = .25f), (f + .005f) * corner, c, style = Stroke(.01f * corner))
            }
            drawCircle(p.cyan, size.width * .13f, c)
        }
    }
}
