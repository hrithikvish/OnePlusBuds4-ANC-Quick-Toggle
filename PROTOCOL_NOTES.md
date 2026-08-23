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
  0000079A-D102-11E1-9B23-00025B00A5A5
  ```
  **Confirmed empirically** via `adb shell dumpsys bluetooth_manager` SDP UUID list on a
  live bonded OnePlus Buds 4 — this is what the buds actually advertise, not
  `00001107-D102-11E1-9B23-00025B00A5A5` (source: `S7/AbstractC1383b.java:32`), which
  is only the BR/EDR *fallback* UUID used when `SupportDeviceConfig.getUuid()` has no
  server-pushed per-device override. Connecting with `00001107-...` fails at the SDP
  layer with `IOException: read failed, socket might closed or timeout, read ret: -1` —
  Android's SDP client finds no matching service record. A background re-check of the
  decompile found `0000079A-...` hardcoded as the whitelist's *first* entry for this
  product (`com/oplus/melody/model/repository/whitelist/a.java:77`) and as the BLE GATT
  fallback UUID, so it's not a fluke — this product's whitelist entry overrides the
  legacy `1107` BR fallback for both transports. **Always verify the live SDP UUID list
  for your own unit** rather than trusting the decompiled fallback constant.
- Device must already be **bonded** (standard Android pairing) before opening the socket.
- Socket creation: `device.createRfcommSocketToServiceRecord(uuid)`
  (`S7/AbstractC1382a.java:167`).
- Writes go straight to `socket.getOutputStream()` — `write(bytes); flush();`
  (`r7/C1356a.java`, method `d()`). No visible extra encryption/escaping at this layer.

---

## 3. Packet framing ("MMI"-style proprietary protocol)

### 3a. Outer envelope — required, goes on the wire *first*

**Not in the original write-up — discovered by HCI-snoop capture after raw inner frames
(see §3b) got zero response from the buds.** Every inner frame is wrapped before hitting
the socket:

```
[0xAA][contentLen][flags][reserved][inner frame...]
  b0      b1         b2      b3        b4..
