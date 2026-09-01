# 🚀 Emulator Integration & FileProvider

## Supported Emulators

| Emulator | Package Name | Format |
| :--- | :--- | :--- |
| **Azahar / AzaharPlus** | `org.azahar_emu.azahar`, `dev.twilitrealm.dusk` | `.zcci`, `.cci`, `.3ds`, `.cia` |
| **Lime3DS** | `io.github.lime3ds.android` | `.cci`, `.3ds`, `.cxi`, `.cia` |
| **Citra** | `org.citra.citra_emu` | `.cci`, `.3ds`, `.cia` |

---

## Dual-Screen Display Lock & Presentation Dismissal

On the **AYN Thor**, Display ID 4 (Bottom Touchscreen) is an Android `Presentation` display. Only **one application** can bind a `Presentation` dialog or window to Display 4 at any given time.

Before launching an external emulator, `MainActivity` invokes `dismissBottomPresentation()` synchronously:
```kotlin
fun launchGame(file: File) {
    // 1. Release Display 4 Presentation immediately
    mainActivity?.dismissBottomPresentation()

    // 2. Launch emulator intent with read permissions
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
```
When returning from the emulator to hShop, `MainActivity.onResume()` automatically re-initializes and re-binds the bottom presentation.

---

## FileProvider Configuration

In `AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

In `res/xml/file_paths.xml`:
```xml
<paths>
    <external-path name="external_files" path="." />
    <external-files-path name="external_app_files" path="." />
    <files-path name="internal_files" path="." />
    <cache-path name="internal_cache" path="." />
</paths>
```

---

## Launch Intent Execution (`GameLauncher.kt`)

```kotlin
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/octet-stream")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(Intent.createChooser(intent, "Play with 3DS Emulator"))
```
