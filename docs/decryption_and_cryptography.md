# 🔐 Decryption & Cryptography Subsystem

## Overview
hShop distributes titles in the **CIA (CTR Importable Archive)** container format. CIAs contain NCCH partitions encrypted with hardware TitleKeys. To make these ROMs loadable directly in emulators like **Lime3DS**, **Citra**, and **Azahar**, the app bundles a native ARM64 decryption pipeline.

---

## Architecture Flow

```
[ .CIA File (Encrypted) ]
           │
           ▼
[ libcia3ds.so Engine ]
     ├── 1. Parse CIA Header & Ticket (TIK)
     ├── 2. Decrypt TitleKey via 3DS Common Key (Slot 0x3D)
     ├── 3. Extract NCCH partitions via ctrtool
     ├── 4. Apply Seed Crypto (from seeddb.bin or SeedFetcher CDN)
     ├── 5. Patch NCCH Crypto Flags to 0x00 (Plaintext)
     └── 6. Rebuild into NCSD Cartridge (.CCI / .3DS) via makerom
           │
           ▼
[ Decrypted .CCI / .3DS Cartridge ROM ]
```

---

## JNI Binding Specification

```kotlin
package io.github.cia3ds.jni

class Cia3ds(private val context: Context) {
    external fun nativeDecryptCia(
        inFd: Int,                          // Source file descriptor
        outFd: Int,                         // Output file descriptor
        seedDbPath: String,                 // Path to extracted seeddb.bin
        tmpDir: String,                     // Application private scratchpad directory
        originalName: String,               // Original filename
        wantCci: Boolean,                   // True for .cci cartridge, False for .cia
        progressCallback: NativeProgressCallback?,
        logCallback: NativeLogCallback?,
        seedFetcher: NativeSeedFetcherCallback?
    ): Int
}
```

### Critical Implementation Notes:
1. **No Null `jstring`s**: All `String` parameters to JNI must be non-null (pass empty string `""` if optional).
2. **`tmpDir` Scratchpad**: Must point to `context.cacheDir.resolve("cia3ds-work")` with `mkdirs()` called prior to invocation.
3. **`wantCci` Flag**: Passing `true` creates an NCSD cartridge container (binary-identical to `.3ds` and directly indexed by Lime3DS).
4. **SeedFetcher Fallback**: When encountering post-2015 Seed Crypto titles not in `seeddb.bin`, `SeedFetcher.kt` queries `https://kagiya-ctr.cdn.nintendo.net/title/0x{TID}/ext_key` using `cdn-nintendo-leaf.pem`.
