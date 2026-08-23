package com.hrithikvish.ancswitch

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * OnePlus Buds 4 ANC protocol — see PROTOCOL_NOTES.md.
 */
object BudsProtocol {
    // Confirmed via `adb shell dumpsys bluetooth_manager` SDP UUID list on the actual bonded
    // device — this pair uses the server-pushed override, not the 0x1107 fallback.
    val SERVICE_UUID: UUID = UUID.fromString("0000079a-d102-11e1-9b23-00025b00a5a5")

    const val CMD_NOISE_REDUCTION = 0x0404
    const val CMD_NOISE_REDUCTION_ACK = CMD_NOISE_REDUCTION or 0x8000

    // TRANSPARENCY/ANC_WEAK bitmasks confirmed swapped from the decompiled HeyMelody source by
    // two independent physical on-device tests. The decompile is HeyMelody 116.7.0; the phone
    // runs 116.9.0 (installed 2026-08-21) — Heytap evidently changed this bit assignment (or
    // replaced the UI widget entirely) in a later release, so live behavior overrides the trace.
    enum class AncMode(val label: String, val bitmask: Int) {
        OFF("Off", 0x01),
        ANC_WEAK("ANC – Weak", 0x02),
        TRANSPARENCY("Transparency", 0x04),
        ANC_STRONG("ANC – Strong", 0x08),
        ANC_ADAPTIVE("ANC – Adaptive", 0x10);

        companion object {
            fun fromBitmask(mask: Int): AncMode? = entries.firstOrNull { it.bitmask == mask }
        }
    }

    private val seqCounter = AtomicInteger(0)

    private fun nextSeq(): Int = seqCounter.getAndUpdate { (it + 1) and 0xFF }

    /** Builds the 8-byte frame: cmd_lo cmd_hi seq len_lo len_hi 01 01 modeBit */
    fun buildModeSwitchFrame(mode: AncMode): ByteArray {
        val seq = nextSeq()
        val payload = byteArrayOf(0x01, 0x01, mode.bitmask.toByte())
        return byteArrayOf(
            (CMD_NOISE_REDUCTION and 0xFF).toByte(),
            ((CMD_NOISE_REDUCTION shr 8) and 0xFF).toByte(),
            seq.toByte(),
            (payload.size and 0xFF).toByte(),
            ((payload.size shr 8) and 0xFF).toByte(),
            *payload
        )
    }

    data class ParsedFrame(val cmd: Int, val seq: Int, val payload: ByteArray)

    /** Parses a raw 5-byte-header frame off the wire. Returns null if incomplete. */
    fun parseFrame(bytes: ByteArray, length: Int): ParsedFrame? {
        if (length < 5) return null
        val cmd = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val seq = bytes[2].toInt() and 0xFF
        val len = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
        if (length < 5 + len) return null
        val payload = bytes.copyOfRange(5, 5 + len)
        return ParsedFrame(cmd, seq, payload)
    }

    private const val OUTER_MAGIC: Byte = 0xAA.toByte()

    /**
     * Every inner cmd/seq/len frame is wrapped in an outer envelope before hitting the socket:
     * `AA <contentLen> <flags=00> <reserved=00> <inner frame>`, where contentLen counts the
     * flags+reserved bytes plus the inner frame. Confirmed via HCI snoop of HeyMelody traffic
     * on this same RFCOMM channel (see PROTOCOL_NOTES.md). contentLen is a plain byte value
     * (not RFCOMM-style EA-shifted), so this only supports inner frames up to 125 bytes.
     */
    fun wrapFrame(inner: ByteArray): ByteArray {
        val contentLen = inner.size + 2
        require(contentLen < 128) { "frame too large for single-byte length encoding" }
        return byteArrayOf(OUTER_MAGIC, contentLen.toByte(), 0x00, 0x00) + inner
    }

    data class UnwrappedFrame(val inner: ByteArray, val consumed: Int)

    /** Strips the outer envelope. Returns null if incomplete or not an envelope frame. */
    fun unwrapFrame(bytes: ByteArray, length: Int): UnwrappedFrame? {
        if (length < 4 || bytes[0] != OUTER_MAGIC) return null
        val contentLen = bytes[1].toInt() and 0xFF
        if (contentLen < 2) return null
        val total = 2 + contentLen
        if (length < total) return null
        return UnwrappedFrame(bytes.copyOfRange(4, total), total)
    }

    /** For an ack/notify frame of CMD_NOISE_REDUCTION_ACK, extract the reported mode. */
    fun modeFromNoisePayload(payload: ByteArray): AncMode? {
        if (payload.size < 3) return null
        val mask = payload[2].toInt() and 0xFF
        return AncMode.fromBitmask(mask)
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
