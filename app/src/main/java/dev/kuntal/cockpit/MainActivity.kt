package dev.kuntal.cockpit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.perf.FrameMeter
import dev.kuntal.cockpit.ui.screens.SettingsPage
import dev.kuntal.cockpit.ui.shell.Dest

class MainActivity : ComponentActivity() {
    private var meter: FrameMeter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        DebugHooks.snapshot = intent.getBooleanExtra("snapshot", false)
        DebugHooks.perf = intent.getBooleanExtra("perf", false)
        meter = FrameMeter(window, log = DebugHooks.perf)
        applyDevSettings()
        // `adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen drive` opens a given screen (used by tools/shot.sh and tests)
        val start = intent.getStringExtra("screen")?.let { n -> Dest.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: Dest.Home
        val page = intent.getStringExtra("page")?.let { n -> SettingsPage.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: SettingsPage.Display
        setContent { CockpitApp(startOn = start, startPage = page) }
    }

    /**
     * Dev hook for scripted runs: `--es set "theme=Midnight,source=ObdTcp,tcpHost=10.0.2.2"` (keys as in Settings.toMap) changes and
     * stores settings, `--ez reset true` restores the defaults first. Snapshot mode always starts from the defaults.
     */
    private fun applyDevSettings() {
        val store = SettingsStore.get(this)
        val kv = intent.getStringExtra("set")?.split(',')?.mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0].trim() to p[1].trim() } }?.toMap().orEmpty()
        if (intent.getBooleanExtra("reset", false) || DebugHooks.snapshot) store.update { Settings() }
        if (kv.isNotEmpty()) store.update { Settings.fromMap(it.toMap() + kv) }
    }

    override fun onStart() { super.onStart(); meter?.start() }
    override fun onStop() { meter?.stop(); super.onStop() }
    override fun onDestroy() { meter?.release(); super.onDestroy() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
