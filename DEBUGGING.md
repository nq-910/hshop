# 🛠️ hShop Thor — Architecture & Debugging Reference Guide

This document contains internal architecture notes, troubleshooting procedures, ADB diagnostic commands, and lessons learned for developers maintaining and debugging **hShop Thor** on the **AYN Thor** dual-screen handheld console.

---

## 1. Hardware & System Architecture (AYN Thor)

- **SoC**: Qualcomm Snapdragon 8 Gen 2 (`kalama`), Android 13 (`TKQ1.231222.001`).
- **Primary Top Screen**:
  - **Display ID**: `0` (Default Display)
  - **Resolution / Orientation**: `1920x1080` Landscape, 120Hz AMOLED.
  - **Managed By**: `MainActivity.setContent { TopScreenContent(...) }`.
- **Secondary Bottom Screen**:
  - **Display ID**: `4` (Category `DISPLAY_CATEGORY_PRESENTATION`)
  - **SurfaceFlinger Unique ID**: `4630946482288158084`
  - **Resolution / Orientation**: `1080x1240` Portrait / Clamshell, 120Hz Touch AMOLED.
  - **Managed By**: `ThorBottomPresentation` extending Android `Presentation(context, secondaryDisplay)`.

### Screenshot Diagnostic Commands
```bash
# Capture Top Screen (Display 0)
adb shell "screencap -p /sdcard/disp0.png" && adb pull /sdcard/disp0.png

# Capture Bottom Screen (Display 4) using SurfaceFlinger display ID
adb shell "screencap -p -d 4630946482288158084 /sdcard/disp_bottom.png" && adb pull /sdcard/disp_bottom.png
```

---

## 2. Controller & Input Subsystem

The physical controls on the AYN Thor are exposed via Linux input devices (`Odin Controller` on `/dev/input/event9`).

### Keycode Mappings:
- **D-Pad Up / Down**: `KEYCODE_DPAD_UP` / `KEYCODE_DPAD_DOWN` (or `AXIS_HAT_Y` / `AXIS_Y` < -0.5 / > 0.5) ➔ Navigates titles with auto-scroll.
- **D-Pad Left / Right**: `KEYCODE_DPAD_LEFT` / `KEYCODE_DPAD_RIGHT` (or `AXIS_HAT_X` / `AXIS_X`) ➔ Cycles subcategory / region filter chips.
- **Shoulder Triggers**: `KEYCODE_BUTTON_L1`/`L2` / `KEYCODE_BUTTON_R1`/`R2` ➔ Cycles main category tabs.
- **Face Buttons**:
  - `KEYCODE_BUTTON_A` / `KEYCODE_ENTER` ➔ Trigger download or play.
  - `KEYCODE_BUTTON_B` / `KEYCODE_BACK` ➔ Return to Browse tab.
  - `KEYCODE_BUTTON_X` ➔ Trigger native `.CCI` decryption.
  - `KEYCODE_BUTTON_Y` / `KEYCODE_BUTTON_SELECT` ➔ Cycle bottom tabs (Browse / Downloads / Settings).

### Debugging Controller Events
```bash
# View real-time key and motion events from Odin Controller
adb shell getevent -lt /dev/input/event9
```

---

## 3. Decryption Subsystem (`libcia3ds.so` + `SeedFetcher`)

### JNI Signature & Argument Layout
In `libcia3ds.so` (upstream from `Hinoaaaaaf212/cia3ds-android` and Project_CTR):
```kotlin
private external fun nativeDecryptCia(
    inFd: Int,                          // ParcelFileDescriptor.fd of source .cia
    outFd: Int,                         // ParcelFileDescriptor.fd of output .cci
    seedDbPath: String,                 // Absolute path to filesDir/seeddb.bin
    tmpDir: String,                     // Absolute path to cacheDir/cia3ds-work
    originalName: String,               // Original filename (e.g. "Game.cia")
    wantCci: Boolean,                   // TRUE for .cci/.3ds cartridge ROM, FALSE for decrypted .cia
    progress: NativeProgressCallback?,  // Progress updates (0-100%)
    log: NativeLogCallback?,            // Verbose stdout log lines
    seedFetcher: NativeSeedFetcherCallback? // Dynamic Nintendo CDN seed callback
): Int
```

### Critical Rules & Gotchas:
1. **Never pass `null` for `String` arguments in `nativeDecryptCia`**:
   - The native C++ function calls `env->GetStringUTFChars(...)` directly without checking for NULL. Passing `null` causes a fatal `SIGABRT` (`GetStringUTFChars received NULL jstring`). Pass `""` (empty string) instead.
2. **`tmpDir` must be explicitly provided**:
   - Do not pass an empty string for `tmpDir`. The native code constructs its scratchpad directory as `sprintf(workDir, "%s/work", tmpDir)`. Passing `""` resolves to `/work`, which fails with `ERR: cannot create work dir /work (unwritable)`.
   - Always pass `context.cacheDir.resolve("cia3ds-work").apply { mkdirs() }.absolutePath`.
