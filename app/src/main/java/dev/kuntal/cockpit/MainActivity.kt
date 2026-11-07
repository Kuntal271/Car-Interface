package dev.kuntal.cockpit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.kuntal.cockpit.ui.shell.Dest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        DebugHooks.snapshot = intent.getBooleanExtra("snapshot", false)
        // `adb shell am start -n dev.kuntal.cockpit/.MainActivity --es screen drive` opens a given screen (used by tools/shot.sh and tests)
        val start = intent.getStringExtra("screen")?.let { n -> Dest.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } } ?: Dest.Home
        setContent { CockpitApp(startOn = start) }
    }

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
