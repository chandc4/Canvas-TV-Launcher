package com.canvas.tv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRepository {

    suspend fun getInstalledTvApps(context: Context): List<AppModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPackage = context.packageName

        // 1. Query Leanback Launcher apps
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackApps = pm.queryIntentActivities(leanbackIntent, 0)

        // 2. Query Standard Launcher apps as fallback
        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val standardApps = pm.queryIntentActivities(standardIntent, 0)

        val seenPackages = mutableSetOf<String>()
        val result = mutableListOf<AppModel>()

        fun processInfo(info: ResolveInfo) {
            val pkg = info.activityInfo.packageName
            if (pkg == selfPackage || seenPackages.contains(pkg)) {
                return
            }
            seenPackages.add(pkg)

            val label = info.loadLabel(pm).toString()
            val banner = info.activityInfo.loadBanner(pm)
            val icon = info.activityInfo.loadIcon(pm)
            val activityName = info.activityInfo.name

            result.add(
                AppModel(
                    label = label,
                    packageName = pkg,
                    activityName = activityName,
                    banner = banner,
                    icon = icon
                )
            )
        }

        leanbackApps.forEach { processInfo(it) }
        standardApps.forEach { processInfo(it) }

        result.sortBy { it.label.lowercase() }
        result
    }
}
