# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ANCSwitch is a personal Android app that switches a OnePlus Buds 4's ANC/Transparency/Off
mode directly — via a Quick Settings tile or the app's own screen — without opening the
official HeyMelody app. It talks to the earbuds over classic Bluetooth RFCOMM using a
protocol reverse-engineered from HeyMelody's decompiled source plus a live HCI packet
capture. Read `PROTOCOL_NOTES.md` before touching anything in `BudsProtocol.kt` or
`BudsConnection.kt` — it documents the wire format, and several of its findings
**contradict** what the decompiled HeyMelody source implies (see below).

## Commands

- Build debug: `./gradlew assembleDebug`
- Build + install debug on a connected/adb-connected device: `./gradlew installDebug`
- Build + install release (R8-shrunk, signed with the debug keystore): `./gradlew installRelease`
- Unit tests (host JVM): `./gradlew test`
- Single unit test: `./gradlew test --tests "com.hrithikvish.ancswitch.ExampleUnitTest.addition_isCorrect"`
- Instrumented tests (needs a connected device/emulator): `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint` (report under `app/build/reports/`)

There's one module, `:app`. No CI config in the repo.

## Architecture

Three entry points into the same Bluetooth logic, each a thin UI over the shared
connection/protocol layer:

- **`MainActivity.kt`** — manual test screen (Compose): lists bonded devices, connect,
  send any of the 5 raw `AncMode`s, view the raw TX/RX log. This is the debug surface;
  it shows the full unfiltered protocol table.
- **`AncTileService.kt`** — the Quick Settings tile. **Never opens a Bluetooth connection
  itself** — it only reads the last-saved mode from `SharedPreferences` and reflects it
  in the tile's label/state. Tapping it launches `AncModePickerActivity` via
  `startActivityAndCollapse`.
- **`AncModePickerActivity.kt`** — the floating picker dialog launched from the tile.
  This is the one component that owns a live `BudsConnection`: connects on `onCreate`,
  disconnects on `onDestroy`. After sending a mode it persists it to `SharedPreferences`
  (keys in `AncTileService.PREFS_NAME`/`KEY_LAST_MODE`) and calls
  `TileService.requestListeningState()` to nudge the tile to refresh immediately, since a
  tile only repaints on its own `onStartListening()` lifecycle otherwise.

**Only one of these should hold the RFCOMM socket at a time.** Earlier the tile also
eagerly connected in `onStartListening()`, which raced against the picker's connect and
intermittently failed with the same "read failed" SDP error described below — that's why
the tile is now connection-free by design. Don't reintroduce a connection there without
re-solving that race.

Shared logic:

- **`BudsProtocol.kt`** — pure protocol: the SDP UUID, the `AncMode` enum (label + wire
  bitmask), inner frame build/parse (`cmd/seq/len` header), and the outer envelope
  wrap/unwrap (`0xAA` + length + flags/reserved) that every frame must be wrapped in.
- **`BudsConnection.kt`** — owns one `BluetoothSocket`. `connect()` spins a background
  thread that opens the RFCOMM socket (falls back secure→insecure on failure) and runs a
  read loop; `sendMode()` writes on its own thread. All `Listener` callbacks
  (`onLog`/`onConnected`/`onDisconnected`/`onModeRead`) are posted back to the main thread
  by the class itself — callers never need to hop threads.
- **`BudsUtil.kt`** — shared helpers used by the tile and picker: permission check,
  finding the actively-connected (not just bonded) Buds device via the A2DP profile proxy
  (async — takes a callback), the tile/picker display-label override for `ANC_WEAK`
  ("Noise Cancellation"), and reading the persisted last-sent mode.

### Protocol facts that look wrong but aren't

`PROTOCOL_NOTES.md` §2–§4 has the full derivation; the short version, because these are
easy to "fix" back to the wrong value by trusting the decompiled app source over the
comments in this repo:

- The SDP UUID (`0000079a-...`) is a **per-device server-pushed override**, not the
  `00001107-...` fallback constant HeyMelody's code falls back to. It was confirmed by
  reading the live SDP record off the actual bonded device, not from source.
- Every frame needs the outer `0xAA` envelope wrapper before the inner `cmd/seq/len`
  frame — this isn't in HeyMelody's higher-level UI code at all; it only showed up in an
  HCI snoop capture and in `X7/C1526b.java`/`y7/C1545a.java` in the decompile.
- The mode→bitmask table (`BudsProtocol.AncMode`) was corrected by physically testing
  each bit against the earbuds. It **disagrees** with what HeyMelody's decompiled
  app-layer arithmetic computes for the same bits (confirmed identical/wrong in two
  separate HeyMelody versions, 116.7.0 and 116.9.0) — why is unresolved. Trust the
  enum's current values, not a fresh re-derivation from source.
- `ANC_STRONG` (`0x08`) and `ANC_ADAPTIVE` (`0x10`) have never been physically verified —
  only `OFF`, `ANC_WEAK`, and `TRANSPARENCY` have confirmed real-world effects.

If you need to re-derive any of this (e.g. for a different earbuds model), the method
that worked: enable Bluetooth HCI snoop logging (Developer options → Networking →
Bluetooth → "Enabled", not "Enabled Filtered"), reproduce the traffic, pull it via
`adb bugreport` (the log is at `FS/data/misc/bluetooth/logs/btsnoop_hci.log` inside the
zip — avoids needing root), then decode the BTSnoop format directly. Full writeup in
`PROTOCOL_NOTES.md` §6.

### Build config notes

- `app/build.gradle.kts` uses AGP 9's newer `optimization { enable = true }` DSL for the
  release build type (not the old `minifyEnabled`) — this requires the
  `android.r8.gradual.support=true` flag in `gradle.properties`.
  R8 keep rules go in `app/src/main/keepRules/` (AGP combines all files there), not a
  `proguard-rules.pro` referenced from the build script.
- Release is signed with the debug keystore purely so `installRelease` works for local
  device testing — there's no real signing config.