```

- `0xAA` — fixed magic byte.
- `contentLen` (1 byte) — count of `flags` + `reserved` + the inner frame that follows
  (i.e. `inner.size + 2`). Plain byte value, not RFCOMM-EA-shifted — only confirmed for
  values `< 128`; a real 2-byte-length example was never captured.
- `flags` — `0x00` seen on every captured frame (single/complete packet, no fragmentation
  observed in practice).
- `reserved` — `0x00` on every captured frame.

Confirmed both from decompiled source (`X7/C1526b.java` "TLVDataProcessor" encode side,
`y7/C1545a.java` "OPOv1Wrapper" decode side — magic-byte check at `y7/C1545a.java:81`)
**and** from a live HCI snoop of real HeyMelody traffic on this exact RFCOMM channel, e.g.
an incoming status push captured as `AA 0F 00 00 04 02 FF 08 00 02 03 01 05 02 07 03 04`.

Example: `Off` (inner frame `04 04 00 03 00 01 01 01`, 8 bytes) wrapped becomes:
```
AA 0A 00 00 04 04 00 03 00 01 01 01
```

### 3b. Inner frame

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
`response_cmd = (cmd & 0x7FFF) | 0x8000`. For cmd `1028` the ack echoes back as `0x8404`
(confirmed live: sending the frame above got back a wrapped `RX cmd=0x8404 seq=0
payload=00` ack, followed by a `cmd=0x0204` status push echoing the new mode bitmask).

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

### Mode → bitmask table — **physically verified on-device**, overrides decompiled source

| Mode              | Bitmask byte | Full inner frame (seq=00 shown, increment per real request) |
|-------------------|:------------:|----------------------------------------------------------|
| Off               | `0x01`       | `04 04 00 03 00 01 01 01` |
| ANC — weak/light  | `0x02`       | `04 04 00 03 00 01 01 02` |
| Transparency      | `0x04`       | `04 04 00 03 00 01 01 04` |
| ANC — strong      | `0x08`       | `04 04 00 03 00 01 01 08` |
| ANC — Adaptive    | `0x10`       | `04 04 00 03 00 01 01 10` |

Bytes 0–1 (`04 04`) = cmd 1028 little-endian. Byte 2 = seq (increment each send).
Bytes 3–4 (`03 00`) = payload length 3, little-endian. Bytes 5–7 = the `01 01 XX` payload.
(Remember to wrap the whole 8-byte inner frame in the §3a outer envelope before sending.)

**Important discrepancy — trust this table, not the decompile.** Both HeyMelody 116.7.0
and 116.9.0 decompile to *identical*, fully-traced logic (UI tap → `NoiseReductionButtonSeekBarView`
→ `AbstractC0939b.k0()` → a switch-case `Supplier` → `CurrentNoiseModeInfo.getData()`, no
undecompiled gaps in either version) that computes `Transparency = 0x02` and
`ANC-weak = 0x04` — i.e. the two values swapped from the table above. On this actual
physical OnePlus Buds 4 unit, sending `0x02` produces the felt ANC effect and `0x04`
produces the felt Transparency effect — confirmed by two independent physical tests. Why
the decompiled app-layer arithmetic and the real wire behavior disagree is unresolved
(possibly a remap inside the still-undecompiled `HeadsetCoreService.u0()`/`j0()` socket
methods, or firmware-specific); if you build against a different unit, verify by ear
before trusting either source.

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
`protocolIndex` (0–4) → `AbstractC0835b.k0(protocolIndex, macAddress)` (renamed
`AbstractC0939b` in 116.9.0) → a switch-case `Supplier` (`B7/g.java` case 12 in 116.7.0,
`R8/y.java` case 6 in 116.9.0) builds a `CurrentNoiseModeInfo`, `setType(1)`,
`setCurrentNoiseReductionModeValue(protocolIndex, true)` → fires an internal `Intent`
(what-code `1014`) to `HeadsetCoreService` → `D0()`/similar reads it, gets cmd `1028` →
`PacketFactory` wraps in `Packet` (adds seq) → `X7/C1526b.java` ("TLVDataProcessor") wraps
the packet in the §3a outer envelope → `HeadsetCoreService.u0()`/`r7/C1356a.java:d()`
writes the final bytes to the RFCOMM socket. **Correction from the original write-up:**
this last stretch isn't JNI/native — it's ordinary (if heavily obfuscated) Java; the
"Method dump skipped" gaps are jadx failing on obfuscated bytecode, not a native boundary.

You don't need this chain for your app; go straight from "user tapped tile" → build inner
frame → wrap in outer envelope (§3a) → write to socket.

---

## 6. Resolved gaps (empirically verified via HCI snoop, see §3a and §4)

Two things flagged as open questions in the original write-up are now settled:

- **Outer envelope**: confirmed real and required — see §3a. Raw inner frames (no `0xAA`
  wrapper) got zero response from the buds; wrapped frames get a proper `0x8404` ack.
- **Mode bitmask table**: the decompiled app-layer arithmetic and the real wire behavior
  disagree on which bit is Transparency vs. ANC-weak — see the discrepancy note in §4.
  Trust the physically-verified table, not the (twice-independently-confirmed, but
  apparently still wrong for the wire) decompiled arithmetic.

No RFCOMM channel-number or checksum surprises were found — the 5-byte inner header plus
outer-envelope 4-byte prefix account for every byte seen on the wire.

**How the snoop capture was done** (repeat this if you need to re-verify against your own
unit or a different firmware revision):
1. Settings → System → Developer options → Networking → Bluetooth → Enable Bluetooth HCI
   snoop log → **Enabled** (not "Enabled Filtered" — filtered mode can drop the RFCOMM
   payload bytes you actually need). Toggle Bluetooth off/on (or `adb shell svc bluetooth
   disable && adb shell svc bluetooth enable`) for the setting to take effect.
2. Trigger the traffic you want to capture (toggle each mode in HeyMelody once, or run
   your own app's connect/send).
3. Pull the log: `adb bugreport bugreport.zip`, then unzip and find
   `FS/data/misc/bluetooth/logs/btsnoop_hci.log` inside — this avoids needing root to
   reach `/data/misc/bluetooth/logs/` directly.
4. Parse it: `tshark`/Wireshark work if installed, or decode the BTSnoop format directly —
   16-byte file header, then repeating 24-byte-big-endian-header + packet records; each
   HCI ACL Data packet (H4 type `0x02`) contains a little-endian ACL header, then an
   L2CAP header (length + CID), then for RFCOMM traffic an address byte (`DLCI = addr >>
   2`), control byte, one-or-two-byte length, and the payload. Filter for the RFCOMM DLCI
   your target UUID's channel maps to (`DLCI = server_channel * 2`; the channel number
   itself comes from the `mPort` Android reports after `socket.connect()`, or from
   `dumpsys bluetooth_manager`), and compare captured bytes against §4's table. Confirms:
   - exact byte sequence for your specific firmware revision (protocolIndex/bitmask
     values can vary — as they did here — by device/firmware),
   - whether any trailing checksum exists,
   - the actual RFCOMM channel number negotiated for the custom UUID above.

---

## 7. Implementation checklist for ANCSwitch

1. Standard Android BT: get bonded device, open RFCOMM socket to UUID
   `0000079A-D102-11E1-9B23-00025B00A5A5` (verify against your own unit's live SDP
   list — see §2).
2. Maintain a per-device sequence counter (byte, wraps at 256).
3. Build inner frame: `cmd_lo cmd_hi seq len_lo len_hi payload...` — cmd = `04 04`,
   payload = `01 01 XX` per table in §4.
4. Wrap the inner frame in the outer envelope (§3a) before writing.
5. Write to socket OutputStream, flush.
6. Read InputStream for ack frame (cmd `0x8404`), confirm success byte — remember
   incoming bytes are also outer-enveloped and must be unwrapped first.
7. `TileService`: manifest entry + class per §1; reflect real mode state read back
   over the socket in `onStartListening()`, not just the last mode you sent (buds
   could be changed by voice assistant, case button, or HeyMelody itself).
8. ~~Validate real bytes via HCI snoop before relying on this in daily use~~ — done,
   see §3a/§4/§6.

Status: connection, framing, and mode-switching are implemented and physically verified
working in `BudsConnection.kt`/`BudsProtocol.kt`. The `TileService` in step 7 is not yet
built — `MainActivity` is a manual test screen only.

---

*Compiled from live decompilation of HeyMelody `base.apk` (116.7.0) on 2026-07-09,
re-verified against 116.9.0 on 2026-08-24. Not affiliated with OPPO/OnePlus/Heytap.
For personal interoperability with hardware you own.*
