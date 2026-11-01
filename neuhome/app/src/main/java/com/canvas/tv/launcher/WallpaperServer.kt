package com.canvas.tv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class WallpaperServer(
    private val context: Context,
    port: Int = 8080,
    private val onWallpaperUploaded: (File) -> Unit,
    private val onVideoUploaded: (File) -> Unit,
    private val onDeleteFile: (String) -> Unit
) : NanoHTTPD(port) {

    private val wallpaperDir = File(context.filesDir, "wallpapers").apply {
        if (!exists()) mkdirs()
    }

    private val moviesDir: File = File(context.filesDir, "movies").apply {
        if (!exists()) mkdirs()
    }

    private fun isVideoFile(name: String): Boolean {
        val ext = File(name).extension.lowercase()
        return ext in listOf("mp4", "mkv", "avi", "mov", "mpg", "mpeg", "webm", "ts")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Handle File Delete
        if (uri.startsWith("/delete/")) {
            val fileName = uri.removePrefix("/delete/")
            val wpTarget = File(wallpaperDir, fileName)
            val movTarget = File(moviesDir, fileName)
            if (wpTarget.exists() && wpTarget.delete()) {
                onDeleteFile(fileName)
            } else if (movTarget.exists() && movTarget.delete()) {
                onDeleteFile(fileName)
            }
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                .apply { addHeader("Location", "/") }
        }

        // Handle File Uploads (Images or Videos)
        if (session.method == Method.POST) {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val params = session.parameters

                for ((paramName, tempFilePath) in files) {
                    val originalFileName = params[paramName]?.firstOrNull() ?: "file_${System.currentTimeMillis()}"
                    val isVideo = isVideoFile(originalFileName)
                    val targetDir = if (isVideo) moviesDir else wallpaperDir

                    val safeName = "${System.currentTimeMillis()}_${File(originalFileName).name}"
                    val destFile = File(targetDir, safeName)

                    val tempFile = File(tempFilePath)
                    // Free-space guard: refuse the upload instead of letting the copy fail halfway
                    if (tempFile.length() > targetDir.usableSpace) {
                        tempFile.delete()
                        return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            "text/plain",
                            "Not enough free space on TV to store $originalFileName. Free up space and try again."
                        )
                    }
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()

                    if (isVideo) {
                        onVideoUploaded(destFile)
                    } else {
                        onWallpaperUploaded(destFile)
                    }
                }

                return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                    .apply { addHeader("Location", "/") }
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Upload error: ${e.message}")
            }
        }

        // Web Dashboard UI
        val wallpapers = wallpaperDir.listFiles() ?: emptyArray()
        val movies = moviesDir.listFiles()?.filter { isVideoFile(it.name) }?.toTypedArray() ?: emptyArray()
        val freeSpaceMb = String.format("%.0f MB free on TV", context.filesDir.usableSpace / (1024.0 * 1024.0))

        fun buildFileListHtml(files: Array<File>, category: String): String {
            if (files.isEmpty()) return "<p style='color:#777; font-size:13px;'>No $category on TV.</p>"
            val sb = StringBuilder()
            for (f in files) {
                val sizeMb = String.format("%.1f MB", f.length() / (1024.0 * 1024.0))
                sb.append("""
                    <div class="file-row">
                        <div>
                            <span class="file-name">${f.name}</span>
                            <span class="file-size">$sizeMb</span>
                        </div>
                        <a href="/delete/${f.name}" class="btn-del" onclick="return confirm('Delete this file?')">Delete</a>
                    </div>
                """.trimIndent())
            }
            return sb.toString()
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Canvas TV Hub</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0D0E12; color: #f1f3f5; padding: 25px 15px; margin: 0; }
                    .container { max-width: 480px; margin: 0 auto; background: #1B1E26; padding: 24px; border-radius: 20px; box-shadow: 0 4px 30px rgba(0,0,0,0.7); }
                    h1 { font-size: 22px; color: #64B5F6; margin: 0 0 6px 0; }
                    p.sub { font-size: 13px; color: #9E9E9E; line-height: 1.4; margin-bottom: 20px; }
                    .file-box { border: 2px dashed #3D4452; border-radius: 14px; padding: 24px 10px; text-align: center; margin-bottom: 16px; background: #14171E; }
                    input[type=file] { display: block; margin: 0 auto 12px auto; color: #f1f3f5; font-size: 13px; }
                    .btn-upload { width: 100%; padding: 14px; background: #1E88E5; border: none; border-radius: 12px; color: #fff; font-size: 15px; font-weight: bold; cursor: pointer; }
                    h2 { font-size: 15px; margin: 24px 0 10px 0; color: #90CAF9; border-bottom: 1px solid #2B313E; padding-bottom: 6px; }
                    .file-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #242934; font-size: 13px; }
                    .file-name { word-break: break-all; display: block; }
                    .file-size { font-size: 11px; color: #78909C; }
                    .btn-del { color: #EF5350; text-decoration: none; font-weight: bold; font-size: 12px; padding: 4px 10px; border: 1px solid #EF5350; border-radius: 6px; margin-left: 8px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Canvas TV Hub</h1>
                    <p class="sub">Send Wallpapers (photos) or Movies (mp4, mkv, mpg) to your TV. ($freeSpaceMb)</p>
                    <form method="post" enctype="multipart/form-data">
                        <div class="file-box">
                            <input type="file" name="file" multiple required>
                        </div>
                        <button type="submit" class="btn-upload">Send to TV</button>
                    </form>
                    
                    <h2>Wallpapers (${wallpapers.size})</h2>
                    ${buildFileListHtml(wallpapers, "wallpapers")}

                    <h2>Movies & Videos (${movies.size})</h2>
                    ${buildFileListHtml(movies, "videos")}
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
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
