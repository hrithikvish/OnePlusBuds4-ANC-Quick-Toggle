# OnePlus Buds 4 — ANC Mode Switching Protocol

Reverse-engineered from the HeyMelody APK (`com.heytap.headset`, OPPO/Heytap "Melody" SDK)
via `jadx` decompilation. Source of truth: `/Users/hrithikvish/Desktop/apks/HeyMelody/jadx_output/`.

This document is the reference for building **ANCSwitch** — a personal app that exposes a
Quick Settings Tile to switch the OnePlus Buds 4 between Off / Transparency / ANC (weak,
strong, adaptive) without opening HeyMelody.

---

## 1. Quick Settings Tile — HeyMelody has none

Confirmed by exhaustive grep across the entire decompiled manifest and source tree:
no `TileService`, no `QS_TILE` action, no `ACTIVE_TILE` meta-data, no Device Controls API
(`ControlsProviderService`) either. HeyMelody's "quick" controls are OEM-private
(`MelodyAliveService` broadcast + `HeadsetRpcMsgService` RPC), not the public Android tile API.

**Conclusion:** the tile is 100% our own build — nothing to port, just standard Android.

### Minimum tile setup

`AndroidManifest.xml`:
```xml
<service
    android:name=".AncTileService"
    android:label="ANC Mode"
    android:icon="@drawable/ic_anc_tile"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
    <!-- lets it show up as a suggested tile before first manual add -->
    <meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="true" />
</service>
```

`AncTileService extends TileService`:
- `onStartListening()` — ensure/refresh the BT RFCOMM connection, reflect current mode via `getQsTile().setLabel(...)`/`setState(...)`/`updateTile()`.
- `onClick()` — cycle to next mode (or launch a small translucent activity via `startActivityAndCollapse` if you want a 4-way picker instead of a cycle) and send the command.
- `onStopListening()` — optionally keep socket alive in a bound Service so state persists between shade opens (see §5).

Tiles cannot render custom layouts — only icon + label + active/inactive state. For a
4-mode picker on tap, launch a translucent `Activity` from `onClick()`.

User must manually add the tile once (long-press shade → edit tiles → drag in);
apps cannot self-pin tiles.

Min API for `TileService`: 24. `ACTIVE_TILE` / boolean state tiles work from API 24+;
richer states arrived later — target API 29+ to be safe.

---

## 2. Bluetooth transport

**Classic Bluetooth RFCOMM/SPP** — not BLE GATT. (BLE only used by HeyMelody for
connection bookkeeping/RSSI, not for sending mode commands.)

- Custom service UUID (not the generic SPP UUID `00001101-...`):
  ```
  00001107-D102-11E1-9B23-00025B00A5A5
  ```
  Source: `S7/AbstractC1383b.java:32`. HeyMelody allows a server-pushed per-device
  override (`SupportDeviceConfig.getUuid()`), but this is the default/fallback UUID
  used when no override is configured — start here.
- Device must already be **bonded** (standard Android pairing) before opening the socket.
- Socket creation: `device.createRfcommSocketToServiceRecord(uuid)`
  (`S7/AbstractC1382a.java:167`).
- Writes go straight to `socket.getOutputStream()` — `write(bytes); flush();`
  (`r7/C1356a.java`, method `d()`). No visible extra encryption/escaping at this layer.

---

## 3. Packet framing ("MMI"-style proprietary protocol)

5-byte header, **little-endian**, followed by payload:

```
[cmd_lo][cmd_hi][seq][len_lo][len_hi][payload...]
  byte0   byte1   b2    b3     b4      b5..
```

- `cmd` (2 bytes, LE) — command ID. `1028` (`0x0404`) = noise-reduction set/read.
- `seq` (1 byte) — rolling counter, tracked per device address (0–255, wraps).
- `len` (2 bytes, LE) — payload length in bytes.
- `payload` — command-specific bytes (see §4).

**Ack/response framing:** top bit of the (15-bit) command field is set to mark a response:
`response_cmd = (cmd & 0x7FFF) | 0x8000`. For cmd `1028` the ack echoes back as `0x8404`.

In-flight commands are tracked by a key of `((seq & 0xFF) << 16) | (cmd & 0x7FFF)`
so you can match a response to the request that triggered it.

Source: `h7/C0987a.java` (`Packet`), `h7/C0988b.java` (`PacketFactory`).
Chinese debug string confirming the length-check logic:
`"检测到包长度错误：cmd=%04x expect %d but %d"` ("packet length error detected").

---

## 4. Noise-reduction command (cmd 1028 / 0x0404)

Payload for a **mode-switch** request is always 3 bytes:

```
01 01 XX
```

- Byte 0 = `0x01` — fixed marker.
- Byte 1 = `0x01` — `mType = 1` (mode-select, as opposed to type 2 = "level" query/report).
- Byte 2 = single-bit bitmask selecting the target mode.

