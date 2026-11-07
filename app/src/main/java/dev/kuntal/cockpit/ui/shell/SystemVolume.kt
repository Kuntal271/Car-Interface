package dev.kuntal.cockpit.ui.shell

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.kuntal.cockpit.DebugHooks
import kotlinx.coroutines.delay

/** The unit's real music volume (no permission needed to change it). Snapshot mode freezes it on the design's 18 of 30. */
class SystemVolume(private val am: AudioManager?, private val frozen: Boolean) {
    val max: Int = if (frozen) 30 else (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
    private val _level = mutableIntStateOf(if (frozen) DebugHooks.VOLUME else am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    val level: State<Int> get() = _level
    val muted: Boolean get() = !frozen && (am?.isStreamMute(AudioManager.STREAM_MUSIC) == true || _level.intValue == 0)

    fun refresh() { if (!frozen && am != null) _level.intValue = am.getStreamVolume(AudioManager.STREAM_MUSIC) }
    fun set(v: Int) {
        val n = v.coerceIn(0, max)
        if (!frozen) runCatching { am?.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0) }
        _level.intValue = n
    }
    /** Mute is the real stream mute where the unit allows it; otherwise volume 0 (and back to [restore]). */
    fun toggleMute(restore: Int) {
        val a = am ?: return
        if (frozen) return
        if (muted) { runCatching { a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }; if (_level.intValue == 0) set(restore.coerceAtLeast(1)) }
        else runCatching { a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
        refresh()
    }
}

/** One SystemVolume for the screen, re-read every second while composed (hardware keys and other apps change it too). */
@Composable
fun rememberSystemVolume(): SystemVolume {
    val ctx = LocalContext.current
    val v = remember { SystemVolume(ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager, DebugHooks.snapshot) }
    LaunchedEffect(v) { while (true) { delay(1_000); v.refresh() } }
    return v
}
