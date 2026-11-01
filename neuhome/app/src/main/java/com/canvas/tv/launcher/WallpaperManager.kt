package com.canvas.tv.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

class WallpaperManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("canvas_tv_launcher_prefs", Context.MODE_PRIVATE)
    
    val internalWallpaperDir = File(context.filesDir, "wallpapers").apply {
        if (!exists()) mkdirs()
    }

    init {
        ensureStarterWallpapers()
    }

    private fun ensureStarterWallpapers() {
        // Seed a few original gradient "starter" wallpapers so a fresh install
        // is not empty and rotation works out of the box.
        if (internalWallpaperDir.listFiles()?.any { isImageFile(it) } == true) return
        val starters = listOf(
            R.drawable.car,
            R.drawable.nature1,
            R.drawable.nature2
        )
        starters.forEachIndexed { index, resId ->
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId) ?: return@forEachIndexed
            val width = 1920
            val height = 1080
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            val out = File(internalWallpaperDir, "starter_${index + 1}.jpg")
            java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
        }
    }

    private fun isImageFile(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp")
    }

    fun getRotationIntervalSeconds(): Int {
        // Default to 15s ambient rotation so a fresh install shows the starter wallpapers.
        // 0 = disabled, 15, 30, 60 (1m), 900 (15m), 3600 (1h), 86400 (24h)
        return prefs.getInt("rotation_interval_seconds", 15)
    }

    fun setRotationIntervalSeconds(seconds: Int) {
        prefs.edit().putInt("rotation_interval_seconds", seconds).apply()
    }

    fun getVisibleAppCount(): Int {
        return prefs.getInt("visible_app_count", 1)
    }

    fun setVisibleAppCount(count: Int) {
        prefs.edit().putInt("visible_app_count", count).apply()
    }

    fun getDimLevel(): Float {
        return prefs.getFloat("dim_level", 0.20f)
    }

    fun setDimLevel(level: Float) {
        prefs.edit().putFloat("dim_level", level).apply()
    }

    fun isKeepScreenOnEnabled(): Boolean {
        // Opt-in ambient canvas: hold the display while Home is visible
        return prefs.getBoolean("keep_screen_on", false)
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun getPinnedPackages(): List<String> {
        val stored = prefs.getString("dock_pinned_packages", null)
        return stored?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
    }

    fun setPinnedPackages(packages: List<String>) {
        prefs.edit().putString("dock_pinned_packages", packages.joinToString(",")).apply()
    }

    fun getHiddenPackages(): Set<String> {
        val stored = prefs.getString("hidden_packages", null)
        return stored?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() }?.toSet() ?: emptySet()
    }

    fun setHiddenPackages(packages: Collection<String>) {
        prefs.edit().putString("hidden_packages", packages.joinToString(",")).apply()
    }

    fun getAppOrder(): List<String> {
        val stored = prefs.getString("app_order", null)
        return stored?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
    }

    fun setAppOrder(packages: List<String>) {
        prefs.edit().putString("app_order", packages.joinToString(",")).apply()
    }

    fun getAllWallpapers(): List<File> {
        val list = mutableListOf<File>()

        // 1. Files from app storage (uploaded from phone)
        internalWallpaperDir.listFiles { f -> isImageFile(f) }?.let { list.addAll(it) }

        // 2. Fallback: check device Pictures folder
        val extPictures = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Wallpapers")
        if (extPictures.exists() && extPictures.isDirectory) {
            extPictures.listFiles { f -> isImageFile(f) }?.let { list.addAll(it) }
        }

        return list.distinctBy { it.absolutePath }
    }

    fun getCurrentWallpaper(): File? {
        val path = prefs.getString("current_wallpaper_path", null)
        if (path != null) {
            val file = File(path)
            if (file.exists()) return file
        }
        return getAllWallpapers().firstOrNull()
    }

    fun getNextWallpaper(): File? {
        val all = getAllWallpapers()
        if (all.isEmpty()) return null

        val current = getCurrentWallpaper()
        val currentIndex = all.indexOfFirst { it.absolutePath == current?.absolutePath }
        val nextIndex = if (currentIndex == -1 || currentIndex >= all.size - 1) 0 else currentIndex + 1
        val nextFile = all[nextIndex]

        prefs.edit().putString("current_wallpaper_path", nextFile.absolutePath).apply()
        return nextFile
    }

    fun getPreviousWallpaper(): File? {
        val all = getAllWallpapers()
        if (all.isEmpty()) return null

        val current = getCurrentWallpaper()
        val currentIndex = all.indexOfFirst { it.absolutePath == current?.absolutePath }
        val prevIndex = if (currentIndex <= 0) all.size - 1 else currentIndex - 1
        val prevFile = all[prevIndex]

        prefs.edit().putString("current_wallpaper_path", prevFile.absolutePath).apply()
        return prevFile
    }

    fun setWallpaper(file: File) {
        prefs.edit().putString("current_wallpaper_path", file.absolutePath).apply()
    }

    fun deleteWallpaper(file: File): Boolean {
        val success = file.delete()
        if (file.absolutePath == prefs.getString("current_wallpaper_path", null)) {
            val remaining = getAllWallpapers()
            if (remaining.isNotEmpty()) {
                setWallpaper(remaining.first())
            } else {
                prefs.edit().remove("current_wallpaper_path").apply()
            }
        }
        return success
    }
}
