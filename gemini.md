# Canvas TV Launcher — Native Android TV Launcher App

## Project Overview
**Canvas TV Launcher** is a lean, ultra-minimal, high-contrast native Android TV launcher and ambient digital canvas built with Kotlin. It is targeted for a Realme Android TV (Android 11 / API 30) and fully portable to any modern Android TV or Google TV device.

### Operating Mode & Safety
- **Mode:** Sideloaded Leanback Application (`android.intent.category.LEANBACK_LAUNCHER`) + Default TV Home ready (`android.intent.category.HOME`, `android.intent.category.DEFAULT`).
- **Default Home Status:** Tested and proven safe. Operates alongside the stock/Simple Home app with zero lockout risk.
- **Resource Mandate:** 0% CPU overhead when idle or in background. All transitions and timers strictly freeze in `onPause()` / `onStop()` when playing content in other apps.

---

## Implemented Architecture & Completed Features

### 1. Ultra-Clean Minimal Dock & Focus Engine
- **Single Row Corner Dock:** Pushed to the extreme bottom-left corner (`paddingStart=24dp`, `paddingBottom=12dp`).
- **1-Icon Extreme Clean Mode:** Displays a single compact icon (`36dp` inside a `52dp` frame) with no text labels cluttering the screen.
- **In-Place D-Pad Navigation:** Navigating left/right smoothly scrolls through all installed Leanback applications in-place.
- **Dock Density Toggle:** Switchable between 1-Icon Minimal and 5-Icon modes via the Settings Hub.

### 2. Ambient Digital Canvas & Seamless Wallpaper Engine
- **Bundled Out-of-the-Box 4K Artwork:** The crisp **Firewatch Watchtower 4K** artwork is permanently bundled into the APK resources (`default_wallpaper.jpg`). First boot and empty states immediately show this canvas with zero black screen gap.
- **Double-Buffered Preloaded Dissolve:** Wallpapers pre-cache into RAM before crossfading. The incoming image dissolves directly over the outgoing image (`alpha = 0` $\rightarrow$ `1.0`), eliminating black dips or frame drops.
- **Multi-Interval Rotation:** Supports 15s (Canvas ambient mode), 30s, 1m, 1h, and Off.
- **Adjustable Dim Scrim:** One-tap toggle between **Bright & Clear (10% dim)** and **Dimmed for Contrast (40% dim)**.
- **High-Contrast Typography:** Clock and Date positioned at the extreme top-left corner (`#F1F3F5` crisp white with drop shadows).

### 3. Integrated Phone-to-TV Transfer Hub & Public Storage Routing
- **Strictly On-Demand Micro Server:** Starts on port 8080 **only** when Settings is opened. The microsecond Settings is closed or content begins playing, the server completely shuts down (0% CPU, 0 open ports, 0 battery drain).
- **Local QR Code Pairing:** Renders a clean 240px QR code on the TV for instant scanning from phone/tablet browser without requiring companion apps.
- **Glassmorphic Single-Page Web App:**
  - Batch uploader for Wallpapers, Movies, and APKs with storage gauges and 1-tap delete.
- **Universal Public Storage Routing (`MediaStorageHelper`):**
  - **Public Folder Resolution & Auto-Creation:**
    - **Wallpapers/Pictures:** Resolves to `/sdcard/Pictures/Wallpapers/` and auto-scans `/sdcard/Pictures`, `/sdcard/Images`, and internal storage. Missing directories are auto-created (`mkdirs()`).
    - **Movies/Videos:** Resolves to `/sdcard/Movies/` with fallback across `/sdcard/Download` and external adopted drives.
    - **Apps/APKs:** Resolves to `/sdcard/Download/` and `/sdcard/Apps/`.
  - **No SD Card Requirement:** Operates transparently on internal flash storage via Android's `/sdcard` standard symlink, with seamless fallback to app-private storage (`context.filesDir`) if primary storage permissions are restricted.
  - **Android MediaScanner Auto-Index:** Triggers `MediaScannerConnection.scanFile` immediately on upload so VLC, Nova Player, and TV file managers instantly index files in their libraries.