3. **Cartridge Image Output**:
   - When converting for **Lime3DS**, **Citra**, or **Azahar**, set `wantCci = true` and name the output file with `.cci` extension.
4. **Seed Crypto (`SeedFetcher.kt`)**:
   - Uses `assets/cdn-nintendo-leaf.pem` client certificate to authenticate with `kagiya-ctr.cdn.nintendo.net` over HTTPS for titles requiring post-2015 Seed Crypto not in `seeddb.bin`.

### Monitoring Decryption Logs
```bash
adb logcat -s "Cia3dsJni" "ThorDownloadManager" "SeedFetcher"
```

---

## 4. Cloudflare Turnstile & Web Scraping

- **Scraper**: `core-scraper` module queries `https://hshop.erista.me` REST API and HTML pages.
- **Turnstile Dialog**:
  - `TurnstileDownloadDialog.kt` renders an invisible/interactive `WebView` with `setAcceptThirdPartyCookies(true)` to pass Cloudflare verification.
  - Uses `MutationObserver`, `setDownloadListener`, and JS injection to capture direct tokens from `https://download*.erista.me/content/{id}?token=...`.

### Monitoring Download & Turnstile Logs
```bash
adb logcat -s "TurnstileDialog" "AndroidBridge" "ThorDownloadManager"
```

---

## 5. Storage & ROM Directory Conventions

- Default storage path: `/storage/emulated/0/ROMs/3DS`
- Permissions: `MANAGE_EXTERNAL_STORAGE` (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`)
- Output file naming: `[Game Name] [ProductCode].cci` (e.g., `Yo-Kai Watch [CTR-P-AYWZ].cci`).
- FileProvider authority: `me.erista.hshop.thor.fileprovider` mapping `external_files` (`/storage/emulated/0`).

---

## 6. Useful One-Liner ADB Commands

```bash
# Restart app cleanly
adb shell am start -S -W -n me.erista.hshop.thor/.MainActivity

# Check crash tombstones
adb logcat -d -b crash

# Check contents of ROMs folder
adb shell ls -lh /sdcard/ROMs/3DS/

# Clear app cache if scratchpad fills up
adb shell pm clear me.erista.hshop.thor
```

---

## 7. Dual-Screen Presentation Focus & Back Button Handling

### The Problem:
`ThorBottomPresentation` inherits from Android's `Dialog` class. When a user tapped the bottom touchscreen, window focus shifted to the Presentation dialog. Subsequently pressing the device's physical **Back button** (`KEYCODE_BACK`) triggered `Dialog.onBackPressed()`, immediately dismissing the Presentation window and leaving the bottom screen blank.

### The Fix in `ThorBottomPresentation.kt`:
1. Mark dialog non-cancelable:
   ```kotlin
   setCancelable(false)
   setCanceledOnTouchOutside(false)
   ```
2. Override `onBackPressed()` and `onKeyDown()` to forward navigation back to `viewModel.handleButtonB()` or the main activity dispatcher, preventing Android from destroying the Presentation dialog.
3. Forward all `onGenericMotionEvent` and `onKeyDown` events to `MainActivity` so gamepad controls remain active even when the secondary touchscreen holds focus.

---

## 8. Android FUSE Casing & Symlink Deduplication

### The Inode Alias Problem:
On the AYN Thor, Android's user storage is mounted via FUSE (`sdcardfs` emulation) where `/sdcard` is a symlink to `/storage/emulated/0`. Furthermore, directory lookups can be case-insensitive:
```bash
ls -id /sdcard/ROMs/n3ds /storage/emulated/0/Roms/n3ds
# Both return the exact same inode: 70731
```
Because standard Java `File.canonicalPath` and string equality do not normalize case differences (`ROMs` vs `Roms`), scanning both the user's selected path and hardcoded fallbacks resulted in every ROM file being indexed twice (e.g. 32 ROMs producing 64 titles in the library).

### The Fix in `MainViewModel.kt`:
1. Scan paths are canonicalized and deduplicated case-insensitively (`.canonicalPath.lowercase()`).
2. Nested child directories (e.g. `Updates_DLC` inside the ROM root) are pruned before `.walkTopDown()` to prevent recursive duplication.
3. Items are deduplicated by file name and byte length:
   ```kotlin
   items.distinctBy { "${it.file.name.lowercase()}#${it.sizeBytes}" }
   ```

---

## 9. Atomic File Staging

Never stream network downloads directly into target ROM filenames (`.cia` or `.cci`). Always stream into `${targetFilePath}.download` and rename atomically upon 100% completion. If interrupted, out-of-storage, or cancelled, delete the staging file immediately so corrupt or incomplete archives never linger on disk.