### Mode → bitmask table (confirmed from UI code, `NoiseReductionButtonSeekBarView.java`)

| Mode              | Bitmask byte | Full frame (seq=00 shown, increment per real request) |
|-------------------|:------------:|----------------------------------------------------------|
| Off               | `0x01`       | `04 04 00 03 00 01 01 01` |
| Transparency      | `0x02`       | `04 04 00 03 00 01 01 02` |
| ANC — weak/light  | `0x04`       | `04 04 00 03 00 01 01 04` |
| ANC — strong      | `0x08`       | `04 04 00 03 00 01 01 08` |
| ANC — Adaptive    | `0x10`       | `04 04 00 03 00 01 01 10` |

Bytes 0–1 (`04 04`) = cmd 1028 little-endian. Byte 2 = seq (increment each send).
Bytes 3–4 (`03 00`) = payload length 3, little-endian. Bytes 5–7 = the `01 01 XX` payload.

String resources for reference/labels (from HeyMelody, useful for your own UI copy):
- `melody_ui_noise_reduction_action_close_reduction` = "Off"
- `melody_ui_noise_reduction_action_pass_through_reduction` = "Transparency"
- `melody_ui_noise_reduction_weak_mode_175` = "Faint" (weak ANC)
- `melody_ui_noise_reduction_strong_mode_175` = "Extreme" (strong ANC)
- `melody_ui_noise_reduction_adaptive_noise_tips` = "Flexibly adjusts the noise level ... according to changes in ambient noise" (Adaptive)

### Reading current mode back

Same cmd (`1028`), a query/notify comes back with `type=1` and a bitmask payload
indicating which mode is currently active — parse it the same way you build it
(`CurrentNoiseModeInfo` in HeyMelody parses `bArr[1]` as `mType`, remaining bytes as
the bitmask). Use this on `onStartListening()` to set the tile's initial state correctly
rather than assuming a mode.

---

## 5. Call chain in HeyMelody (for cross-reference, not needed to replicate)

UI tap → `NoiseReductionItem` / `NoiseReductionButtonSeekBarView` → resolves UI mode to
`protocolIndex` (0–4) → `AbstractC0835b.k0(protocolIndex, macAddress)` → internal IPC
(what-code `3058`) → `HeadsetCoreService.D0()` builds `CurrentNoiseModeInfo`, gets cmd
`1028` → `PacketFactory` wraps in `Packet` (adds seq) → `HeadsetCoreService.u0()` writes
framed bytes to the RFCOMM socket (this last method was JNI/native-adjacent and jadx
could not fully decompile it — see gap below).

You don't need this chain for your app; go straight from "user tapped tile" → build frame
→ write to socket.

---

## 6. Known gap / what to verify empirically

`HeadsetCoreService.u0()` — the actual socket-write call — was only partially decompiled
("Method dump skipped" in jadx output). Everything upstream of it (packet assembly) is
fully verified in source, and the socket write itself (`r7/C1356a.java:d()`) shows a
plain `write()`/`flush()` with no visible extra wrapping. But there's a small chance of
an additional checksum/trailer byte hiding in the undecompiled segment.

**Before shipping, verify with a Bluetooth HCI snoop log:**
1. Settings → Developer options → Enable Bluetooth HCI snoop log.
2. Toggle each mode in HeyMelody once.
3. Disable logging, pull `btsnoop_hci.log` (`adb bugreport` or `/sdcard/.../btsnoop_hci.log`
   depending on OS version), open in Wireshark with the Bluetooth dissector.
4. Filter for the RFCOMM channel to your buds' MAC and compare captured bytes against
   the frames in §4. Confirms:
   - exact byte sequence for your specific firmware revision (protocolIndex/bitmask
     values can vary slightly by device/firmware),
   - whether any trailing checksum exists,
   - the actual RFCOMM channel number negotiated for the custom UUID above.

---

## 7. Implementation checklist for ANCSwitch

1. Standard Android BT: get bonded device, open RFCOMM socket to UUID
   `00001107-D102-11E1-9B23-00025B00A5A5`.
2. Maintain a per-device sequence counter (byte, wraps at 256).
3. Build frame: `cmd_lo cmd_hi seq len_lo len_hi payload...` — cmd = `04 04`,
   payload = `01 01 XX` per table in §4.
4. Write to socket OutputStream, flush.
5. Read InputStream for ack frame (cmd `0x8404`), confirm success byte.
6. `TileService`: manifest entry + class per §1; reflect real mode state read back
   over the socket in `onStartListening()`, not just the last mode you sent (buds
   could be changed by voice assistant, case button, or HeyMelody itself).
7. Validate real bytes via HCI snoop (§6) before relying on this in daily use.

---

*Compiled from live decompilation of HeyMelody `base.apk` on 2026-07-09. Not affiliated
with OPPO/OnePlus/Heytap. For personal interoperability with hardware you own.*
