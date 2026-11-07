package dev.kuntal.cockpit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import dev.kuntal.cockpit.DebugHooks

/**
 * A full-screen render node for content that never changes (cards, labels, icons, gauge furniture). It is recorded once and replayed.
 * With `--ez flatten true` it is additionally rendered once into a texture, so a frame draws one textured quad instead of replaying
 * every card, gradient and glyph (a fill-rate and draw-call saving on a weak GPU; see docs/reviews/perf.md for the measurement).
 */
@Composable
fun StaticLayer(content: @Composable () -> Unit) {
    val strategy = if (DebugHooks.flatten) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    Box(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = strategy)) { content() }
}