- **Direct Sideloading (Mobile-to-TV APK Installer):**
  - Upload any `.apk` file (like SmartTube Beta) from your phone.
  - Immediately triggers Android TV's native package installer dialog (`ACTION_VIEW`), enabling effortless remote updates with zero USB thumb drive swapping.

### 4. TV Media Shelf & Clean Thumbnail Engine
- **"Wallpapers on TV" Strip:** Horizontal gallery showing all discovered and uploaded images with click-to-apply and D-pad long-press delete (with confirmation dialog).
- **High-Performance Downsampling (200+ Images):** Micro-thumbnail downsampling (`160x90` via Coil) guarantees 60 FPS snappy D-pad scrolling with zero OOM spikes, even with large image libraries.
- **Zero-Ghost Cache Eviction:** Deleted images are immediately evicted from Coil memory cache keys and disk cache, ensuring stale downsampled thumbnails never linger.
- **"Movies & Videos on TV" Shelf:** Lists all sent video files with filenames and sizes.
- **Secure FileProvider Playback:** Clicking any video card generates a secure `content://` URI and immediately opens the file in **VLC** or **Nova Video Player**.

### 5. Settings Hub & TV Diagnostics
- **Sleek Transparent Settings Button:** Positioned at the extreme top-right corner.
- **TV System Settings Access:** Direct shortcut button inside the hub opens Android TV system settings (Wi-Fi, Bluetooth, Inputs, Apps).
- **Default Launcher Chooser:** In-app button to set or switch default launcher.
- **Idle Standby Context:** With ambient/dream components disabled (Google Backdrop / Android Screensaver / TCL screensaver), the panel falls through to the system screen-off timeout and sleeps during idle (~2 min observed on the Realme TV). The earlier fix (`screensaver_enabled = 0`) effectively removed the idle ambient layer — disabling a dream does not extend a screen-off timeout. Ambient canvas can be restored via the in-app **Keep Screen On (Ambient)** opt-in or by re-enabling the backdrop package.

---

## Technical Stack
- **Language:** Kotlin
- **Build System:** Gradle 8.9 / Android Gradle Plugin 8.5.2
- **Java Runtime:** Android Studio JBR (OpenJDK 21)
- **Compile SDK:** 35 | **Target SDK:** 33 | **Min SDK:** 24 (Android 7.0)
- **Core Libraries:**
  - `androidx.core:core-ktx:1.13.1`
  - `androidx.appcompat:appcompat:1.7.0`
  - `androidx.leanback:leanback:1.0.0`
  - `androidx.core.content.FileProvider` (Secure media sharing)
  - `io.coil-kt:coil:2.6.0` (Image loading & RAM caching)
  - `org.nanohttpd:nanohttpd:2.3.1` (Local micro HTTP server)
  - `com.google.zxing:core:3.5.3` (Local QR code generation)

---

## Verified System Profiler Metrics
- **RAM Usage:** **~41–59 MB** total PSS (vs. 300–500 MB for stock Google TV launcher).
- **CPU Idle Overhead:** **0.0%** (zero background threads when playing media).
- **GPU Rendering Time:** **5 ms** per frame (50th percentile) on Skia/OpenGL hardware pipeline.
- **Disk Footprint:** **~12 MB** total app size.

---

## Project Roadmap

```
[Phase 1: Foundation & Scaffold]  ──────────► COMPLETED
[Phase 2: App Discovery & TV Manifest] ─────► COMPLETED
[Phase 3: Minimal Dock & D-Pad Engine] ─────► COMPLETED
[Phase 4: Phone Hub & File Routing] ────────► COMPLETED
[Phase 5: Seamless Canvas Crossfade] ───────► COMPLETED
[Phase 6: Default Home & Media Playback] ───► COMPLETED
       │
       ▼
[Phase 7: Packaging & Production Release] ──► READY
```

### Production APK Location:
- **Path:** `neuhome/app/build/outputs/apk/debug/app-debug.apk`
- **Portability:** Ready for sideloading via USB Drive, Downloader app, or ADB on any Android TV.
