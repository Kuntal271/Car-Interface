package dev.kuntal.cockpit.ui.shell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.kuntal.cockpit.data.AppEntry
import dev.kuntal.cockpit.data.AppIndex
import dev.kuntal.cockpit.data.RecentApps

/** Reads the launchable apps from the package manager. The list logic itself (ordering, search) lives in [AppIndex]. */
object InstalledApps {
    fun query(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // A package manager that throws (TransactionTooLargeException with thousands of apps) or an app with a hostile label must not take the launcher down.
        val all = try {
            pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
                try { AppEntry(AppIndex.cleanLabel(ri.loadLabel(pm).toString(), fallback = ri.activityInfo.packageName), ri.activityInfo.packageName, ri.activityInfo.name) }
                catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
        return AppIndex.visible(all, own = ctx.packageName)
    }

    fun launch(ctx: Context, app: AppEntry): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    private val icons = object : LruCache<String, ImageBitmap>(96) {}      // 96 icons at 112 px is about 5 MB

    /** App icon as a 112 px bitmap (shown at 56 dp). Call off the main thread. */
    fun icon(ctx: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        // An icon that cannot be loaded or drawn (broken vector, missing resource, out of memory) is an app without an icon, not a crash.
        return try {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 112, 112); d.draw(Canvas(bmp))
            bmp.asImageBitmap().also { icons.put(pkg, it) }
        } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
    }

    private const val PREFS = "cockpit"
    private const val KEY_RECENT = "recent_apps"
    fun recents(ctx: Context): List<String> = RecentApps.parse(
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, "") ?: "" } catch (_: ClassCastException) { "" },       // a damaged file can hold another type under this key
    )
    fun remember(ctx: Context, pkg: String) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_RECENT, RecentApps.serialise(RecentApps.touch(recents(ctx), pkg))).apply()
    }
}
