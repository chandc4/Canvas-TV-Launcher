package com.canvas.tv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class WallpaperServer(
    private val context: Context,
    private val serverPort: Int = 8080,
    private val onWallpaperUploaded: (File) -> Unit,
    private val onVideoUploaded: (File) -> Unit,
    private val onDeleteFile: (String) -> Unit
) : NanoHTTPD(serverPort) {

    private val wallpaperDir = MediaStorageHelper.getTargetWallpapersDir(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // --- Install APK Trigger ---
        if (uri.startsWith("/install/")) {
            val fileName = uri.removePrefix("/install/")
            val apkFile = File(MediaStorageHelper.getApkDir(context), fileName)
            if (apkFile.exists()) {
                MediaStorageHelper.promptInstallApk(context, apkFile)
            }
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                .apply { addHeader("Location", "/") }
        }

        // --- File Delete ---
        if (uri.startsWith("/delete/")) {
            val fileName = uri.removePrefix("/delete/")
            val allWallpapers = MediaStorageHelper.getAllWallpaperFiles(context)
            val wpTarget = allWallpapers.firstOrNull { it.name == fileName }
            val apkTarget = File(MediaStorageHelper.getApkDir(context), fileName)

            if (wpTarget != null && wpTarget.exists() && wpTarget.delete()) {
                MediaStorageHelper.notifyMediaScanner(context, wpTarget)
                onDeleteFile(fileName)
            } else if (apkTarget.exists() && apkTarget.delete()) {
                MediaStorageHelper.notifyMediaScanner(context, apkTarget)
                onDeleteFile(fileName)
            } else {
                val allMovies = MediaStorageHelper.getAllMovieFiles(context)
                val movTarget = allMovies.firstOrNull { it.name == fileName }
                if (movTarget != null && movTarget.exists() && movTarget.delete()) {
                    MediaStorageHelper.notifyMediaScanner(context, movTarget)
                    onDeleteFile(fileName)
                }
            }
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                .apply { addHeader("Location", "/") }
        }

        // --- File Uploads (Images, Videos, APKs) ---
        if (method == Method.POST) {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val formParams = session.parameters

                for ((paramName, tempFilePath) in files) {
                    val originalFileName = formParams[paramName]?.firstOrNull() ?: "file_${System.currentTimeMillis()}"
                    val isVideo = MediaStorageHelper.isVideoFile(originalFileName)
                    val isApk = MediaStorageHelper.isApkFile(originalFileName)
                    val targetDir = when {
                        isApk -> MediaStorageHelper.getApkDir(context)
                        isVideo -> MediaStorageHelper.getTargetMoviesDir(context)
                        else -> wallpaperDir
                    }

                    val safeName = "${System.currentTimeMillis()}_${File(originalFileName).name}"
                    val destFile = File(targetDir, safeName)

                    val tempFile = File(tempFilePath)
                    if (tempFile.length() > targetDir.usableSpace) {
                        tempFile.delete()
                        return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            "text/plain",
                            "Not enough free space on storage to store $originalFileName."
                        )
                    }
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()

                    if (isApk) {
                        MediaStorageHelper.notifyMediaScanner(context, destFile)
                        MediaStorageHelper.promptInstallApk(context, destFile)
                    } else if (isVideo) {
                        MediaStorageHelper.notifyMediaScanner(context, destFile)
                        onVideoUploaded(destFile)
                    } else {
                        MediaStorageHelper.notifyMediaScanner(context, destFile)
                        onWallpaperUploaded(destFile)
                    }
                }

                return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                    .apply { addHeader("Location", "/") }
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Upload error: ${e.message}")
            }
        }

        // --- Web Dashboard UI ---
        val wallpapers = MediaStorageHelper.getAllWallpaperFiles(context)
        val movies = MediaStorageHelper.getAllMovieFiles(context)
        val apks = MediaStorageHelper.getAllApkFiles(context)
        val freeSpaceMb = String.format("%.0f MB free", MediaStorageHelper.getTargetMoviesDir(context).usableSpace / (1024.0 * 1024.0))

        fun buildFileListHtml(files: Collection<File>, category: String, allowInstall: Boolean = false): String {
            if (files.isEmpty()) return "<p style='color:#616161; font-size:13px; margin: 6px 0;'>No $category found.</p>"
            val sb = StringBuilder()
            for (f in files) {
                val sizeMb = String.format("%.1f MB", f.length() / (1024.0 * 1024.0))
                val installBtn = if (allowInstall) """<a href="/install/${f.name}" class="btn-install">Install</a>""" else ""
                sb.append("""
                    <div class="file-row">
                        <div style="flex:1; overflow:hidden; padding-right:10px;">
                            <span class="file-name">${f.name}</span>
                            <span class="file-size">$sizeMb</span>
                        </div>
                        <div style="display:flex; align-items:center; gap:6px;">
                            $installBtn
                            <a href="/delete/${f.name}" class="btn-del" onclick="return confirm('Delete this file?')">Delete</a>
                        </div>
                    </div>
                """.trimIndent())
            }
            return sb.toString()
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
                <title>Canvas TV Hub</title>
                <style>
                    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0A0C10; color: #F1F3F5; padding: 20px 14px; margin: 0; }
                    .container { max-width: 460px; margin: 0 auto; background: #151821; padding: 22px; border-radius: 24px; box-shadow: 0 8px 32px rgba(0,0,0,0.8); border: 1px solid #232838; }
                    .header-title { font-size: 20px; font-weight: 800; color: #64B5F6; margin: 0 0 4px 0; display: flex; justify-content: space-between; align-items: center; }
                    .header-sub { font-size: 13px; color: #9E9E9E; margin-bottom: 20px; }
                    .file-box { border: 2px dashed #3D4452; border-radius: 16px; padding: 26px 12px; text-align: center; margin-bottom: 14px; background: #11141B; }
                    input[type=file] { display: block; margin: 0 auto 12px auto; color: #f1f3f5; font-size: 13px; width: 100%; max-width: 240px; }
                    .btn-upload { width: 100%; padding: 14px; background: #1E88E5; border: none; border-radius: 12px; color: #fff; font-size: 15px; font-weight: bold; cursor: pointer; transition: background 0.2s; }
                    .btn-upload:active { background: #1976D2; }
                    .section-title { font-size: 13px; font-weight: 700; color: #90CAF9; text-transform: uppercase; letter-spacing: 0.5px; margin: 24px 0 8px 0; border-bottom: 1px solid #202634; padding-bottom: 6px; }
                    .file-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #1C212E; font-size: 13px; }
                    .file-name { word-break: break-all; display: block; color: #E0E0E0; line-height: 1.3; }
                    .file-size { font-size: 11px; color: #78909C; }
                    .btn-del { color: #EF5350; text-decoration: none; font-weight: bold; font-size: 12px; padding: 5px 12px; border: 1px solid #EF5350; border-radius: 8px; white-space: nowrap; }
                    .btn-install { color: #66BB6A; text-decoration: none; font-weight: bold; font-size: 12px; padding: 5px 12px; border: 1px solid #66BB6A; border-radius: 8px; white-space: nowrap; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header-title">
                        <span>Canvas TV Hub</span>
                        <span style="font-size:11px; font-weight: normal; color:#66BB6A;">● Connected</span>
                    </div>
                    <div class="header-sub">Send Wallpapers, Movies, or APKs (e.g. SmartTube) to install on TV. ($freeSpaceMb)</div>

                    <form method="post" enctype="multipart/form-data">
                        <div class="file-box">
                            <input type="file" name="file" multiple required>
                        </div>
                        <button type="submit" class="btn-upload">Send to TV</button>
                    </form>

                    <div class="section-title">Wallpapers (${wallpapers.size})</div>
                    ${buildFileListHtml(wallpapers.toList(), "wallpapers")}

                    <div class="section-title">Movies & Videos (${movies.size})</div>
                    ${buildFileListHtml(movies, "videos")}

                    <div class="section-title">Apps / APKs (${apks.size})</div>
                    ${buildFileListHtml(apks, "apps", allowInstall = true)}
                </div>
            </body>
            </html>
        """.trimIndent()

        val response = newFixedLengthResponse(Response.Status.OK, "text/html", html)
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
        return response
    }

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val addrs = intf.inetAddresses
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (ignored: Exception) {}
            return null
        }

        fun generateQrCodeBitmap(text: String, size: Int = 240): Bitmap? {
            return try {
                val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
}
