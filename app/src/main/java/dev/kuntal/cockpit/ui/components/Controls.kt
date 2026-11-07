package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.ui.theme.CockpitFonts
import dev.kuntal.cockpit.ui.theme.LocalPalette

/** One-of-N pills. The pill is 46 dp, the touch target 56 dp (spec: targets at least 56). The selected one is solid accent. */
@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (o in options) {
            val on = o == selected
            Box(Modifier.height(56.dp).tap { onSelect(o) }, contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(46.dp).pill(fill = if (on) p.accent else p.sky.copy(alpha = .12f), border = if (on) Color.Transparent else p.hair).padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Txt(label(o), 19, 23, if (on) p.onAccent else p.text, weight = FontWeight.SemiBold) }
            }
        }
    }
}

/** - value + control. Both buttons are 60 x 56. */
@Composable
fun Stepper(value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Ic.Minus, onMinus)
        Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.Center) {
            Txt(value, 34, 38, p.text, Modifier.alignByBaseline(), CockpitFonts.Condensed, FontWeight.Bold, tnum = true)
            Spacer(Modifier.width(6.dp))
            Txt(unit, 23, 28, p.dim, Modifier.alignByBaseline(), CockpitFonts.Condensed)
        }
        StepButton(Ic.Plus, onPlus)
    }
}

@Composable
private fun StepButton(ic: Ic, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(60.dp, 56.dp).pill(fill = p.sky.copy(alpha = .12f)).tap(onClick), contentAlignment = Alignment.Center) { Icon(ic, 22, p.text) }
}

/**
 * A settings row: title (+ optional help line) on the left, [content] right-aligned, hairline underneath. 72 dp high, 708 wide.
 * Placed at design coordinates by the caller.
 */
@Composable
fun SettingRow(x: Int, y: Int, title: String, help: String? = null, last: Boolean = false, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Txt(title, 21, 26, p.text, Modifier.at(x, y + if (help == null) 23 else 12), weight = FontWeight.SemiBold)
    if (help != null) Para(help, 18, 22, p.dim, 460, Modifier.at(x, y + 39), FontWeight.Normal)
    Row(Modifier.at(x, y).size(708.dp, 72.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { content() }
    if (!last) Box(Modifier.at(x, y + 71).size(708.dp, 1.dp).background(p.hairSoft))
}

@Composable
fun Label(text: String, color: Color, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Txt(text, 21, 26, color, modifier, weight = FontWeight.SemiBold, align = align)
