<div align="center">

# 🎮 hShop Thor

**A high-performance, dual-screen native Android client designed exclusively for the AYN Thor handheld console.**

[![Release](https://img.shields.io/github/v/release/yggdrasil-seed/hshop?style=for-the-badge&color=2ecc71&logo=android)](https://github.com/yggdrasil-seed/hshop/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Platform](https://img.shields.io/badge/Target-AYN%20Thor-00E5FF?style=for-the-badge&logo=nintendo3ds&logoColor=white)](https://www.ayntec.com/)
[![License](https://img.shields.io/badge/License-GPLv3%2FMIT-orange?style=for-the-badge)](LICENSE)

<br/>

Browse, download, decrypt, compress, and launch Nintendo 3DS titles directly from the cloud with zero hassle and seamless dual-screen presentation.

</div>

---

## 🌟 Key Highlights

### 📱 Tailored Dual-Screen Architecture
- **Top Display (1920×1080 Landscape AMOLED)**: Full-bleed hero art, technical metadata (Title ID, Product Code, SHA-256), live decryption speeds, one-tap emulator launch, and ROM file deletion.
- **Bottom Touchscreen (1080×1240 Clamshell AMOLED)**: Fast catalogue browsing with category chips, region filters, local ROM management, download queues, and in-depth configuration.

### ⚡ On-Device Decryption & Seekable .ZCCI Compression
- **Hardware-Accelerated Decryption**: Bundled ARM64 `libcia3ds.so` with embedded `seeddb.bin` and dynamic Nintendo CDN seed retrieval (`SeedFetcher`). Converts encrypted `.cia` files into ready-to-play `.cci` / `.3ds` dumps.
- **Native .ZCCI Compression (Z3DS)**: Frame-by-frame seekable Zstandard compression (`zstd-jni`). Saves **40%–75% SD card space** while preserving instantaneous random access for **AzaharPlus**.
- **Auto-Pipeline**: Download `.cia` ➔ Decrypt to `.cci` ➔ Compress to `.zcci` ➔ Launch into emulator automatically.

### 🕹️ Native Handheld Controller Integration
Full hardware input support for the AYN Thor gamepad including D-Pad navigation, analog sticks, face buttons, shoulder triggers, and hotkeys.

### 🚀 Direct Emulator Launch & Seamless Handoff
- Supports **Azahar / AzaharPlus**, **Lime3DS**, and **Citra**.
- Uses Android `FileProvider` with automatic `Presentation` display release, ensuring the secondary screen is cleanly handed over to the emulator during gameplay.

---

## 🎮 Gamepad Controls

| Button | Action | Function |
| :--- | :--- | :--- |
| **D-Pad Up / Down** (or **Left Stick**) | **Navigate** | Scroll through catalogue or local ROM list |
| **D-Pad Left / Right** | **Region** | Switch region filters (USA, EUR, JPN, etc.) |
| **L1 / R1** (or **L2 / R2**) | **Category** | Cycle categories (Games, Updates, DLC, VC, DSiWare) |
| **Button A** (or **Enter**) | **Confirm** | Download title / select local item |
| **Button B** (or **Back**) | **Back** | Dismiss overlays / return to Browse tab |
| **Button X** | **Decrypt** | Manually decrypt selected `.cia` |
| **Button Y** (or **Select**) | **Tabs** | Cycle tabs (`Browse` ➔ `Library` ➔ `Downloads` ➔ `Settings`) |

---

## 📥 Installation

Grab the latest pre-compiled APK from the [**GitHub Releases**](https://github.com/yggdrasil-seed/hshop/releases) page.

```bash
# Install via ADB directly to your AYN Thor
adb install -r hshop-thor-v0.0.2-beta.apk
```

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat (or Android SDK 35)
- JDK 17+
- Kotlin 2.0+

```bash
# Clone the repository
git clone https://github.com/yggdrasil-seed/hshop.git
cd hshop

# Build Release APK
./gradlew :app:assembleRelease

# Install directly on connected AYN Thor
./gradlew :app:installRelease
```

---

## 🏗️ Architecture & Modules

```
hshop/
├── app/                        # Main Android Application (Jetpack Compose)
│   ├── src/main/assets/        # seeddb.bin & cdn-nintendo-leaf.pem
│   ├── src/main/jniLibs/       # Native libcia3ds.so (ARM64, ARMv7, x86_64)
│   └── src/main/kotlin/
│       ├── io/github/cia3ds/   # JNI Decryption Bindings & SeedFetcher
│       └── me/erista/hshop/thor/
│           ├── compressor/     # Seekable Z3DS (.ZCCI) Stream Compressor
│           ├── converter/      # CIA to CCI Decryption Pipeline
│           ├── data/           # AppSettings, DownloadModels & Persistence
│           ├── download/       # ThorDownloadManager & Turnstile Solver
│           ├── presentation/   # Dual-Screen Presentation Controller
│           ├── ui/             # Top/Bottom Split UI & Themes
│           └── util/           # GameLauncher & SAF Storage Utilities
│
├── core-scraper/               # Multiplatform Scraper & Metadata Parser
└── docs/                       # Architectural & Technical Specifications
```

---

## 📚 Documentation

Detailed subsystem specifications are available in the [`docs/`](docs/) directory:
- [**Dual-Screen Architecture**](docs/dual_screen_architecture.md)
- [**Decryption & Cryptography**](docs/decryption_and_cryptography.md)
- [**Seekable .ZCCI Compression**](docs/zcci_compression.md)
- [**Emulator Integration & FileProvider**](docs/emulator_integration.md)
- [**Gamepad & Input Mapping**](docs/input_and_gamepad.md)
- [**Turnstile & Download Pipeline**](docs/turnstile_and_downloads.md)

---

## 📄 License & Credits

- Upstream decryption routines based on Project_CTR (`ctrtool`, `makerom`).
- Seekable `.zcci` specification compatible with [AzaharPlus](https://github.com/AzaharPlus/AzaharPlus).
- Built with ❤️ for the handheld emulation community.
