package com.canvas.tv.launcher

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File

object MediaStorageHelper {
    private const val TAG = "MediaStorageHelper"

    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "mpg", "mpeg", "webm", "ts")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun isVideoFile(fileName: String): Boolean {
        val ext = File(fileName).extension.lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun isImageFile(fileName: String): Boolean {
        val ext = File(fileName).extension.lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun isApkFile(fileName: String): Boolean {
        return File(fileName).extension.lowercase() == "apk"
    }

    /**
     * Resolves public Pictures directory (/sdcard/Pictures/Wallpapers) on primary/SanDisk storage.
     * Auto-creates the folder if missing. Falls back to internal storage if unmounted.
     */
    fun getTargetWallpapersDir(context: Context): File {
        try {
            val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val wallpapersDir = File(publicPictures, "Wallpapers")
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs()
            if (wallpapersDir.canWrite()) {
                return wallpapersDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access public Pictures directory, falling back", e)
        }

        return File(context.filesDir, "wallpapers").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves public Movies directory (/sdcard/Movies) on primary/SanDisk storage.
     * Auto-creates the folder if missing.
     */
    fun getTargetMoviesDir(context: Context): File {
        try {
            val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!publicMovies.exists()) publicMovies.mkdirs()
            if (publicMovies.canWrite()) {
                return publicMovies
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access public Movies directory, falling back", e)
        }

        return File(context.filesDir, "movies").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves public Download directory (/sdcard/Download) on primary/SanDisk storage.
     * Auto-creates the folder if missing.
     */
    fun getApkDir(context: Context): File {
        try {
            val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!publicDownload.exists()) publicDownload.mkdirs()
            if (publicDownload.canWrite()) {
                return publicDownload
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access public Download directory, falling back", e)
        }

        return File(context.filesDir, "apks").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Returns all wallpaper image files across public /sdcard/Pictures/Wallpapers,
     * /sdcard/Pictures, /sdcard/Images, and internal legacy folders.
     */
    fun getAllWallpaperFiles(context: Context): List<File> {
        val result = LinkedHashMap<String, File>()
        val searchDirs = mutableListOf<File>()

        try {
            val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            searchDirs.add(File(publicPictures, "Wallpapers"))
            searchDirs.add(publicPictures)
        } catch (ignored: Exception) {}

        // Custom /sdcard/Images check
        try {
            val imagesDir = File(Environment.getExternalStorageDirectory(), "Images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                searchDirs.add(imagesDir)
            }
        } catch (ignored: Exception) {}

        // Internal app private storage
        searchDirs.add(File(context.filesDir, "wallpapers"))

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { f ->
                    f.isFile && isImageFile(f.name)
                }?.forEach { file ->
                    if (!result.containsKey(file.name)) {
                        result[file.name] = file
                    }
                }
            }
        }

        return result.values.sortedByDescending { it.lastModified() }
    }

    /**
     * Returns all movie files across public /sdcard/Movies, /sdcard/Download,
     * adopted storage, and legacy internal folders.
     */
    fun getAllMovieFiles(context: Context): List<File> {
        val result = LinkedHashMap<String, File>()
        val searchDirs = mutableListOf<File>()

        try {
            val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (publicMovies.exists() && publicMovies.isDirectory) {
                searchDirs.add(publicMovies)
            }
        } catch (ignored: Exception) {}

        try {
            val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownload.exists() && publicDownload.isDirectory) {
                searchDirs.add(publicDownload)
            }
        } catch (ignored: Exception) {}

        try {
            val externalDirs = context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)
            externalDirs.filterNotNull().forEach { dir ->
                if (Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED && dir.exists()) {
                    searchDirs.add(dir)
                }
            }
        } catch (ignored: Exception) {}

        searchDirs.add(File(context.filesDir, "movies"))

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { f ->
                    f.isFile && isVideoFile(f.name)
                }?.forEach { file ->
                    if (!result.containsKey(file.name)) {
                        result[file.name] = file
                    }
                }
            }
        }

        return result.values.sortedByDescending { it.lastModified() }
    }

    /**
     * Returns all APK files across public /sdcard/Download, /sdcard/Apps,
     * and internal folders.
     */
    fun getAllApkFiles(context: Context): List<File> {
        val result = LinkedHashMap<String, File>()
        val searchDirs = mutableListOf<File>()

        try {
            val publicDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDownload.exists() && publicDownload.isDirectory) {
                searchDirs.add(publicDownload)
            }
        } catch (ignored: Exception) {}

        try {
            val appsDir = File(Environment.getExternalStorageDirectory(), "Apps")
            if (appsDir.exists() && appsDir.isDirectory) {
                searchDirs.add(appsDir)
            }
        } catch (ignored: Exception) {}

        searchDirs.add(File(context.filesDir, "apks"))

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { f ->
                    f.isFile && isApkFile(f.name)
                }?.forEach { file ->
                    if (!result.containsKey(file.name)) {
                        result[file.name] = file
                    }
                }
            }
        }

        return result.values.sortedByDescending { it.lastModified() }
    }

    fun promptInstallApk(context: Context, apkFile: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer for ${apkFile.name}", e)
        }
    }

    fun notifyMediaScanner(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Scanned $path -> URI: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner notification failed for ${file.name}", e)
        }
    }
}
