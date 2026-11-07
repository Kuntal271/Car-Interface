package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kuntal.cockpit.ui.theme.LocalPalette

/**
 * Segmented bar: [n] rounded segments with a 3 dp gap. [litCount] and [litFrom] are read in the draw phase only, so a value
 * change repaints this one canvas and does not recompose anything. Unlit segments are the sky tint at 18 %.
 * For a bar with a zero point (boost), [litFrom] is the first lit index and [vacuumTo] the last vacuum index.
 */
@Composable
fun SegBar(
    n: Int, height: Dp, modifier: Modifier = Modifier,
    litCount: () -> Int,
    litFrom: () -> Int = { 0 },
    vacuumFrom: () -> Int = { 0 },
    vacuumCount: () -> Int = { 0 },
    litColor: Color = LocalPalette.current.cyan,
    vacuumColor: Color = LocalPalette.current.sky,
) {
    val unlit = LocalPalette.current.sky.copy(alpha = .18f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val sw = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(3.dp.toPx())
        val from = litFrom(); val lit = litCount()
        val vFrom = vacuumFrom(); val vCount = vacuumCount()
        for (i in 0 until n) {
            val c = when {
                i >= from && i < from + lit -> litColor
                vCount > 0 && i >= vFrom && i < vFrom + vCount -> vacuumColor
                else -> unlit
            }
            drawRoundRect(c, Offset(i * (sw + gap), 0f), Size(sw, size.height), radius)
        }
    }
}
