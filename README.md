# 🎮 hShop Thor

**hShop Thor** is a specialized, dual-screen native Android client for browsing, downloading, decrypting, and launching Nintendo 3DS titles directly on the **AYN Thor** handheld gaming console.

---

## ✨ Features

- **Dual-Screen Architecture**:
  - **Top Screen (Display 0 - 1920x1080 Landscape)**: High-resolution boxart hero view, game metadata, live download/decryption progress, one-tap **"Play Game"** launch actions, and direct **"Delete ROM"** management.
  - **Bottom Screen (Display 4 - 1080x1240 Touchscreen)**: Interactive catalogue browsing with region filter chips, search bar, Local Library manager, download queue manager, and customizable settings.
- **Seekable .ZCCI Compression (AzaharPlus / Z3DS format)**:
  - High-speed native Zstandard compression (`com.github.luben:zstd-jni`) that converts `.cci` cartridges into seekable `.zcci` files.
  - Reduces SD card storage footprint by 40%–75% while maintaining instant zero-lag random access for emulators.
  - On-demand compression directly in the Library tab or fully automated upon download.
- **Local ROM Library Manager**:
  - Dedicated **Library** tab that scans your 3DS ROM directory for `.zcci`, `.cci`, `.3ds`, and `.cia` files.
  - One-tap Play launch into **Azahar / Lime3DS / Citra**, on-demand `.zcci` compression, manual `.cia` decryption, and ROM deletion.
- **Handheld Gamepad Navigation**:
  - Full hardware mapping for **D-Pad**, **Analog Sticks**, **A/B/X/Y**, and **Shoulder Triggers (L1/R1/L2/R2)**.
- **On-Device Hardware Decryption**:
  - Bundled ARM64 native decryption engine (`libcia3ds.so`) with embedded `seeddb.bin`.
  - Automatically converts encrypted `.cia` files into decrypted `.cci` / `.3ds` cartridge ROMs directly compatible with **Azahar**, **Lime3DS**, and **Citra**.
  - Integrated **SeedFetcher** for on-the-fly Nintendo CDN seed retrieval for late-generation titles.
- **Streamlined Cloudflare Verification**:
  - Isolated, compact Turnstile verification widget that lets you complete security checks with a single tap.
- **Direct Emulator Integration & Dual-Screen Handoff**:
  - Integrated Android `FileProvider` to launch downloaded ROMs directly into installed emulators (`org.azahar_emu.azahar`, `io.github.lime3ds.android`, `org.citra.citra_emu`).
  - Seamless Display 4 presentation dismissal before launching emulators so the emulator can take full control of both screens.
- **Flexible Storage Management & Auto-Update**:
  - Dedicated storage folders for base games (`/sdcard/ROMs/3DS`) and Updates/DLC (`/sdcard/ROMs/3DS/Updates_DLC`).
  - Automatic GitHub update checker that alerts you when a new release is available.

---

## 🎮 Handheld Button Mapping

| Button | Action | Description |
| :--- | :--- | :--- |
| **D-Pad Up / Down** (or **Left Stick**) | **Navigate Titles** | Scrolls catalogue and live-updates the top screen hero view. |
| **D-Pad Left / Right** | **Switch Region** | Cycles region filters (USA, EUR, JPN, Australia, etc.). |
| **L1 / R1** (or **L2 / R2**) | **Switch Category** | Cycles categories (Games, Updates, DLC, Virtual Console, DSiWare). |
| **Button A** (or **Enter**) | **Download / Select** | Starts download or selects title. |
| **Button B** (or **Back**) | **Back** | Closes overlays or returns to Browse tab. |
| **Button X** | **Decrypt .CCI** | Manually runs native decryption on downloaded `.cia`. |
| **Button Y** (or **Select**) | **Cycle Tabs** | Switches between Browse, Library, Downloads, and Settings tabs. |

---

## 🛠️ Building & Installing

### Prerequisites
- Android Studio / Android SDK (API 35, Min SDK 26)
- Java 17 / Kotlin 2.0+
- Connected **AYN Thor** via ADB

### Build & Deploy Debug APK
```bash
# Build and install directly onto connected AYN Thor
./gradlew :app:installDebug

# Launch the app
adb shell am start -n me.erista.hshop.thor/.MainActivity
```

---

## 📂 Project Structure

```
hshop/
├── app/
│   ├── src/main/
│   │   ├── assets/             # seeddb.bin & cdn-nintendo-leaf.pem
│   │   ├── jniLibs/            # Native libcia3ds.so (arm64-v8a, armeabi-v7a, x86_64)
│   │   └── kotlin/
│   │       ├── io/github/cia3ds/   # JNI decryption bindings & SeedFetcher
│   │       └── me/erista/hshop/thor/
│   │           ├── converter/  # Conversion routines
│   │           ├── data/       # AppSettings, DownloadModels & Repositories
│   │           ├── download/   # ThorDownloadManager & Turnstile resolver
│   │           ├── presentation/ # Dual-screen presentation controller
│   │           ├── ui/         # Jetpack Compose UI (Top, Bottom, Settings, Downloads)
│   │           └── util/       # GameLauncher & StorageUtils
└── core-scraper/               # Multiplatform hShop HTML / REST Scraper
```

---

## 📄 License
Open source and built for the handheld emulation community. Upstream decryption components derived from Project_CTR `ctrtool` and `makerom`.
