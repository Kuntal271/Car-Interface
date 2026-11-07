package dev.kuntal.cockpit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.kuntal.cockpit.data.MotionMode
import dev.kuntal.cockpit.data.Smoother
import dev.kuntal.cockpit.ui.theme.LocalSettings
import kotlinx.coroutines.flow.collectLatest

/**
 * A value that glides to [target] with exponential smoothing, one step per display frame. The loop only runs while the value
 * is moving; when it has settled nothing is scheduled, so an idle dashboard costs no frames.
 */
@Composable
fun smoothed(tauSec: Float = 0.07f, target: () -> Float): State<Float> {
    val read by rememberUpdatedState(target)
    val reduced = LocalSettings.current.motion == MotionMode.Reduced
    val out = remember { mutableFloatStateOf(target()) }
    val snapped = remember { derivedStateOf { read() } }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect                       // reduced motion: no gliding, readers follow the reading directly
        snapshotFlow { read() }.collectLatest { goal ->
            var last = withFrameNanos { it }
            while (out.floatValue != goal) {
                val now = withFrameNanos { it }
                out.floatValue = Smoother.step(out.floatValue, goal, (now - last) / 1e9f, tauSec)
                last = now
            }
        }
    }
    return if (reduced) snapped else out
}
