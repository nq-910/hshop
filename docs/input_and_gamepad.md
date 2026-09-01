# 🎮 Input & Gamepad Mapping Guide

## Hardware Device
The AYN Thor uses the **Odin Controller** Linux input device (`/dev/input/event9`).

---

## Control Mapping Reference

```
  [ L1 / L2 ]                                                [ R1 / R2 ]
  (Prev Category)                                          (Next Category)

       ▲                                                          (X) [Decrypt .CCI]
   ◄   ┼   ►                                            (Y)                (A) [Download / Play]
  (Prev/Next Region)                                   [Cycle Tabs]
       ▼                                                          (B) [Back to Browse]
(Scroll Titles)
                   [ SELECT ]               [ START ]
                  (Cycle Tabs)
```

---

## Technical Handling in `MainActivity.kt`

- **Key Events (`onKeyDown`)**: Intercepts `KEYCODE_DPAD_*`, `KEYCODE_BUTTON_*`, and `KEYCODE_ENTER` to route actions directly into `MainViewModel`.
- **Motion Events (`onGenericMotionEvent`)**: Reads `AXIS_HAT_X`, `AXIS_HAT_Y`, `AXIS_X`, and `AXIS_Y` with a `0.5f` deadzone threshold for responsive analog stick navigation.
- **Auto-Scroll Synchronization**: `BottomScreenContent.kt` uses a `rememberLazyListState()` linked with `LaunchedEffect(selectedTitleDetail?.id)` to animate the list viewport whenever the selection changes via D-Pad or thumbstick.
