package dev.kuntal.cockpit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kuntal.cockpit.data.MediaFmt
import dev.kuntal.cockpit.data.MediaKind
import dev.kuntal.cockpit.data.MediaSource
import dev.kuntal.cockpit.data.RepeatMode
import dev.kuntal.cockpit.data.toggle
import dev.kuntal.cockpit.ui.components.AlbumArt
import dev.kuntal.cockpit.ui.components.BaselineRow
import dev.kuntal.cockpit.ui.components.DemoMark
import dev.kuntal.cockpit.ui.components.Ic
import dev.kuntal.cockpit.ui.components.Icon
import dev.kuntal.cockpit.ui.components.IconLabelAt
import dev.kuntal.cockpit.ui.components.LiveTxt
import dev.kuntal.cockpit.ui.components.OptionChip
import dev.kuntal.cockpit.ui.components.PillBtn
import dev.kuntal.cockpit.ui.components.SegSlider
import dev.kuntal.cockpit.ui.components.StaticLayer
import dev.kuntal.cockpit.ui.components.TabPills
import dev.kuntal.cockpit.ui.components.Txt
import dev.kuntal.cockpit.ui.components.WaitingCard
import dev.kuntal.cockpit.ui.components.at
import dev.kuntal.cockpit.ui.components.card
import dev.kuntal.cockpit.ui.components.pill
import dev.kuntal.cockpit.ui.components.roundBtn
import dev.kuntal.cockpit.ui.components.tap
import dev.kuntal.cockpit.ui.shell.SystemVolume
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/**
 * Media: now playing, transport, volume and the queue, over a [MediaSource] (a labelled mock until a real MediaSession exists).
 * With no source the screen says so and offers the unit's own player instead of showing invented tracks.
 */
@Composable
fun MediaScreen(
    media: MediaSource?, volume: SystemVolume, eqPreset: String?,
    onSound: () -> Unit, onRadio: () -> Unit, onOpenPlayer: () -> Unit,
) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    if (media == null) {
        Box(Modifier.fillMaxSize()) {
            WaitingCard(24, 80, 1120, 696, Ic.Music, "Waiting for a media player", "Nothing is playing and no player has shared its session with this app. Start music in the player app and it shows up here.")
            PillBtn(504, 560, 280, "Open the music player", onOpenPlayer, ic = Ic.Music, primary = true)
        }
        return
    }
    val st = media.state.collectAsStateWithLifecycle()
    val kind by remember { derivedStateOf { st.value?.kind ?: MediaKind.Bluetooth } }
    Box(Modifier.fillMaxSize()) {
        StaticLayer {
            Box(Modifier.at(24, 140).size(650.dp, 400.dp).card())
            AlbumArt(Modifier.at(46, 195).size(290.dp))
            Box(Modifier.at(24, 552).size(650.dp, 224.dp).card())
            IconLabelAt(48, 578, Ic.Vol, "Volume")
            Box(Modifier.at(698, 80).size(446.dp, 696.dp).card())
            Txt("Up next", 26, 29, p.text, Modifier.at(718, 94), cond, FontWeight.SemiBold)
        }
        val demo by remember { derivedStateOf { st.value?.demo == true } }
        if (demo) DemoMark(545, 88)
        TabPills(24, 80, MediaKind.entries, kind, { it.label }) { k -> if (k == MediaKind.Radio) onRadio() else media.setKind(k) }

        // format badge
        val fmt by remember { derivedStateOf { st.value?.format } }
        fmt?.let { f -> Row(Modifier.at(360, 162).height(32.dp).pill(border = p.hair).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Txt(f, 18, 22, p.text, weight = FontWeight.SemiBold) } }

        // title block: two-line slot, bottom aligned so the artist always sits right under it
        Box(Modifier.at(360, 208).size(292.dp, 122.dp), contentAlignment = Alignment.BottomStart) {
            val title by remember { derivedStateOf { st.value?.track?.title ?: "Nothing playing" } }
            BasicText(
                title, Modifier.width(292.dp),
                style = TextStyle(color = p.text, fontSize = 64.sp, fontFamily = cond, fontWeight = FontWeight.SemiBold, lineHeight = 61.sp, letterSpacing = 0.012.em, platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)),
                softWrap = true, maxLines = 2,
            )
        }
        LiveTxt({ st.value?.track?.artist ?: "" }, 26, 29, p.dim, Modifier.at(360, 340).width(292.dp), cond, FontWeight.SemiBold)
        LiveTxt({ st.value?.track?.album ?: "" }, 19, 21, p.dim, Modifier.at(360, 376).width(292.dp))
        SegSlider(360, 397, 292, 10, 40, frac = { st.value?.progress?.toFloat() ?: 0f }) { f -> st.value?.track?.let { media.seekTo((f * it.durationSec).toInt()) } }
        LiveTxt({ MediaFmt.clock(st.value?.positionSec ?: 0) }, 20, 24, p.text, Modifier.at(360, 413), cond, FontWeight.Medium, tnum = true)
        LiveTxt({ MediaFmt.remaining(st.value?.remainingSec ?: 0) }, 20, 24, p.text, Modifier.at(500, 413).width(152.dp), cond, FontWeight.Medium, TextAlign.End, tnum = true)

        // transport
        Box(Modifier.at(360, 438).size(89.dp, 80.dp).roundBtn().tap { media.prev() }) { Icon(Ic.Prev, 30, p.text, Modifier.at(30, 25)) }
        val playing by remember { derivedStateOf { st.value?.playing == true } }
        Box(Modifier.at(461, 438).size(89.dp, 80.dp).roundBtn(primary = true).tap { media.toggle() }) { Icon(if (playing) Ic.Pause else Ic.Play, 40, p.onAccent, Modifier.at(25, 20)) }
        Box(Modifier.at(563, 438).size(89.dp, 80.dp).roundBtn().tap { media.next() }) { Icon(Ic.Next, 30, p.text, Modifier.at(30, 25)) }

        // volume (the unit's real music volume)
        BaselineRow(x = 650, baseline = 606, gap = 6, alignEnd = true) {
            LiveTxt({ volume.level.value.toString() }, 40, 36, p.text, family = cond, weight = FontWeight.SemiBold, tnum = true)
            Txt("/ ${volume.max}", 23, 28, p.dim, family = cond)
        }
        SegSlider(48, 650, 602, 22, 36, frac = { volume.level.value / volume.max.toFloat() }, knob = true) { f -> volume.set(Math.round(f * volume.max)) }
        val shuffle by remember { derivedStateOf { st.value?.shuffle == true } }
        val repeat by remember { derivedStateOf { st.value?.repeat ?: RepeatMode.Off } }
        OptionChip(48, 712, 145, "Shuffle", Ic.Shuffle, shuffle, { media.setShuffle(!shuffle) })
        OptionChip(205, 712, 143, when (repeat) { RepeatMode.One -> "Repeat one"; RepeatMode.All -> "Repeat all"; else -> "Repeat" }, Ic.Repeat, repeat != RepeatMode.Off, { media.cycleRepeat() })
        OptionChip(359, 712, 151, "EQ \u00B7 ${eqPreset ?: "Unit"}", Ic.Eq, false, onSound)

        Queue(media)
    }
}

