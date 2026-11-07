package dev.kuntal.cockpit.ui.shell

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Starts the stock apps the dock and Home tiles point at. Vendor apps (radio, phone, camera, CarPlay / Android Auto) stay
 * untouched; this only fires an intent. Returns false when nothing on the device handles it, so the UI can say so.
 */
object AppLauncher {
    fun launch(ctx: Context, what: String): Boolean {
        val intent = when (what) {
            "maps" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
            "music", "media" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            "phone" -> Intent(Intent.ACTION_DIAL)
            "camera" -> Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            else -> return false           // radio and the unit's own apps need their package names (Settings, after the unit is inspected)
        }
        return try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: ActivityNotFoundException) { false }
    }
}
