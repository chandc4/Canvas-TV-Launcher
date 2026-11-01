package com.canvas.tv.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.size.Scale
import com.canvas.tv.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wallpaperManager: WallpaperManager
    private var wallpaperServer: WallpaperServer? = null

    private lateinit var appAdapter: MinimalAppAdapter
    private lateinit var thumbAdapter: WallpaperThumbAdapter
    private lateinit var movieAdapter: MovieAdapter
    private lateinit var allAppsAdapter: AppAdapter
    private var isEditMode: Boolean = false
    private var allInstalledApps: List<AppModel> = emptyList()
    private var installedApps: List<AppModel> = emptyList()
    private var dockApps: List<AppModel> = emptyList()
    private lateinit var appManageAdapter: AppManageAdapter

    private val rotationHandler = Handler(Looper.getMainLooper())
    private val rotationRunnable = object : Runnable {
        override fun run() {
            val intervalSec = wallpaperManager.getRotationIntervalSeconds()
            if (intervalSec > 0) {
                // Skip crossfade/decoding while the display is off (idle standby / dream state)
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isInteractive) return@run
                cycleNextWallpaperSmoothly()
                rotationHandler.postDelayed(this, intervalSec * 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wallpaperManager = WallpaperManager(this)

        setupAppDock()
        setupWallpaperGallery()
        setupMoviesShelf()
        setupAllApps()
        setupAppManager()
        setupListeners()
        loadInitialWallpaper()
        applyDimLevel(wallpaperManager.getDimLevel())
        applyDockStyle(wallpaperManager.getVisibleAppCount())
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        // Ambient Canvas opt-in: hold the display while Home is visible (idle standby fix)
        if (wallpaperManager.isKeepScreenOnEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Resume rotation only when home launcher is visible
        startWallpaperRotationTimer()
    }

    override fun onPause() {
        super.onPause()
        // FREEZE rotation when launching another app (VLC, YouTube) - 0% CPU/RAM
        stopWallpaperRotationTimer()
        // Release keep-awake when leaving Home so media apps manage their own screen on
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupAppDock() {
        appAdapter = MinimalAppAdapter(
            apps = emptyList(),
            onAppFocused = { _ -> },
            onAppClick = { app -> launchApp(app) }
        )
        binding.appDockRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.appDockRecyclerView.adapter = appAdapter
    }

    private fun setupMoviesShelf() {
        movieAdapter = MovieAdapter(
            movies = emptyList(),
            onMovieClick = { file ->
                playMovieInVlc(file)
            },
            onMovieLongClick = { file ->
                confirmDeleteMovie(file)
            }
        )
        binding.moviesRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.moviesRecyclerView.adapter = movieAdapter
    }

    private fun setupAllApps() {
        allAppsAdapter = AppAdapter(
            apps = emptyList(),
            onAppClick = { app -> handleOverlayAppClick(app) },
            onAppLongClick = { app -> showAppContextMenu(app) },
            onAppFocused = { app -> binding.allAppsHeader.text = app.label }
        )
        binding.allAppsRecyclerView.layoutManager = GridLayoutManager(this, 4)
        binding.allAppsRecyclerView.adapter = allAppsAdapter
    }

    private fun setupAppManager() {
        appManageAdapter = AppManageAdapter(
            apps = emptyList(),
            hidden = wallpaperManager.getHiddenPackages(),
            onHiddenChanged = { app, hidden ->
                val current = wallpaperManager.getHiddenPackages().toMutableSet()
                if (hidden) current.add(app.packageName) else current.remove(app.packageName)
                wallpaperManager.setHiddenPackages(current)
                applyAppPreferencesAndRefresh()
            },
            onMoveUp = { pos -> moveManagedApp(pos, -1) },
            onMoveDown = { pos -> moveManagedApp(pos, 1) }
        )
        binding.appsManageRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsManageRecyclerView.adapter = appManageAdapter
    }

    private fun refreshDockApps() {
        val pinned = wallpaperManager.getPinnedPackages().toSet()
        val base = if (pinned.isEmpty()) installedApps else installedApps.filter { it.packageName in pinned }
        val count = wallpaperManager.getVisibleAppCount()
        dockApps = if (count <= 0) emptyList() else base.take(count)
        appAdapter.updateApps(dockApps)
        applyDockStyle(count)
    }

    private fun syncDockCountLabel() {
        binding.txtDockCount.text = wallpaperManager.getVisibleAppCount().toString()
    }

    private fun playMovieInVlc(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No video player (VLC/Nova) installed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDeleteMovie(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Movie")
            .setMessage("Delete video file from TV?\n${file.name}")
            .setPositiveButton("Delete") { _, _ ->
                file.delete()
                refreshMoviesShelf()
                Toast.makeText(this, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshMoviesShelf() {
        val moviesDir = File(filesDir, "movies")
        val moviesList = moviesDir.listFiles()?.filter { f ->
            f.isFile && f.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "mpg", "mpeg", "webm", "ts")
        } ?: emptyList()

        movieAdapter.updateMovies(moviesList)
        if (moviesList.isEmpty()) {
            binding.noMoviesNotice.visibility = View.VISIBLE
            binding.moviesRecyclerView.visibility = View.GONE
        } else {
            binding.noMoviesNotice.visibility = View.GONE
            binding.moviesRecyclerView.visibility = View.VISIBLE
        }
    }
    private fun setupWallpaperGallery() {
        thumbAdapter = WallpaperThumbAdapter(
            wallpapers = emptyList(),
            onWallpaperClick = { file ->
                wallpaperManager.setWallpaper(file)
                transitionToWallpaper(file)
                Toast.makeText(this, "Wallpaper applied: ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onWallpaperLongClick = { file ->
                confirmDeleteWallpaper(file)
            }
        )
        binding.wallpapersRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.wallpapersRecyclerView.adapter = thumbAdapter
    }

    private fun confirmDeleteWallpaper(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Wallpaper")
            .setMessage("Do you want to delete this wallpaper?\n${file.name}")
            .setPositiveButton("Delete") { _, _ ->
                wallpaperManager.deleteWallpaper(file)
                refreshWallpaperGallery()
                loadInitialWallpaper()
                Toast.makeText(this, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshWallpaperGallery() {
        val list = wallpaperManager.getAllWallpapers()
        thumbAdapter.updateWallpapers(list)
        if (list.isEmpty()) {
            binding.noWallpapersNotice.visibility = View.VISIBLE
            binding.wallpapersRecyclerView.visibility = View.GONE
        } else {
            binding.noWallpapersNotice.visibility = View.GONE
            binding.wallpapersRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun applyDockStyle(visibleCount: Int) {
        if (visibleCount <= 0) {
            binding.bottomDockContainer.visibility = View.GONE
        } else {
            binding.bottomDockContainer.visibility = View.VISIBLE
            // Let the RecyclerView wrap its contents so scaled/focused items are not clipped
            val layoutParams = binding.appDockRecyclerView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.appDockRecyclerView.layoutParams = layoutParams
        }
    }

    private fun applyDimLevel(level: Float) {
        val alphaInt = (level * 255).toInt().coerceIn(0, 255)
        binding.scrimOverlay.setBackgroundColor(Color.argb(alphaInt, 0, 0, 0))
    }

    private fun setupListeners() {
        binding.btnLauncherSettings.setOnClickListener {
            openSettings()
        }

        binding.keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            wallpaperManager.setKeepScreenOnEnabled(isChecked)
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        binding.btnSystemSettings.setOnClickListener {
            openTvSystemSettings()
        }

        binding.btnSetDefaultLauncher.setOnClickListener {
            openDefaultHomeChooser()
        }

        binding.btnDeleteCurrentWallpaper.setOnClickListener {
            val current = wallpaperManager.getCurrentWallpaper()
            if (current != null) {
                confirmDeleteWallpaper(current)
            } else {
                Toast.makeText(this, "No active wallpaper to delete", Toast.LENGTH_SHORT).show()
            }
        }

        // Settings sidebar navigation
        binding.navWallpapers.setOnClickListener { showSettingsPage("wallpapers") }
        binding.navMovies.setOnClickListener { showSettingsPage("movies") }
        binding.navAmbient.setOnClickListener { showSettingsPage("ambient") }
        binding.navDock.setOnClickListener { showSettingsPage("dock") }
        binding.navTransfer.setOnClickListener { showSettingsPage("transfer") }
        binding.navApps.setOnClickListener { showSettingsPage("apps") }

        // Dock Size Counter (0 = hidden, 1-10 = number of dock apps)
        binding.btnDockDecrease.setOnClickListener {
            val current = wallpaperManager.getVisibleAppCount()
            val next = (current - 1).coerceIn(0, 10)
            wallpaperManager.setVisibleAppCount(next)
            refreshDockApps()
            syncDockCountLabel()
            Toast.makeText(this, "Dock size: $next", Toast.LENGTH_SHORT).show()
        }
        binding.btnDockIncrease.setOnClickListener {
            val current = wallpaperManager.getVisibleAppCount()
            val next = (current + 1).coerceIn(0, 10)
            wallpaperManager.setVisibleAppCount(next)
            refreshDockApps()
            syncDockCountLabel()
            Toast.makeText(this, "Dock size: $next", Toast.LENGTH_SHORT).show()
        }

        // Dim Scrim Toggles
        binding.btnDimClear.setOnClickListener {
            wallpaperManager.setDimLevel(0.10f)
            applyDimLevel(0.10f)
            Toast.makeText(this, "Wallpaper: Bright & Clear", Toast.LENGTH_SHORT).show()
        }
        binding.btnDimDark.setOnClickListener {
            wallpaperManager.setDimLevel(0.40f)
            applyDimLevel(0.40f)
            Toast.makeText(this, "Wallpaper: Dimmed for Contrast", Toast.LENGTH_SHORT).show()
        }

        // Wallpaper Rotation Toggles (15s, 30s, 1m, 1h, Off)
        binding.btnRotOff.setOnClickListener {
            wallpaperManager.setRotationIntervalSeconds(0)
            startWallpaperRotationTimer()
            Toast.makeText(this, "Rotation: OFF", Toast.LENGTH_SHORT).show()
        }
        binding.btnRot15s.setOnClickListener {
            wallpaperManager.setRotationIntervalSeconds(15)
            startWallpaperRotationTimer()
            Toast.makeText(this, "Ambient Mode: Every 15 Seconds", Toast.LENGTH_SHORT).show()
        }
        binding.btnRot30s.setOnClickListener {
            wallpaperManager.setRotationIntervalSeconds(30)
            startWallpaperRotationTimer()
            Toast.makeText(this, "Ambient Mode: Every 30 Seconds", Toast.LENGTH_SHORT).show()
        }
        binding.btnRot1m.setOnClickListener {
            wallpaperManager.setRotationIntervalSeconds(60)
            startWallpaperRotationTimer()
            Toast.makeText(this, "Rotation: Every 1 Minute", Toast.LENGTH_SHORT).show()
        }
        binding.btnRot1h.setOnClickListener {
            wallpaperManager.setRotationIntervalSeconds(3600)
            startWallpaperRotationTimer()
            Toast.makeText(this, "Rotation: Every 1 Hour", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTvSystemSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open system settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDefaultHomeChooser() {
        try {
            // Android 11 standard Home App settings intent
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to General Apps Settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Please select Home App in TV Settings -> Apps", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startWallpaperRotationTimer() {
        rotationHandler.removeCallbacks(rotationRunnable)
        val intervalSec = wallpaperManager.getRotationIntervalSeconds()
        if (intervalSec > 0) {
            rotationHandler.postDelayed(rotationRunnable, intervalSec * 1000L)
        }
    }

    private fun stopWallpaperRotationTimer() {
        rotationHandler.removeCallbacks(rotationRunnable)
    }

    private var activeLayerIsA = true

    private fun loadInitialWallpaper() {
        val wp = wallpaperManager.getCurrentWallpaper()
        binding.wallpaperImageViewA.alpha = 1f
        binding.wallpaperImageViewB.alpha = 0f

        if (wp != null && wp.exists()) {
            binding.wallpaperImageViewA.load(wp) {
                scale(Scale.FILL)
            }
        } else {
            // Direct native drawable fallback - renders immediately without asynchronous delay
            binding.wallpaperImageViewA.setImageResource(R.drawable.default_wallpaper)
        }
        activeLayerIsA = true
    }

    // Double-Buffered Smooth Hardware Crossfade Transition
    private fun transitionToWallpaper(file: File) {
        val incomingView = if (activeLayerIsA) binding.wallpaperImageViewB else binding.wallpaperImageViewA
        val outgoingView = if (activeLayerIsA) binding.wallpaperImageViewA else binding.wallpaperImageViewB

        incomingView.animate().cancel()
        outgoingView.animate().cancel()

        // 1. Pre-load the bitmap into RAM FIRST before touching alphas
        val imageLoader = coil.Coil.imageLoader(this)
        val request = coil.request.ImageRequest.Builder(this)
            .data(file)
            .scale(Scale.FILL)
            .target { drawable ->
                // 2. Both bitmaps are now ready in memory with zero decode latency
                incomingView.setImageDrawable(drawable)
                incomingView.alpha = 0f
                outgoingView.alpha = 1f
                incomingView.bringToFront()
                binding.scrimOverlay.bringToFront()
                binding.topBar.bringToFront()
                binding.bottomDockContainer.bringToFront()
                binding.allAppsOverlay.bringToFront()
                binding.settingsOverlay.bringToFront()

                // 3. Dissolve incoming ON TOP of outgoing without black dip
                incomingView.animate()
                    .alpha(1f)
                    .setDuration(1000)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            outgoingView.alpha = 0f
                            outgoingView.setImageDrawable(null) // Release memory
                            activeLayerIsA = !activeLayerIsA
                        }
                    })
                    .start()
            }
            .build()

        imageLoader.enqueue(request)
    }

    private fun cycleNextWallpaperSmoothly() {
        val nextWp = wallpaperManager.getNextWallpaper()
        if (nextWp != null) {
            transitionToWallpaper(nextWp)
        }
    }

    private fun applyAppPreferences(apps: List<AppModel>): List<AppModel> {
        val hidden = wallpaperManager.getHiddenPackages()
        val order = wallpaperManager.getAppOrder()
        val indexMap = order.mapIndexed { i, pkg -> pkg to i }.toMap()
        val visible = apps.filter { it.packageName !in hidden }
        return visible.sortedWith(compareBy<AppModel> { indexMap[it.packageName] ?: Int.MAX_VALUE })
    }

    private fun applyAppPreferencesAndRefresh() {
        installedApps = applyAppPreferences(allInstalledApps)
        refreshDockApps()
        allAppsAdapter.updateApps(installedApps)
        appManageAdapter.update(allInstalledApps, wallpaperManager.getHiddenPackages())
    }

    private fun moveVisibleApp(fromVis: Int, toVis: Int) {
        if (fromVis < 0 || toVis < 0 || fromVis >= installedApps.size || toVis >= installedApps.size) return
        val hidden = wallpaperManager.getHiddenPackages()
        val visibleIndices = allInstalledApps.indices.filter { allInstalledApps[it].packageName !in hidden }
        if (fromVis >= visibleIndices.size || toVis >= visibleIndices.size) return
        val fromFull = visibleIndices[fromVis]
        val toFull = visibleIndices[toVis]
        val list = allInstalledApps.toMutableList()
        val item = list.removeAt(fromFull)
        list.add(toFull.coerceIn(0, list.size), item)
        allInstalledApps = list
        wallpaperManager.setAppOrder(list.map { it.packageName })
        applyAppPreferencesAndRefresh()
    }

    private fun moveManagedApp(pos: Int, delta: Int) {
        val current = appManageAdapter.currentApps().toMutableList()
        val newPos = pos + delta
        if (pos < 0 || newPos < 0 || pos >= current.size || newPos >= current.size) return
        val tmp = current[pos]
        current[pos] = current[newPos]
        current[newPos] = tmp
        wallpaperManager.setAppOrder(current.map { it.packageName })
        appManageAdapter.update(current, wallpaperManager.getHiddenPackages())
        applyAppPreferencesAndRefresh()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val apps = AppRepository.getInstalledTvApps(this@MainActivity)
            allInstalledApps = apps
            // Persist initial order if none saved yet
            if (wallpaperManager.getAppOrder().isEmpty()) {
                wallpaperManager.setAppOrder(apps.map { it.packageName })
            }
            applyAppPreferencesAndRefresh()

            if (dockApps.isNotEmpty() && wallpaperManager.getVisibleAppCount() > 0) {
                binding.appDockRecyclerView.post {
                    val firstHolder = binding.appDockRecyclerView.findViewHolderForAdapterPosition(0)
                    firstHolder?.itemView?.requestFocus()
                }
            } else {
                binding.btnLauncherSettings.requestFocus()
            }
        }
    }

    private fun launchApp(app: AppModel) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open ${app.label}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppFromOverlay(app: AppModel) {
        // Pin the launched app so it becomes part of the dock
        val pinned = wallpaperManager.getPinnedPackages().toMutableSet()
        pinned.add(app.packageName)
        wallpaperManager.setPinnedPackages(pinned.toList())
        refreshDockApps()
        closeAllApps()
        launchApp(app)
    }

    private fun handleOverlayAppClick(app: AppModel) {
        if (isEditMode) {
            exitEditMode()
            return
        }
        openAppFromOverlay(app)
    }

    private fun showAppContextMenu(app: AppModel) {
        val options = arrayOf("Hide app", "Enter reorder mode", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> hideApp(app)
                    1 -> enterEditMode()
                }
            }
            .show()
    }

    private fun hideApp(app: AppModel) {
        val current = wallpaperManager.getHiddenPackages().toMutableSet()
        current.add(app.packageName)
        wallpaperManager.setHiddenPackages(current)
        applyAppPreferencesAndRefresh()
        Toast.makeText(this, "Hidden: ${app.label}. Restore in Settings > Apps.", Toast.LENGTH_SHORT).show()
    }

    private fun enterEditMode() {
        isEditMode = true
        Toast.makeText(this, "Edit mode: D-pad to move app, Back to exit", Toast.LENGTH_LONG).show()
    }

    private fun exitEditMode() {
        isEditMode = false
        Toast.makeText(this, "Edit mode off", Toast.LENGTH_SHORT).show()
    }

    private fun moveFocusedAppInOverlay(dx: Int, dy: Int) {
        val rv = binding.allAppsRecyclerView
        val pos = rv.focusedChild?.let { rv.getChildAdapterPosition(it) } ?: -1
        if (pos < 0) return
        val target = pos + dx + dy
        if (target < 0 || target >= installedApps.size || target == pos) return
        moveVisibleApp(pos, target)
        rv.post {
            val holder = rv.findViewHolderForAdapterPosition(target)
            holder?.itemView?.requestFocus()
        }
    }

    private fun openAllApps() {
        isEditMode = false
        if (installedApps.isNotEmpty()) {
            allAppsAdapter.updateApps(installedApps)
        }
        binding.allAppsOverlay.visibility = View.VISIBLE
        binding.allAppsOverlay.post {
            val firstHolder = binding.allAppsRecyclerView.findViewHolderForAdapterPosition(0)
            if (firstHolder != null) firstHolder.itemView.requestFocus() else binding.allAppsRecyclerView.requestFocus()
        }
    }

    private fun restoreHomeFocus() {
        if (wallpaperManager.getVisibleAppCount() > 0 && binding.bottomDockContainer.visibility == View.VISIBLE) {
            val holder = binding.appDockRecyclerView.findViewHolderForAdapterPosition(0)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                binding.appDockRecyclerView.requestFocus()
            }
        } else {
            binding.btnLauncherSettings.requestFocus()
        }
    }

    private fun closeAllApps() {
        if (isEditMode) exitEditMode()
        binding.allAppsOverlay.visibility = View.GONE
        binding.root.post {
            restoreHomeFocus()
        }
    }

    private fun openSettings() {
        refreshWallpaperGallery()
        refreshMoviesShelf()

        val ip = WallpaperServer.getLocalIpAddress()
        val port = 8080
        val url = if (ip != null) "http://$ip:$port" else "http://<TV_IP>:$port"

        binding.serverUrlText.text = url
        val qrBitmap = WallpaperServer.generateQrCodeBitmap(url, 240)
        binding.qrCodeImageView.setImageBitmap(qrBitmap)

        startServer(port)

        showSettingsPage("system")
        binding.keepScreenOnSwitch.isChecked = wallpaperManager.isKeepScreenOnEnabled()
        appManageAdapter.update(allInstalledApps, wallpaperManager.getHiddenPackages())
        syncDockCountLabel()
        binding.settingsOverlay.visibility = View.VISIBLE
        binding.btnSystemSettings.requestFocus()
    }

    private fun showSettingsPage(page: String) {
        binding.pageWallpapers.visibility = if (page == "wallpapers") View.VISIBLE else View.GONE
        binding.pageMovies.visibility = if (page == "movies") View.VISIBLE else View.GONE
        binding.pageAmbient.visibility = if (page == "ambient") View.VISIBLE else View.GONE
        binding.pageDock.visibility = if (page == "dock") View.VISIBLE else View.GONE
        binding.pageApps.visibility = if (page == "apps") View.VISIBLE else View.GONE
        binding.pageTransfer.visibility = if (page == "transfer") View.VISIBLE else View.GONE
        binding.pageSystem.visibility = if (page == "system") View.VISIBLE else View.GONE
    }

    private fun closeSettings() {
        stopServer()
        binding.settingsOverlay.visibility = View.GONE
        binding.btnLauncherSettings.requestFocus()
    }

    private fun startServer(port: Int) {
        if (wallpaperServer == null) {
            try {
                wallpaperServer = WallpaperServer(
                    context = this,
                    port = port,
                    onWallpaperUploaded = { uploadedFile ->
                        runOnUiThread {
                            wallpaperManager.setWallpaper(uploadedFile)
                            transitionToWallpaper(uploadedFile)
                            refreshWallpaperGallery()
                            Toast.makeText(this, "Wallpaper applied: ${uploadedFile.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onVideoUploaded = { videoFile ->
                        runOnUiThread {
                            refreshMoviesShelf()
                            Toast.makeText(this, "Video ready to play: ${videoFile.name}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDeleteFile = { fileName ->
                        runOnUiThread {
                            loadInitialWallpaper()
                            refreshWallpaperGallery()
                            Toast.makeText(this, "Deleted: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                wallpaperServer?.start()
            } catch (e: Exception) {
                Toast.makeText(this, "Server error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopServer() {
        wallpaperServer?.stop()
        wallpaperServer = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (isEditMode) {
                        exitEditMode()
                        return true
                    }
                    if (binding.settingsOverlay.visibility == View.VISIBLE) {
                        closeSettings()
                        return true
                    }
                    if (binding.allAppsOverlay.visibility == View.VISIBLE) {
                        closeAllApps()
                        return true
                    }
                    // When this app is the default launcher, never let BACK exit/reload Home
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isEditMode && binding.allAppsOverlay.visibility == View.VISIBLE) {
                        moveFocusedAppInOverlay(-1, 0)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isEditMode && binding.allAppsOverlay.visibility == View.VISIBLE) {
                        moveFocusedAppInOverlay(1, 0)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isEditMode && binding.allAppsOverlay.visibility == View.VISIBLE) {
                        val span = (binding.allAppsRecyclerView.layoutManager as GridLayoutManager).spanCount
                        moveFocusedAppInOverlay(0, -span)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isEditMode && binding.allAppsOverlay.visibility == View.VISIBLE) {
                        val span = (binding.allAppsRecyclerView.layoutManager as GridLayoutManager).spanCount
                        moveFocusedAppInOverlay(0, span)
                        return true
                    }
                    if (binding.settingsOverlay.visibility != View.VISIBLE && binding.allAppsOverlay.visibility != View.VISIBLE) {
                        openAllApps()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.settingsOverlay.visibility == View.VISIBLE) {
            closeSettings()
        } else if (binding.allAppsOverlay.visibility == View.VISIBLE) {
            closeAllApps()
        }
        // Consume back on the home surface so the launcher is not finished/reloaded
    }

    override fun onStop() {
        super.onStop()
        // Stop the local web server when the activity is backgrounded (e.g. via TV System Settings shortcut)
        stopServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWallpaperRotationTimer()
        stopServer()
    }
}
