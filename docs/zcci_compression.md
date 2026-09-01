# 🗜️ Seekable .ZCCI Compression & Storage Architecture

**hShop Thor** features native support for compressing decrypted `.cci` / `.3ds` Nintendo 3DS cartridge dumps into seekable `.zcci` files (`Z3DS` specification).

---

## 💡 What is `.ZCCI` / `Z3DS`?

Standard `.zip` or `.7z` archives cannot be played directly by emulators without extracting the entire multi-gigabyte cartridge to disk first.

The `Z3DS` / `.zcci` format (used natively by **AzaharPlus**) is a **seekable Zstandard stream format**:
- Compresses the cartridge in **256 KiB independent frames**.
- Stores a seek table (`0x184D2A5E` frame) at the end of the file containing frame offsets and uncompressed block offsets.
- Allows emulators to seek to arbitrary file positions and decompress only the requested 256 KiB blocks into RAM in real-time.
- Reduces file size by **40% to 75%** with **zero loading lag or decompression penalty during gameplay**.

---

## ⚙️ Binary Format Specification

```
+-------------------------------------------------------------+
| Header (32 Bytes):                                          |
|   - Magic: 'Z3DS' (0x5344335A)                              |
|   - Underlying Magic: 'NCSD' (0x4453434E)                   |
|   - Version: 1                                              |
|   - Compressor String: 'zstd'                               |
|   - Date String: (ISO timestamp)                            |
|   - maxframesize: 262144 (256 KiB)                          |
+-------------------------------------------------------------+
| Body:                                                       |
|   [ Zstandard Frame 0 (<= 256 KiB uncompressed) ]           |
|   [ Zstandard Frame 1 (<= 256 KiB uncompressed) ]           |
|   ...                                                       |
|   [ Zstandard Frame N ]                                     |
+-------------------------------------------------------------+
| Trailer / Seek Table (Skippable Frame):                     |
|   - Skippable Frame Magic: 0x184D2A5E                       |
|   - Table Size (4 bytes uint32)                             |
|   - Frame Sizes Table:                                      |
|       [ uint32 compressed_size, uint32 uncompressed_size ]  |
|   - Seek Table Magic: 0x8F92EAB1                            |
+-------------------------------------------------------------+
```

---

## 🛠️ Usage in hShop Thor

1. **Auto-Compress on Download**:
   - Go to the **Settings** tab and toggle **Auto-Compress to .ZCCI (AzaharPlus)**.
   - When a `.cia` base game finishes downloading, hShop automatically decrypts it to `.cci`, compresses it to `.zcci`, and removes the temporary uncompressed `.cci` file.
2. **On-Demand Compression**:
   - Open the **Library** tab on the bottom touchscreen.
   - Any uncompressed `.cci` cartridge will display a **"Compress (.ZCCI)"** action button.
   - Live progress is reported on the top display status bar.
