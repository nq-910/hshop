# 📚 hShop Thor Documentation Index

Welcome to the technical documentation for **hShop Thor**. This directory contains detailed architectural specifications, subsystem documentation, and operational guides.

---

## 📑 Documentation Overview

1. [**Dual-Screen Architecture (`dual_screen_architecture.md`)**](dual_screen_architecture.md)  
   Details on Android `DisplayManager`, presentation lifecycle, SurfaceFlinger ID mapping, and Jetpack Compose split layout logic for AYN Thor.

2. [**Decryption & Cryptography Subsystem (`decryption_and_cryptography.md`)**](decryption_and_cryptography.md)  
   Technical breakdown of `libcia3ds.so` (Project_CTR `ctrtool` and `makerom`), AES hardware acceleration, `seeddb.bin` format, JNI bindings, and the `SeedFetcher` HTTPS client.

3. [**Input & Gamepad Mapping Guide (`input_and_gamepad.md`)**](input_and_gamepad.md)  
   Comprehensive guide to the `Odin Controller` hardware mapping, D-Pad/analog axis deadzones, and accessibility navigation.

4. [**Turnstile & Download Pipeline (`turnstile_and_downloads.md`)**](turnstile_and_downloads.md)  
   Documentation on the Cloudflare Turnstile automated solver, `core-scraper` multiplatform module, and `ThorDownloadManager` coroutine download state machine.

5. [**Seekable .ZCCI Compression (`zcci_compression.md`)**](zcci_compression.md)  
   Technical specification for the `Z3DS` seekable Zstandard format, binary seek table structure, and AzaharPlus compatibility.

6. [**Emulator Launching & FileProvider (`emulator_integration.md`)**](emulator_integration.md)  
   Intent routing, URI permissions, presentation lifecycle release, and configuration for **Azahar**, **Lime3DS**, and **Citra**.

7. [**Storage & Cache Architecture (`storage_and_cache.md`)**](storage_and_cache.md)  
   Pre-flight storage checks, atomic staging with `.download` files, LRU and Coil cache policies, and cache clearing.

