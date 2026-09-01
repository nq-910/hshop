# 🎮 hShop Thor

**hShop Thor** is a specialized, dual-screen native Android client for browsing, downloading, decrypting, and launching Nintendo 3DS titles directly on the **AYN Thor** handheld gaming console.

---

## ✨ Features

- **Dual-Screen Architecture**:
  - **Top Screen (Display 0 - 1920x1080 Landscape)**: High-resolution boxart hero view, game metadata, download speeds, live decryption progress, and one-tap **"Play Game"** launch actions.
  - **Bottom Screen (Display 4 - 1080x1240 Touchscreen)**: Interactive catalogue browsing with region filter chips, search bar, download queue manager, and customizable settings.
- **Handheld Gamepad Navigation**:
  - Full hardware mapping for **D-Pad**, **Analog Sticks**, **A/B/X/Y**, and **Shoulder Triggers (L1/R1/L2/R2)**.
- **On-Device Hardware Decryption**:
  - Bundled ARM64 native decryption engine (`libcia3ds.so`) with embedded `seeddb.bin`.
  - Automatically converts encrypted `.cia` files into decrypted `.cci` / `.3ds` cartridge ROMs directly compatible with **Lime3DS**, **Citra**, and **Azahar**.
  - Integrated **SeedFetcher** for on-the-fly Nintendo CDN seed retrieval for late-generation titles.
- **Direct Emulator Integration**:
  - Integrated Android `FileProvider` to launch downloaded ROMs directly into installed emulators (`io.github.lime3ds.android`, `dev.twilitrealm.dusk`).
- **Flexible Storage Management**:
  - Built-in visual folder browser and Android Storage Access Framework (SAF) picker to target internal storage or external SD cards (`/sdcard/ROMs/3DS`).

---

## 🎮 Handheld Button Mapping

| Button | Action | Description |
| :--- | :--- | :--- |
| **D-Pad Up / Down** (or **Left Stick**) | **Navigate Titles** | Scrolls catalogue and live-updates the top screen hero view. |
| **D-Pad Left / Right** | **Switch Region** | Cycles region filters (USA, EUR, JPN, Australia, etc.). |
| **L1 / R1** (or **L2 / R2**) | **Switch Category** | Cycles categories (Games, Updates, DLC, Virtual Console, DSiWare). |
| **Button A** (or **Enter**) | **Download / Select** | Starts download of selected title. |
| **Button B** (or **Back**) | **Back** | Closes overlays or returns to Browse tab. |
| **Button X** | **Decrypt .CCI** | Manually runs native decryption on downloaded `.cia`. |
| **Button Y** (or **Select**) | **Cycle Tabs** | Switches between Browse, Downloads, and Settings tabs. |

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
