package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuntal.cockpit.data.BoostBar
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.data.PressureUnit
import dev.kuntal.cockpit.data.SpeedUnit
import dev.kuntal.cockpit.data.Units
import dev.kuntal.cockpit.data.VehicleUiState
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.SegBar
import dev.kuntal.cockpit.ui.components.Tach
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.ValueUnit
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.baselineAt
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.smoothed
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette
import dev.kuntal.cockpit.ui.theme.LocalSettings


/**
 * Drive: tach + speed, boost, four vitals, trip strip. Layout is the measured design (1280 x 800 grid).
 * Every value is read from [s] inside a text/draw lambda, so telemetry at 20 Hz recomposes only the strings that changed.
 */
@Composable
fun DriveScreen(s: State<VehicleUiState>) {
    val p = LocalPalette.current
    val st = LocalSettings.current
    val u = st.speedUnit; val pu = st.pressureUnit; val tu = st.tempUnit
    val cond = CockpitFonts.Condensed
    val rpm = smoothed(0.06f) { (s.value.sample?.rpm ?: 0.0).toFloat() }
    val boostBar = smoothed(0.09f) { ((s.value.sample?.boostKpa ?: 0.0) / 100.0).toFloat() }

    Box(Modifier.fillMaxSize()) {
        // shift-light bar (48 segments) and its label
        val shiftLit = remember { derivedStateOf { Fmt.lit(((rpm.value - (st.shiftRpm - 2000)) / 2000f).toDouble().coerceIn(0.0, 1.0), 48) } }
        SegBar(48, 10.dp, Modifier.at(24, 75).width(977.dp), litCount = { shiftLit.value })
        Txt("Shift ${Fmt.grouped(st.shiftRpm)} rpm", 19, 21, p.dim, Modifier.at(1013, 70))

        // tach + speed + gear
        Tach(rpm, Modifier.at(24, 96).size(600.dp), maxRpm = st.tachMaxRpm.toFloat(), redRpm = st.redlineRpm.toFloat())
        LiveTxt(
            { Fmt.speed(s.value.sample?.speedKmh, u) }, 236, 236, p.text, Modifier.width(600.dp).baselineAt(24, 446),
            cond, FontWeight.SemiBold, TextAlign.Center, letterSpacing = 0.sp, tnum = true,
        )
        Txt(Units.label(u), 22, 26, p.dim, Modifier.at(30, 460).width(594.dp), cond, FontWeight.Medium, TextAlign.Center)
        Row(
            Modifier.at(248, 566).size(151.dp, 40.dp).pill(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Gear", 20, 24, p.text, weight = FontWeight.SemiBold)
            LiveTxt({ Fmt.gear(s.value.gear) }, 26, 31, p.text, weight = FontWeight.SemiBold)
            Txt("\u00B7 est.", 20, 24, p.text, weight = FontWeight.SemiBold)
        }

        // boost card
        Box(Modifier.at(652, 100).size(492.dp, 262.dp).card())
        IconLabel(676, 121, Ic.Bolt, "Boost")
        Row(
            Modifier.at(968, 120).size(152.dp, 32.dp).pill(border = p.cyan.copy(alpha = .5f)),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Peak", 18, 22, p.cyan, weight = FontWeight.SemiBold)
            LiveTxt({ Fmt.boost(s.value.trip.peakBoostKpa ?: 0.0, pu) }, 18, 22, p.cyan, weight = FontWeight.SemiBold)
            Txt(Units.label(pu), 18, 22, p.cyan, weight = FontWeight.SemiBold)
        }
        ValueUnit(
            676, 261,
            value = { LiveTxt({ Fmt.boost(s.value.sample?.boostKpa ?: 0.0, pu) }, 120, 120, p.text, family = cond, weight = FontWeight.SemiBold, letterSpacing = 0.sp, tnum = true) },
            unit = { Txt(Units.label(pu), 23, 28, p.dim, family = cond) },
        )
        val segs = { BoostBar.segments(boostBar.value.toDouble()) }
        SegBar(
            BoostBar.N, 28.dp, Modifier.at(676, 276).width(444.dp),
            litFrom = { segs().litFrom }, litCount = { segs().litCount },
            vacuumFrom = { segs().vacFrom }, vacuumCount = { segs().vacCount },
        )
        BoostScale(pu)

        // four vitals
        Tile(652, 374, Ic.Thermo, "Coolant", Units.label(tu), { Fmt.temp(s.value.sample?.coolantC, tu) }) { Fmt.lit(Fmt.fraction(s.value.sample?.coolantC, 40.0, 120.0), 20) }
        Tile(904, 374, Ic.Fan, "Intake air", Units.label(tu), { Fmt.temp(s.value.sample?.intakeC, tu) }) { Fmt.lit(Fmt.fraction(s.value.sample?.intakeC, 0.0, 110.0), 20) }
        Tile(652, 536, Ic.Gauge, "Throttle", "%", { Fmt.whole(s.value.sample?.throttlePct) }) { Fmt.lit(Fmt.fraction(s.value.sample?.throttlePct, 0.0, 100.0), 20) }
        Tile(904, 536, Ic.Fuel, "Fuel", "%", { Fmt.whole(s.value.sample?.fuelPct) }) { Fmt.lit(Fmt.fraction(s.value.sample?.fuelPct, 0.0, 100.0), 20) }

        // trip strip
        Box(Modifier.at(24, 698).size(1120.dp, 78.dp).card(fillAlpha = .55f, flat = true))
        TripCell(46, "Trip A", if (u == SpeedUnit.KMH) "km" else "mi", { Fmt.distance(s.value.trip.distanceKm, u) })
        TripCell(270, "Average", Units.label(u), { Fmt.speed(s.value.trip.avgKmh, u) })
        TripCell(494, "Top speed", Units.label(u), { Fmt.speed(s.value.trip.topKmh, u) })
        TripCell(719, "0\u2013100 best", "s", { s.value.trip.zeroTo100Best?.let { Fmt.dec(it, 1) } ?: Fmt.DASH }, accent = true)
        TripCell(943, "Engine load", "%", { Fmt.whole(s.value.sample?.loadPct) })
    }
}

/** Dim label with its 22 px line icon in front (8 px gap), as every card label in the design. */
@Composable
private fun IconLabel(x: Int, y: Int, ic: Ic, text: String) {
    val p = LocalPalette.current
    Row(Modifier.at(x, y), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(ic, 22, p.dim)
        Txt(text, 19, 26, p.dim)
    }
}

@Composable
private fun BoostScale(pu: PressureUnit) {
    val p = LocalPalette.current
    val w = 444; val x0 = 676
    fun cx(bar: Double) = x0 + ((bar - BoostBar.MIN_BAR) / (BoostBar.MAX_BAR - BoostBar.MIN_BAR) * w).toInt()
    fun tick(bar: Double) = Fmt.boostTick(bar * 100.0, pu)            // labels follow the chosen pressure unit, positions stay on the bar scale
    Txt(tick(-1.0), 18, 22, p.dim, Modifier.at(x0, 310))
    Txt(tick(0.0), 18, 22, p.dim, Modifier.at(cx(0.0) - 5, 310))
    for (b in listOf(0.5, 1.0)) Txt(tick(b), 18, 22, p.dim, Modifier.at(cx(b) - 16, 310))
    Txt(tick(1.5), 18, 22, p.dim, Modifier.width(60.dp).at(x0 + w - 60, 310), align = TextAlign.End)
}

@Composable
private fun Tile(x: Int, y: Int, ic: Ic, label: String, unit: String, value: () -> String, lit: () -> Int) {
    val p = LocalPalette.current
    Box(Modifier.at(x, y).size(240.dp, 150.dp).card())
    IconLabel(x + 24, y + 18, ic, label)
    ValueUnit(
        x + 24, y + 105,
        value = { LiveTxt(value, 64, 64, p.text, family = CockpitFonts.Condensed, weight = FontWeight.Bold, tnum = true) },
        unit = { Txt(unit, 23, 28, p.dim, family = CockpitFonts.Condensed) },
        gap = 0,
    )
    SegBar(20, 14.dp, Modifier.at(x + 24, y + 116).width(192.dp), litCount = lit)
}

@Composable
private fun TripCell(x: Int, label: String, unit: String, value: () -> String, accent: Boolean = false) {
    val p = LocalPalette.current
    Txt(label, 19, 21, p.dim, Modifier.at(x, 704))
    ValueUnit(
        x, 765,
        value = { LiveTxt(value, 40, 40, if (accent) p.cyan else p.text, family = CockpitFonts.Condensed, weight = FontWeight.Bold, tnum = true) },
        unit = { Txt(unit, 23, 28, p.dim, family = CockpitFonts.Condensed) },
    )
}
