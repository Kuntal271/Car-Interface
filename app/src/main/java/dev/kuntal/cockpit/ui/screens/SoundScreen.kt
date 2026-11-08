package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kuntal.cockpit.data.AudioPresets
import dev.kuntal.cockpit.data.AudioSettings
import dev.kuntal.cockpit.data.AudioState
import dev.kuntal.cockpit.data.Fmt
import dev.kuntal.cockpit.ui.components.BaselineRow
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.IconLabelAt
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.TabPills
import dev.kuntal.cockpit.ui.components.ToggleSwitch
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.WaitingCard
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.shell.SystemVolume
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/** Signed whole dB, with the real minus sign: "+4", "0", "\u22121". */
private fun db(v: Int) = if (v == 0) "0" else if (v > 0) "+$v" else "\u2212${-v}"

private const val COL0 = 119.7f
private const val COLP = 73.35f
private const val EQ_TOP = 246f
private const val ROW_P = 12.8f

/**
 * Sound: 8-band equalizer, balance / fader pad, subwoofer, loudness and the music volume, over an [AudioSettings]. The unit's real
 * DSP is a vendor service this app cannot reach yet, so the controls drive a mock that remembers values and changes no sound.
 */
@Composable
fun SoundScreen(audio: AudioSettings?, volume: SystemVolume, onOpenEq: () -> Unit) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    if (audio == null) {
        Box(Modifier.fillMaxSize()) {
            WaitingCard(24, 80, 1120, 696, Ic.Eq, "Sound is set on the unit", "The equalizer, balance and fader belong to the unit's own sound app. This screen can control them once the unit shares its sound service.")
            PillBtn(504, 560, 280, "Open the unit's EQ", onOpenEq, ic = Ic.Eq, primary = true)
        }
        return
    }
    val st = audio.state.collectAsStateWithLifecycle()
    val preset by remember { derivedStateOf { st.value.preset } }
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Txt("Sound", 34, 34, p.text, Modifier.at(24, 85), cond, FontWeight.SemiBold)
            Box(Modifier.at(24, 136).size(690.dp, 478.dp).card())
            Box(Modifier.at(24, 626).size(690.dp, 150.dp).card())
            Box(Modifier.at(738, 80).size(406.dp, 410.dp).card())
            Box(Modifier.at(738, 502).size(406.dp, 274.dp).card())
            Txt("Equalizer \u00B7 8 band", 19, 21, p.dim, Modifier.at(48, 162))
            Txt("+12 dB", 18, 18, p.dim, Modifier.at(48, 241).size(51.dp, 18.dp), align = TextAlign.End)
            Txt("0 dB", 18, 18, p.dim, Modifier.at(48, 395).size(51.dp, 18.dp), align = TextAlign.End)
            Txt("\u221212 dB", 18, 18, p.dim, Modifier.at(48, 549).size(51.dp, 18.dp), align = TextAlign.End)
            AudioPresets.FREQ.forEachIndexed { i, f -> Txt(f, 18, 22, p.dim, Modifier.at((COL0 + COLP * i - 14).toInt(), 572).size(74.dp, 22.dp), align = TextAlign.Center) }
            IconLabelAt(48, 659, Ic.Vol, "Volume")
            Txt("Balance \u00B7 Fader", 19, 21, p.dim, Modifier.at(762, 104))
            Txt("Subwoofer", 22, 26, p.text, Modifier.at(762, 520), weight = FontWeight.SemiBold)
            Txt("\u221210 to +10 dB", 18, 21, p.dim, Modifier.at(950, 562).size(170.dp, 21.dp), weight = FontWeight.Normal, align = TextAlign.Center)
            Txt("Loudness", 22, 26, p.text, Modifier.at(762, 609), weight = FontWeight.SemiBold)
            Txt("Speed-sensitive volume", 22, 26, p.text, Modifier.at(762, 677), weight = FontWeight.SemiBold)
            Txt("Raises level with road noise", 18, 22, p.dim, Modifier.at(762, 703), weight = FontWeight.Normal)
        }
        DemoMark(150, 88)
        TabPills(296, 78, AudioPresets.NAMES, preset, { it }) { audio.applyPreset(it) }

        // preset badge
        Row(Modifier.at(548, 156).height(32.dp).pill(border = p.cyan.copy(alpha = .5f)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveTxt({ st.value.preset + if (st.value.edited) " \u00B7 edited" else "" }, 18, 22, p.cyan, weight = FontWeight.SemiBold)
        }
        Bands(st, audio)
        LiveTxt({ volume.level.value.toString() }, 56, 50, p.text, Modifier.at(592, 646).size(60.dp, 52.dp), cond, FontWeight.SemiBold, TextAlign.End, tnum = true)
        Txt("/ ${volume.max}", 23, 28, p.dim, Modifier.at(656, 670), family = cond)
        SegSlider(48, 722, 642, 22, 40, frac = { volume.level.value / volume.max.toFloat() }, knob = true) { f -> volume.set(Math.round(f * volume.max)) }

        // balance / fader readout and pad
        Row(Modifier.at(920, 102).size(200.dp, 23.dp), horizontalArrangement = Arrangement.End) {
            LiveTxt({ side(st.value.balance, "L", "R") }, 19, 23, p.text)
            Txt(" / 10 \u00B7 ", 19, 23, p.dim)
            LiveTxt({ side(st.value.fader, "R", "F") }, 19, 23, p.text)
            Txt(" / 10", 19, 23, p.dim)
        }
        Pad(st, audio)

        // subwoofer, loudness, speed-sensitive volume
        Row(Modifier.at(762, 547).size(172.dp, 22.dp)) {
            LiveTxt({ db(st.value.subwooferDb) }, 18, 22, p.text, weight = FontWeight.SemiBold)
            Txt(" dB", 18, 22, p.dim, weight = FontWeight.Normal)
        }
        SegSlider(950, 523, 170, 22, 12, frac = { (st.value.subwooferDb + 10) / 20f }, knob = true) { f -> audio.setSubwoofer(Math.round(f * 20) - 10) }
        val loud by remember { derivedStateOf { st.value.loudness } }
        val spd by remember { derivedStateOf { st.value.speedVolume } }
        ToggleSwitch(1032, 600, loud) { audio.setLoudness(!loud) }
        ToggleSwitch(1032, 679, spd) { audio.setSpeedVolume(!spd) }
    }
}

