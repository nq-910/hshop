<div align="center">

# 🎮 hShop Thor

**A high-performance, dual-screen native Android client designed exclusively for the AYN Thor handheld console.**

[![Release](https://img.shields.io/github/v/release/nq-910/hshop?include_prereleases&style=for-the-badge&color=2ecc71&logo=android)](https://github.com/nq-910/hshop/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/Target-AYN%20Thor-00E5FF?style=for-the-badge&logo=nintendo3ds&logoColor=white)](https://www.ayntec.com/)
[![License](https://img.shields.io/badge/License-GPLv3%2FMIT-orange?style=for-the-badge)](LICENSE)

<br/>

Browse, download, decrypt, compress, and launch Nintendo 3DS titles directly from the cloud with zero hassle and seamless dual-screen presentation.

</div>

> [!IMPORTANT]
> **Legal Notice & Disclaimer**: This software is intended strictly for personal archiving, educational, and backup purposes. Only download, decrypt, or manage ROMs and digital titles for which you legally own a genuine physical copy. The authors and contributors do not host files, nor do they condone copyright infringement.

---

## 🌟 Key Highlights

### 📱 Tailored Dual-Screen Architecture
- **Top Display (1920×1080 Landscape AMOLED)**: Full-bleed hero art, technical metadata (Title ID, Product Code, SHA-256), live decryption speeds, one-tap emulator launch, and instant synchronised tab dashboards (including live storage/cache metrics).
- **Bottom Touchscreen (1080×1240 Clamshell AMOLED)**: Fast catalogue browsing with category chips, region filters, local ROM management, download queues, and in-depth configuration.

### 🕹️ Dual-Layer Handheld Controller Navigation
- **Zero-Touch Operation**: Seamlessly move between the bottom tab bar and content areas without ever touching the screen.
- **Tab-Aware Inputs**:
  - **On Browse Tab**: Left/Right navigates region filters (`All Regions`, `Europe`, `North America`, etc.); L1/R1 cycles categories (`Games`, `Updates`, `DLC`, `DSiWare`, `Videos`, `Extras`).
  - **On Library Tab**: Left/Right and L1/R1 cycles format filters (`ALL` ⟷ `CCI` ⟷ `ZCCI` ⟷ `3DS` ⟷ `CIA`).
  - **On Tab Bar**: Up, Down, and Button A all immediately enter content. Left/Right cycles tabs.
- **Auto-Dismissing Keyboard**: Software keyboard automatically collapses and clears focus on Search/Enter, search icon tap, filter chip selection, or item selection.

### ⚡ On-Device Decryption & Seekable .ZCCI Compression
- **Hardware-Accelerated Decryption**: Bundled ARM64 `libcia3ds.so` with embedded `seeddb.bin` and dynamic Nintendo CDN seed retrieval (`SeedFetcher`). Converts encrypted `.cia` files into ready-to-play `.cci` / `.3ds` dumps.
- **Native .ZCCI Compression (Z3DS)**: Frame-by-frame seekable Zstandard compression (`zstd-jni`). Saves **40%–75% SD card space** while preserving instantaneous random access for **AzaharPlus**.
- **Auto-Pipeline**: Download `.cia` ➔ Decrypt to `.cci` ➔ Compress to `.zcci` ➔ Launch into emulator automatically.

### 💾 Hardened Storage & High-Performance Caching
- **Pre-Flight Storage Checks**: Drive space is validated before network downloads and before `.cci` decryptions to prevent out-of-storage crashes and half-written files.
- **Atomic Staging**: Downloads stream to `.download` temporary files and are promoted to `.cia` only after 100% completion. Interrupted or cancelled tasks are cleanly removed.
- **Symlink & Inode Deduplication**: Fully canonicalized scanning resolves Android FUSE casing variations (`Roms` vs `ROMs`) and symlinks (`/sdcard` vs `/storage/emulated/0`), guaranteeing unique titles.
- **Predictive Artwork Pre-fetching & LRU Metadata**: Coil 256MB disk cache + bounded 300-entry LRU title cache ensures instantaneous (0ms) browsing and reloading with zero memory leaks.
- **One-Tap Cache Wiping**: In-app Storage & Cache Management card in Settings displays live internal/ROM drive space and allows one-tap cache clearing.

### 🚀 Direct Emulator Launch & Seamless Handoff
- Supports **AzaharPlus**
- Uses Android `FileProvider` with automatic `Presentation` display release, ensuring the secondary screen is cleanly handed over to the emulator during gameplay.

---

## 🎮 Gamepad Controls

### Tab Bar Focused
| Button / Input | Action | Function |
| :--- | :--- | :--- |
| **D-Pad Left / Right** (or **Left Stick Left/Right**) | **Switch Tabs** | Cycle through `Browse` ⟷ `Library` ⟷ `Downloads` ⟷ `Settings` |
| **Button A** / **D-Pad Up** / **D-Pad Down** | **Enter Content** | Jump directly into the active tab's list/controls |
| **Button B** | **Back** | Clear active input or dismiss overlays |

### Content Focused
| Button / Input | Tab | Function |
| :--- | :--- | :--- |
| **D-Pad Up / Down** (or **Left Stick**) | *All Tabs* | Scroll through games, local ROMs, download tasks, or settings |
| **D-Pad Left / Right** | **Browse** | Switch region filters (`All Regions`, `Europe`, `North America`, etc.) |
| **D-Pad Left / Right** | **Library** | Cycle format filter chips (`ALL`, `CCI`, `ZCCI`, `3DS`, `CIA`) |
| **L1 / R1** (or **L2 / R2**) | **Browse** | Cycle categories (`Games`, `Updates`, `DLC`, `DSiWare`, `Videos`, `Extras`) |
| **L1 / R1** (or **L2 / R2**) | **Library** | Cycle format filter chips (`ALL`, `CCI`, `ZCCI`, `3DS`, `CIA`) |
| **Button A** (or **Enter**) | *All Tabs* | Trigger primary action (Download / Launch Game / Decrypt) |
| **Button B** | *All Tabs* | **Return to Tab Bar** (never closes app or dismisses presentations) |
| **Button X** | **Library** | Quick-decrypt `.cia` to `.cci` or compress `.cci` to `.zcci` |

---

## 📥 Installation

Grab the latest pre-compiled APK from the [**GitHub Releases**](https://github.com/nq-910/hshop/releases) page.

- **Direct Install**: Download the `.apk` directly onto your AYN Thor and open it with any file manager.
- **Via ADB (PC / Mac / Linux)**:
  ```bash
  adb install -r hshop-thor-v0.0.4-beta.apk
  # Or install whichever version was downloaded:
  # adb install -r hshop-thor-*.apk
  ```

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat (or Android SDK 35)
- JDK 17+
- Kotlin 2.0+

```bash
# Clone the repository
git clone https://github.com/nq-910/hshop.git
cd hshop

# Build Debug or Release APK
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# Install directly on connected AYN Thor
./gradlew :app:installDebug
```

---

## 🏗️ Architecture & Modules

```
hshop/
├── app/                        # Main Android Application (Jetpack Compose)
│   ├── src/main/assets/        # gametdb.db, 3dstdb.xml, seeddb.bin & cdn-nintendo-leaf.pem
│   ├── src/main/jniLibs/       # Native libcia3ds.so (ARM64, ARMv7, x86_64)
│   └── src/main/kotlin/
│       ├── io/github/cia3ds/   # JNI Decryption Bindings & SeedFetcher
│       └── me/erista/hshop/thor/
│           ├── compressor/     # Seekable Z3DS (.ZCCI) Stream Compressor
│           ├── converter/      # CIA to CCI Decryption Pipeline
│           ├── data/           # GameTdbRepository, DownloadModels & Persistence
│           ├── download/       # ThorDownloadManager & Turnstile Solver
│           ├── presentation/   # Dual-Screen Presentation Controller
│           ├── ui/             # Top/Bottom Dual-Screen UI & ViewModels
│           └── util/           # StorageUtils, GameLauncher & SAF Integrations
│
├── core-scraper/               # Multiplatform Scraper & Metadata Parser
└── docs/                       # Architectural & Technical Specifications
```

---

## 📚 Documentation

Detailed subsystem specifications are available in the [`docs/`](docs/) directory:
- [**Dual-Screen Architecture**](docs/dual_screen_architecture.md)
- [**Decryption & Cryptography**](docs/decryption_and_cryptography.md)
- [**Metadata & Box Art Pipeline**](docs/metadata_and_boxart.md)
- [**Seekable .ZCCI Compression**](docs/zcci_compression.md)
- [**Storage & Cache Architecture**](docs/storage_and_cache.md)
- [**Gamepad & Input Navigation**](docs/input_and_gamepad.md)
- [**Emulator Integration & FileProvider**](docs/emulator_integration.md)
- [**Turnstile & Download Pipeline**](docs/turnstile_and_downloads.md)

---

## ⚖️ Legal Disclaimer

- **Personal Backups Only**: This project is developed strictly for educational, research, and personal archival purposes. You must **only download, decrypt, or manage ROMs and digital titles for which you own a legally acquired physical cartridge or genuine copy**.
- **No Hosting / Distribution**: The developers and contributors do not host, store, or distribute any copyrighted files, software, or ROMs.
- **Intellectual Property**: All trademarks, system names, and game titles are the intellectual property of Nintendo Co., Ltd. and their respective copyright holders. This project is not affiliated with, endorsed by, or sponsored by Nintendo.

---

## 📄 License & Credits

- Upstream decryption routines based on Project_CTR (`ctrtool`, `makerom`).
- Seekable `.zcci` specification compatible with [AzaharPlus](https://github.com/AzaharPlus/AzaharPlus).
- Built with ❤️ for the handheld emulation community.

