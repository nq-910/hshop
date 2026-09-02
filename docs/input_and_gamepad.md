# 🎮 Input & Gamepad Navigation Guide

## Hardware Device
The AYN Thor uses the **Odin Controller** Linux input device (`/dev/input/event9`).

---

## Control Mapping Reference

```
  [ L1 / L2 ]                                                [ R1 / R2 ]
  (Cycle Filter/Category)                                   (Cycle Filter/Category)

       ▲                                                          (X) [Quick Decrypt / Compress]
   ◄   ┼   ►                                            (Y)                (A) [Enter / Select / Download]
  (Tab-Aware Left/Right)                               (Unused)
       ▼                                                          (B) [Return to Tabs / Back]
  (Scroll / Navigate)
                   [ SELECT ]               [ START ]
```

---

## 🧭 Dual-Layer Navigation Mechanic

The UI implements a responsive dual-layer focus model tailored for handheld navigation without needing touchscreen interaction:

### Layer 1: Bottom Navigation Bar (Tabs)
* **Active Indicator**: A green focus border surrounds the bottom tab bar, with a highlighted pill on the active tab (`Browse` • `Library` • `Downloads` • `Settings`).
* **Controls**:
  - **D-Pad Left / Right** (or **Left Stick Left/Right**): Cycles between tabs.
  - **Button A**, **D-Pad Up**, **D-Pad Down**, or **Left Stick Up/Down**: Enters the content area of the highlighted tab.
  - **Button B**: Exits active search or returns.

### Layer 2: Content Area (In-Tab)
* **Controls**:
  - **D-Pad Up / Down** (or **Left Stick Up/Down**): Navigates through title cards, ROMs, download tasks, or settings with animated auto-scroll.
  - **D-Pad Left / Right** (Tab-Aware):
    - **Browse Tab**: Switches region / subcategory filters (`All Regions`, `Europe`, `North America`, etc.).
    - **Library Tab**: Cycles through format filter chips (`ALL` ⟷ `CCI` ⟷ `ZCCI` ⟷ `3DS` ⟷ `CIA`).
    - **Downloads & Settings Tabs**: Clean no-op (no background network requests or unintended state changes).
  - **L1 / R1 / L2 / R2 Shoulder Buttons**:
    - **Browse Tab**: Cycles main categories (`Games`, `Updates`, `DLC`, `DSiWare`, `Videos`, `Extras`).
    - **Library Tab**: Cycles format filter chips.
  - **Button A**: Triggers the primary action on the selected item (Download game, Launch ROM, etc.).
  - **Button B**: Returns focus to the bottom navigation bar without dismissing presentations or exiting the app.
  - **Button X**: Triggers native `.cia` decryption or `.zcci` compression on the selected library ROM.

---

## ⌨️ Software Keyboard Auto-Dismissal

When interacting with search fields on the bottom touchscreen:
1. **On Search / Enter (`ImeAction.Search`)**: Submitting a search automatically hides the on-screen keyboard and clears text focus.
2. **On Search Icon or Clear (✕) Click**: Automatically hides the keyboard.
3. **On Item Selection**: Selecting any game, format chip, or category chip immediately dismisses the keyboard.
4. **On Navigating Back to Tabs**: Returning focus to the tab bar (via Button B or D-Pad) automatically hides the keyboard.