private fun side(v: Int, neg: String, pos: String) = if (v == 0) "0" else if (v < 0) "$neg ${-v}" else "$pos $v"

@Composable
private fun Bands(st: androidx.compose.runtime.State<AudioState>, audio: AudioSettings) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    // dB readouts
    for (i in 0 until AudioState.BANDS) {
        val cx = COL0 + COLP * i + 23.1f
        Box(Modifier.at((cx - 40).toInt(), 202).size(80.dp, 34.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                LiveTxt({ db(st.value.bands.getOrElse(i) { 0 }) }, 30, 34, p.text, family = cond, weight = FontWeight.Bold, tnum = true)
                Txt(" dB", 18, 24, p.dim)
            }
        }
    }
    val unlit = p.sky.copy(alpha = .18f)
    Canvas(
        Modifier.at(100, 236).size(640.dp, 340.dp).pointerInputBands(audio),
    ) {
        val d = 1.dp.toPx()
        val bands = st.value.bands
        for (i in 0 until AudioState.BANDS) {
            val x = (COL0 + COLP * i - 100f) * d
            val v = bands.getOrElse(i) { 0 }
            for (r in 0..24) {
                val value = r - 12
                val lit = (v > 0 && value in 1..v) || (v < 0 && value in v..-1)
                val y = (553.2f - ROW_P * r - 236f) * d
                drawRoundRect(if (lit) p.cyan else unlit, Offset(x, y), Size(46.2f * d, 8.8f * d), CornerRadius(3f * d))
            }
        }
    }
}

/** Vertical drag on a column sets that band, 25 steps from -12 to +12 dB. */
private fun Modifier.pointerInputBands(audio: AudioSettings): Modifier = this.pointerInput(audio) {
    awaitEachGesture {
        val d = awaitFirstDown()
        fun apply(pos: Offset) {
            val density = size.width / 640f
            val xDesign = pos.x / density + 100f; val yDesign = pos.y / density + 236f
            val i = Math.floor(((xDesign - (COL0 - 13.5f)) / COLP).toDouble()).toInt()
            if (i !in 0 until AudioState.BANDS) return
            val rowFromBottom = ((553.2f + 8.8f / 2 - yDesign) / ROW_P)
            audio.setBand(i, Math.round(rowFromBottom).toInt() - 12)
        }
        apply(d.position)
        drag(d.id) { ch -> apply(ch.position); ch.consume() }
    }
}

@Composable
private fun Pad(st: androidx.compose.runtime.State<AudioState>, audio: AudioSettings) {
    val p = LocalPalette.current
    Canvas(
        Modifier.at(762, 135).size(358.dp, 331.dp).pointerInput(audio) {
            awaitEachGesture {
                val d = awaitFirstDown()
                fun apply(o: Offset) {
                    val fx = (o.x / size.width).coerceIn(0f, 1f); val fy = (o.y / size.height).coerceIn(0f, 1f)
                    audio.setBalance(Math.round((fx - .5f) * 20)); audio.setFader(Math.round((.5f - fy) * 20))
                }
                apply(d.position); drag(d.id) { ch -> apply(ch.position); ch.consume() }
            }
        },
    ) {
        val u = 1.dp.toPx()
        val w = 357f * u; val h = 330f * u; val x0 = .5f * u; val y0 = .5f * u
        drawRect(p.s1, Offset(x0, y0), Size(w, h))
        val grid = p.blue.copy(alpha = .5f)
        for (k in 0..6) drawLine(grid, Offset(x0 + w * k / 6, y0), Offset(x0 + w * k / 6, y0 + h), u)
        for (k in 0..5) drawLine(grid, Offset(x0, y0 + h * k / 5), Offset(x0 + w, y0 + h * k / 5), u)
        val bal = st.value.balance; val fad = st.value.fader
        val cx = x0 + w / 2 + bal / 10f * w / 2; val cy = y0 + h / 2 - fad / 10f * h / 2
        drawLine(p.cyan, Offset(cx, y0), Offset(cx, y0 + h), u); drawLine(p.cyan, Offset(x0, cy), Offset(x0 + w, cy), u)
        val dia = Path().apply { moveTo(cx, cy - 20 * u); lineTo(cx + 20 * u, cy); lineTo(cx, cy + 20 * u); lineTo(cx - 20 * u, cy); close() }
        drawPath(dia, p.cyan)
    }
    Txt("Front", 20, 25, p.text, Modifier.at(775, 143), weight = FontWeight.SemiBold)
    Txt("Rear", 20, 25, p.text, Modifier.at(775, 433), weight = FontWeight.SemiBold)
    Txt("L", 20, 25, p.text, Modifier.at(775, 287), weight = FontWeight.SemiBold)
    Txt("R", 20, 25, p.text, Modifier.at(1095, 287), weight = FontWeight.SemiBold)
}
