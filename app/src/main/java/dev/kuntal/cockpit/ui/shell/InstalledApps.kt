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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Reads the launchable apps from the package manager. The list logic itself (ordering, search) lives in [AppIndex]. */
object InstalledApps {
    /** The last list read from the package manager: shown at once on the next visit to Apps while a fresh query runs behind it. */
    @Volatile var lastQuery: List<AppEntry> = emptyList()
        private set

    fun query(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(intent, 0).map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        return AppIndex.visible(all, own = ctx.packageName).also { lastQuery = it }
    }

    fun launch(ctx: Context, app: AppEntry): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    private val icons = object : LruCache<String, ImageBitmap>(96) {}      // 96 icons at 112 px is about 5 MB

    /** A cached icon, or null. Cheap enough for the main thread: a tile that scrolls back into view shows its icon in its first frame. */
    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)

    /** Icon decoding is bounded to two threads (the IO pool would otherwise start up to 64 on a fast fling through 300 apps). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val iconDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    /** App icon as a 112 px bitmap (shown at 56 dp). Call off the main thread. */
    fun icon(ctx: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        val d = try { ctx.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { return null }
        val bmp = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 112, 112); d.draw(Canvas(bmp))
        return bmp.asImageBitmap().also { icons.put(pkg, it) }
    }

    private const val PREFS = "cockpit"
    private const val KEY_RECENT = "recent_apps"
    fun recents(ctx: Context): List<String> = RecentApps.parse(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, "") ?: "")
    fun remember(ctx: Context, pkg: String) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_RECENT, RecentApps.serialise(RecentApps.touch(recents(ctx), pkg))).apply()
    }
}
