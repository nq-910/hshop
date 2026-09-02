# 💾 Storage & Cache Architecture

This document describes the storage model, caching policies, pre-flight validation safeguards, and temporary file lifecycles in **hShop Thor**.

---

## 1. Directory Structure & File Paths

hShop Thor handles three main classes of files:

| File Class | Location | Purpose | Persistence |
| :--- | :--- | :--- | :--- |
| **ROM Storage** | User configured (default: `/sdcard/ROMs/3DS` or `/sdcard/ROMs/n3ds`) | Main `.cci`, `.zcci`, and `.3ds` decrypted game dumps | Permanent |
| **Updates & DLC** | User configured (default: `<ROMs>/Updates_DLC`) | Unencrypted `.cia` update and DLC packages for emulator installation | Permanent |
| **App Internal Cache** | `/data/user/0/me.erista.hshop.thor/cache` | Coil image disk cache and `cia3ds-work` extraction scratch space | Evictable / Managed |

---

## 2. Pre-Flight Storage Validation

To eliminate mid-stream disk-full crashes (`ENOSPC`) and corrupt half-written files, hShop Thor performs pre-flight storage validation before initiating IO-heavy tasks:

### A. Download Space Checks (`ThorDownloadManager.kt`)
* Before starting a remote HTTP stream, `ThorDownloadManager` queries `StorageUtils.getUsableSpace(targetDirectory)`.
* Compares available drive space against the title's content length + a **50 MB safety margin**.
* If space is insufficient, the task immediately halts with status `DownloadStatus.OUT_OF_STORAGE` and shows an `OutOfStorageDialog` on the top display, before creating any files on disk.

### B. Decryption Space Checks (`Cia3ds.kt`)
* Before extracting partitions from an encrypted `.cia`, two checks are verified:
  1. **Internal Cache Scratch Space**: Ensures `context.cacheDir` has at least `ciaLength + 30 MB` for temporary partition extraction.
  2. **Target Destination Space**: Ensures the target directory has at least `ciaLength * 1.05 + 50 MB` for the finished unencrypted `.cci`.
* If either check fails, an `OutOfStorageException` is thrown and caught cleanly by the UI.

---

## 3. Atomic Downloads & Temp File Lifecycle

To prevent corrupt, partial, or interrupted downloads from appearing in the user's library:

1. **Staged Download Extension**: Active network streams are written to `${targetFilePath}.download`.
2. **Atomic Promotion**: Only upon 100% completion of the network stream and checksum verification is the file atomically promoted:
   ```kotlin
   tempDownloadFile.renameTo(finalCiaFile)
   ```
3. **Guaranteed Cleanup on Cancellation/Failure**: If a download is cancelled by the user, fails due to network disconnection, or encounters an out-of-storage condition, the `.download` staging file is **immediately deleted**.
4. **Decryption Cleanup**: If `.cci` conversion fails or is cancelled, any incomplete output file is automatically deleted so storage is never leaked.

---

## 4. Multi-Tier Cache Management

### A. Title Metadata LRU Cache (`MainViewModel.kt`)
* `titleDetailCache` stores parsed hShop title metadata and full descriptions.
* Uses a synchronized **Bounded LRU Cache** capped at **300 items**:
  ```kotlin
  private val titleDetailCache = Collections.synchronizedMap(
      object : LinkedHashMap<String, HShopTitleDetail>(128, 0.75f, true) {
          override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HShopTitleDetail>?): Boolean {
              return size > 300
          }
      }
  )
  ```
* Navigating between previously viewed titles requires **0ms network delay** and **zero memory leaks** over long gaming sessions.

### B. Predictive Artwork Pre-fetching (`HShopApplication.kt`)
* Coil `ImageLoader` configured with:
  - **Memory Cache**: Up to 25% of available JVM heap.
  - **Disk Cache**: Capped at **256 MB** in `cacheDir/image_cache`.
  - Permanent cache policy (`respectCacheHeaders(false)`).
* When a title is selected in the catalogue, background coroutines predictively pre-fetch:
  - Thumbnails for the next 6 upcoming titles.
  - High-resolution hero covers for the next 3 upcoming titles.
  - Full metadata for the adjacent 2 titles.

---

## 5. Storage & Cache Health UI in Settings

The Settings tab provides a dedicated **Storage & Cache Management** dashboard:
* **Internal Storage Free Space**: Monitored in real-time.
* **ROM Storage Free Space**: Monitored on the target internal or external SD path.
* **App Cache Usage**: Live byte counter across image cache and temporary scratch files.
* **One-Tap "Clear Cache" Button**:
  - Recursively wipes `context.cacheDir`.
  - Clears Coil in-memory bitmap cache.
  - Resets `titleDetailCache`.
  - Reclaims disk space without affecting installed games or settings.
