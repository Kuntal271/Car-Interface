package dev.kuntal.cockpit

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.kuntal.cockpit.data.Settings
import dev.kuntal.cockpit.perf.FrameMeter
import dev.kuntal.cockpit.ui.screens.SettingsPage
import dev.kuntal.cockpit.ui.shell.Dest

class MainActivity : ComponentActivity() {
    private var meter: FrameMeter? = null
    private val homePresses = mutableIntStateOf(0)         // bumped when HOME is pressed while we are already running (singleTask delivers it here)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The one place launch extras are read for dev hooks; see DevLaunch for what a release build ignores.
        val dev = DevLaunch.parse(
            debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            bool = { intent.getBooleanExtra(it, false) }, str = { intent.getStringExtra(it) },
        )
        DebugHooks.snapshot = dev.snapshot
        DebugHooks.perf = dev.perf
        meter = FrameMeter(window, log = DebugHooks.perf)
        applyDevSettings(dev)
        // `adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen drive` opens a given screen (used by tools/shot.sh and tests)
        val start = intent.getStringExtra("screen")?.let { n -> Dest.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: Dest.Home
        val page = intent.getStringExtra("page")?.let { n -> SettingsPage.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: SettingsPage.Display
        setContent { CockpitApp(startOn = start, startPage = page, homePresses = homePresses.intValue) }
    }

    /**
     * Dev hook for scripted runs: `--es set "theme=Midnight,source=ObdTcp,tcpHost=10.0.2.2"` (keys as in Settings.toMap) changes and
     * stores settings, `--ez reset true` restores the defaults first. Snapshot mode always starts from the defaults. Debuggable builds only (DevLaunch).
     */
    private fun applyDevSettings(dev: DevLaunch) {
        val store = SettingsStore.get(this)
        if (dev.reset || dev.snapshot) store.update { Settings() }
        if (dev.set.isNotEmpty()) store.update { Settings.fromMap(it.toMap() + dev.set) }
    }

    /** A launcher answers the HOME key with its home screen. Before this, HOME on Drive/Apps/Settings did nothing (the intent was dropped). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && intent.getStringExtra("screen") == null) homePresses.intValue++
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
