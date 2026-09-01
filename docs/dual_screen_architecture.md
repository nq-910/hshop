# 🖥️ Dual-Screen Architecture (AYN Thor)

## Hardware Overview
The **AYN Thor** features two distinct physical AMOLED screens connected to the Qualcomm Snapdragon 8 Gen 2 platform:

```
+-------------------------------------------------------------+
|                                                             |
|                   TOP SCREEN (Display 0)                    |
|             1920 x 1080 Landscape • 120Hz AMOLED            |
|                  Primary Presentation Hero                  |
|                                                             |
+-------------------------------------------------------------+
                              |
                     [ Clamshell Hinge ]
                              |
                    +-------------------+
                    |                   |
                    |   BOTTOM SCREEN   |
                    |    (Display 4)    |
                    |    1080 x 1240    |
                    |  120Hz Touchscreen|
                    |                   |
                    +-------------------+
```

---

## Display Lifecycle Management

1. **`DisplayManager` Listener**:
   - `MainActivity` registers a `DisplayListener` in `onCreate()` to detect secondary screen attachment, detachment, or configuration changes dynamically.
2. **`ThorBottomPresentation`**:
   - Extends Android's native `android.app.Presentation(context, display)`.
   - When Display `4` (or any display flagged with `DISPLAY_CATEGORY_PRESENTATION`) is detected, `MainActivity` instantiates and shows `ThorBottomPresentation`.
3. **Single-Screen Fallback**:
   - If no secondary display is present (e.g. running in standard Android emulator or standard phone), `MainActivity` automatically collapses both top and bottom views into a single vertically split `Dual-Pane` layout.

---

## Jetpack Compose State Synchronization

Both screens share the unified `MainViewModel` scoped to `MainActivity`:
- **Bottom Screen**: Dispatches search queries, category selections, region filtering, and title focus changes.
- **Top Screen**: Collects `selectedTitleDetail`, `downloadTasks`, and `statusMessage` flows, updating boxart and download progress with zero IPC latency.
