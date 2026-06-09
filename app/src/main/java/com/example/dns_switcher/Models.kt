package com.example.dns_switcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?
)

fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

    resolveInfos
        .map { ri ->
            AppInfo(
                name = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = try { ri.loadIcon(pm)?.toImageBitmap() } catch (_: Exception) { null }
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}