@Composable
private fun Queue(media: MediaSource) {
    val p = LocalPalette.current
    val cond = CockpitFonts.Condensed
    val st = media.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val n by remember { derivedStateOf { st.value?.queue?.size ?: 0 } }
    LiveTxt({ val a = list.firstVisibleItemIndex; "${a + 1}\u2013${minOf(a + 8, n)} of $n" }, 19, 21, p.dim, Modifier.at(924, 98).width(200.dp), align = TextAlign.End)
    val queue by remember { derivedStateOf { st.value?.queue ?: emptyList() } }
    LazyColumn(Modifier.at(708, 135).size(426.dp, 608.dp), state = list) {
        itemsIndexed(queue, key = { i, t -> "$i/${t.title}" }) { i, t ->
            val on by remember(i) { derivedStateOf { st.value?.index == i } }
            Box(
                Modifier.size(426.dp, 76.dp).tap { media.select(i) }.then(
                    if (on) Modifier.drawBehind {
                        drawRoundRect(Brush.horizontalGradient(listOf(p.cyan.copy(alpha = .12f), Color.Transparent)), cornerRadius = CornerRadius(14.dp.toPx()))
                        drawRoundRect(p.accent, Offset.Zero, Size(4.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
                    } else Modifier,
                ),
            ) {
                Txt((i + 1).toString().padStart(2, '0'), 20, 24, p.text, Modifier.at(10, 25), cond, FontWeight.Medium, tnum = true)
                Txt(t.title, 22, 26, p.text, Modifier.at(54, 13).width(270.dp), weight = FontWeight.SemiBold)
                Txt(t.artist, 18, 22, p.dim, Modifier.at(54, 39).width(270.dp), weight = FontWeight.Normal)
                Txt(MediaFmt.clock(t.durationSec), 20, 24, p.text, Modifier.at(316, 25).width(100.dp), cond, FontWeight.Medium, TextAlign.End, tnum = true)
                if (i < queue.size - 1) Box(Modifier.at(0, 75).size(426.dp, 1.dp).background(p.hairSoft))
            }
        }
    }
}
