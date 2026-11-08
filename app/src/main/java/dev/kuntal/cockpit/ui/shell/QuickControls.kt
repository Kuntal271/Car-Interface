package dev.kuntal.cockpit.ui.shell

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.kuntal.cockpit.DebugHooks

/** Window brightness for this app (no WRITE_SETTINGS needed): 5..100 %. Starts from the system level. */
class Brightness(private val activity: Activity?, initial: Int) {
    var pct by mutableIntStateOf(initial.coerceIn(5, 100)); private set
    fun set(p: Int) {
        pct = p.coerceIn(5, 100)
        if (DebugHooks.snapshot) return
        activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = pct / 100f } }
    }
}

@Composable
fun rememberBrightness(): Brightness {
    val ctx = LocalContext.current
    return remember {
        var a: Context = ctx; while (a is ContextWrapper && a !is Activity) a = a.baseContext
        val sys = try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 178) * 100 / 255 } catch (_: Throwable) { 70 }
        Brightness(a as? Activity, if (DebugHooks.snapshot) 70 else sys)
    }
}

/** What the quick tiles can truly know and do from a launcher: read Wi-Fi / Bluetooth, and open the system pages that change them. */
class QuickControls(private val ctx: Context) {
    fun wifiOn(): Boolean = DebugHooks.snapshot || try { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (_: Throwable) { false }
    fun btOn(): Boolean = DebugHooks.snapshot || try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (_: Throwable) { false }
    fun openWifi() = start(Settings.ACTION_WIFI_SETTINGS)
    fun openBluetooth() = start(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openHotspot() = start("android.settings.TETHER_SETTINGS") || start(Settings.ACTION_WIRELESS_SETTINGS)
    private fun start(action: String): Boolean = try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Throwable) { false }
}

@Composable
fun rememberQuickControls(): QuickControls { val ctx = LocalContext.current; return remember { QuickControls(ctx) } }
