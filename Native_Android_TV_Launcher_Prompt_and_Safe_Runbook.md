# Native Android TV Launcher — Project Prompt & Safe Implementation Runbook

## Project prompt (copy/paste)

### Objective
Build a polished, lightweight **native Android TV launcher** for my Realme Android TV running Android 11. The goal is a clean, modern home screen with a compact bottom app dock, a complete app library, user-selected local wallpapers, and optional timed wallpaper rotation.

This project is **launcher-only**. Do not debloat the TV, remove or disable packages, change DNS, remap remote keys, alter display resolution/density, or modify firmware. Preserve the stock launcher and a reliable route back to it throughout development and testing.

### Device and work boundaries
- First establish the exact TV model, Android build/API level, available RAM/storage, current Home app, and whether the device is Android TV/Google TV. Do not assume these from “Realme Android 11.”
- Use the supplied repository/workspace if one is provided. Do not clone an unrelated repository or replace project files without explaining why and obtaining approval.
- Do not scan the home network. For ADB, use only a connection method and address I provide or explicitly authorize. Tell me when a TV-side debugging authorization prompt will appear; I must approve it on the TV.
- Begin with read-only inspection and a written change plan. Ask before any action that could affect the current Home app, device connectivity, stored Wi-Fi, remote behavior, or recovery options.
- Never disable, uninstall, overwrite, or hide the stock launcher or setup/recovery components. Do not make the custom launcher the default until I approve after testing.
- Keep a record of commands, package names, APK version/hash, and test results. Never claim an action succeeded based only on a command returning “Success”; verify the resulting TV state.

### Product requirements

#### 1. Home screen and navigation
- Use an opaque, dark, high-contrast background and a restrained modern visual style. Avoid imitating proprietary assets or branding; use an original visual design.
- Provide a configurable **bottom dock** with 4–7 user-chosen apps; default to 5 if I have not specified a count. Let me reorder, add, and remove dock apps using a TV-friendly interface.
- Provide a separate scrollable app-library grid for all discoverable TV apps. Discover apps through the Android TV/Leanback launch intent and package visibility rules; do not hard-code package names as the only discovery mechanism.
- Make the dock visually compact, with smaller icons than a conventional oversized TV launcher. Keep labels readable at normal viewing distance. Provide an optional setting for icon size and dock density rather than forcing a single layout.
- Make D-pad behavior predictable: visible focus at all times; left/right move within a row; up/down move between logical rows; edge behavior is deliberate; Back closes overlays before leaving Home; focus returns to the previously selected item when appropriate.
- Support launch, missing/uninstalled app states, and a safe empty-app state without crashing. Do not intercept or remap remote keys globally.
- Keep startup and transitions responsive on a low-memory TV. Avoid live video, continuous animation, heavy blur, polling, and unnecessary background services.

#### 2. Wallpaper selection and rotation
- Allow the user to choose one or more local images using Android’s system document/photo picker where available. Use the Storage Access Framework and persist only the URI permissions the user grants; do not request broad storage access unless the implementation proves it is essential and I approve it.
- If folder selection is unavailable or unreliable on this TV’s picker, provide a clear fallback: select multiple images, or copy selected images into app-private storage with the user’s consent. Explain that limitation in the UI and README.
- Offer fit modes appropriate to TV screens (for example, crop/fill and fit/letterbox), plus a dark overlay control so app labels remain legible. Validate file type, decode size safely, and handle deleted or inaccessible images gracefully.
- Provide rotation options: off, and user-selectable intervals such as 15 minutes, 1 hour, 6 hours, and 24 hours. Include “next wallpaper” and “previous wallpaper” controls in the launcher’s own settings.
- Rotation must be best-effort, not promised to happen at an exact wall-clock time. Android may defer background work during sleep or idle. Do not keep a permanent service awake or prevent TV sleep. Persist the selected set and last displayed image; rotate on safe lifecycle events such as Home becoming active and, when appropriate, scheduled work. Document the timing behavior.
- Make rotation easy to pause and reset. If a source image disappears or permission is revoked, show a safe fallback and a clear way to choose images again.

#### 3. Useful TV-specific features
- Add a small launcher settings screen usable entirely with the D-pad: manage dock order, icon/dock sizing, wallpaper sources/fit/overlay/rotation interval, and restore defaults.
- Add a clearly labeled shortcut to Android system Settings. Do not imply that restricted system functions are supported.
- If showing date/time, use the device’s local time and locale; do not require network access. Do not add weather, accounts, analytics, advertising, or cloud sync in the initial release.
- Only implement HDMI/Live TV input switching if the device exposes a supported public TV-input API and testing confirms it works. Otherwise provide a shortcut to the stock input/settings screen if accessible, or omit the feature and explain why. Do not rely on hidden or privileged APIs.
- Do not add accessibility services, notification access, VPNs, device-admin privileges, or key-filtering services for convenience.

#### 4. Engineering requirements
- Build a native Android app suitable for television, using Kotlin (unless the supplied project already establishes another native language). Keep dependencies minimal and justify each one.
- Inspect the device API level and select a compatible `minSdk`; select a current, compatible `targetSdk` supported by the build tools and the device. Do not promise installability until compatibility is checked.
- Declare the correct Home/Leanback launcher intent and app visibility queries needed to discover TV apps on Android 11+. Verify that the app list is not empty on the actual device.
- Use Android lifecycle-aware storage and work scheduling. Do not run continuous polling or hold wake locks for wallpaper changes.
- Keep the activity window opaque. Handle process recreation, configuration changes, storage permission revocation, malformed images, absent apps, and low-memory conditions.
- Do not include credentials, user data, or private device details in logs. No analytics or internet permission unless a documented, approved feature genuinely needs it.
- Provide source code, build instructions, install/update instructions, a versioned APK if requested, and a plain-language README. Include the exact rollback steps and known limitations.

### Safety and rollout rules
1. **Read-only inventory first:** record model/build/API, current default Home/package, installed Home-capable activities, available storage, and ADB/debugging state. Do not alter settings during inventory.
2. **Build and inspect off-device first:** run static checks and tests; review manifest permissions, exported components, and requested privileges. Provide the permission/package diff before installation.
3. **Install without taking over Home:** sideload the debug APK only after I approve the exact APK and installation method. Keep stock Home enabled. Launch the custom app directly for testing.
4. **Test on device before defaulting:** verify real remote navigation, app discovery/launch, wallpaper selection/persistence, rotation behavior, Settings shortcut, restart/relaunch, and recovery route. Capture screenshots where supported, but do not treat screenshots as proof for protected video/HDMI surfaces.
5. **Approval gate for default Home:** only after I review test results, ask whether I want to set the new launcher as default. Prefer selecting it through the TV’s normal Home-app/Default-app UI. Do not disable the stock launcher. If no safe UI route exists, stop and explain options rather than forcing a takeover.
6. **Rollback:** document and test how to return to stock Home before changing the default. Keep the stock launcher installed and enabled. Confirm that the stock Home can be opened from Settings or the system Home chooser. Do not reboot into a changed default until rollback is understood and approved.
7. **Close out safely:** if ADB was used, tell me how to revoke the authorization and turn debugging off after all tests. Do not toggle networking/debugging in a way that could disconnect the TV mid-recovery.

### Required acceptance evidence
Report pass/fail and evidence for each item:
- The APK installs on the identified Android 11 device without replacing or disabling stock Home.
- Home can be launched directly and the app grid discovers the installed TV apps.
- D-pad navigation works with the physical remote across dock, grid, dialogs, and settings; focus is visible and predictable.
- Dock order and supported sizing options persist after app restart.
- User-selected wallpaper(s) display correctly; granted image access persists as designed; revoked/missing images fail safely.
- Rotation interval can be changed, paused, and resumed; observed behavior and Android timing limitations are documented.
- TV Settings can be opened through the shortcut, or the limitation is documented.
- The launcher survives force-stop/relaunch and a normal TV reboot while stock Home remains available.
- The rollback route to stock Home has been verified before any default-Home change.
- App permissions, package visibility, known limitations, build steps, and restore instructions are documented.

At the end, provide a concise implementation summary, acceptance checklist with evidence, APK/source locations, remaining risks, and the next decision I need to make. Never describe an untested behavior as verified.

---

## Safe implementation steps for you

### Phase 1 — Confirm the target and recovery path
1. Find the exact model number from the TV’s About/System screen or label, plus full software/build version. Confirm it is an Android TV device and record Android API level.
2. Confirm the current Home app and whether the stock launcher is still enabled. Since the TV has already been debloated, do not assume that all recovery components remain intact; inspect before changing anything.
3. Decide whether you can recover through the TV’s normal Settings/Home-app chooser if the custom launcher crashes. If that route is unclear, do not change the default Home app.
4. If using ADB, enable it only with your consent, approve the on-screen computer authorization yourself, and use a known address rather than network scanning.

### Phase 2 — Build and review without taking over
5. Build the launcher for the TV’s measured API level. Confirm Leanback app discovery works on Android 11 and that the app’s manifest requests only necessary permissions.
6. Test the UI with an emulator or local build first. Check D-pad focus, text size/readability at TV distance, empty and error states, and image memory usage.
7. Review the APK contents and list its permissions, exported components, and dependencies. Do not proceed if unexplained permissions or services appear.

### Phase 3 — Install as a test app
8. Install the APK without changing the default Home app or disabling any existing package. Open it directly from the app list or an approved ADB launch command.
9. Test the physical remote, app discovery and launching, dock editing, wallpaper selection, and settings. For local images, verify the system picker grants persistable access and test what happens after force-stop/relaunch.
10. Test wallpaper rotation over realistic intervals and after sleep/resume. Treat scheduling as best-effort; confirm it never keeps the TV awake unnecessarily.
11. Test Settings access and any input shortcuts individually. Omit features blocked by device policy or privileged API requirements instead of using hidden APIs or accessibility workarounds.

### Phase 4 — Decide whether to make it Home
12. Review test evidence and known limitations. Verify the stock launcher is still installed, enabled, and reachable.
13. Only if you explicitly approve, select the custom launcher using the TV’s normal default-Home interface. Avoid disabling stock Home. If the TV offers no safe selection path, stop rather than forcing one through shell commands.
14. Verify Home button behavior, app launch, return navigation, and stock-launcher recovery. Do not add remote-key remapping or other system changes in the same rollout.

### Phase 5 — Close out and preserve recovery
15. Provide the source/build instructions, APK version/hash, configuration steps, acceptance evidence, and plain-language rollback instructions.
16. If ADB was enabled, revoke the host authorization and disable debugging after the user confirms all tests and recovery steps are complete. Keep the TV connected to its normal network and verify normal Home behavior afterward.

## Explicitly out of scope
- Debloating, disabling or uninstalling packages, factory/service menus, firmware changes, DNS/ad blocking, remote-key remapping, accessibility helpers, permanent background services, cloud accounts, analytics, and forced default-launcher takeover.
