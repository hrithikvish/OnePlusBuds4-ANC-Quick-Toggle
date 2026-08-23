package com.hrithikvish.ancswitch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages one RFCOMM connection to a bonded Buds device and speaks the
 * cmd/seq/len framing described in PROTOCOL_NOTES.md. All callbacks are
 * delivered on the main thread.
 */
class BudsConnection(private val listener: Listener) {

    interface Listener {
        fun onLog(line: String)
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onModeRead(mode: BudsProtocol.AncMode)
    }

    companion object {
        private const val MAX_PENDING_BYTES = 4096
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile private var readThread: Thread? = null
    @Volatile private var running = false

    val isConnected: Boolean get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        running = true
        readThread = Thread {
            var sock: BluetoothSocket? = null
            try {
                log("Opening RFCOMM socket to ${device.address} (UUID ${BudsProtocol.SERVICE_UUID})")
                sock = try {
                    device.createRfcommSocketToServiceRecord(BudsProtocol.SERVICE_UUID).also { it.connect() }
                } catch (e: IOException) {
                    log("Secure connect failed (${e.message}), retrying insecure")
                    device.createInsecureRfcommSocketToServiceRecord(BudsProtocol.SERVICE_UUID).also { it.connect() }
                }
                socket = sock
                outputStream = sock.outputStream
                log("Connected to ${device.name ?: device.address}")
                mainHandler.post { listener.onConnected() }
                readLoop(sock.inputStream)
            } catch (e: IOException) {
                log("Connection error: ${e.message}")
                mainHandler.post { listener.onDisconnected(e.message) }
            } finally {
                try { sock?.close() } catch (_: IOException) {}
            }
        }.also { it.start() }
    }

    private fun readLoop(input: InputStream) {
        val buffer = ByteArray(512)
        var pending = ByteArray(0)
        while (running) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                log("Read error: ${e.message}")
                mainHandler.post { listener.onDisconnected(e.message) }
                return
            }
            if (read <= 0) {
                mainHandler.post { listener.onDisconnected("stream closed") }
                return
            }
            pending += buffer.copyOfRange(0, read)

            while (true) {
                val unwrapped = BudsProtocol.unwrapFrame(pending, pending.size) ?: break
                val frame = BudsProtocol.parseFrame(unwrapped.inner, unwrapped.inner.size)
                if (frame == null) {
                    log("RX (unparsed inner, ${unwrapped.inner.size} bytes): ${BudsProtocol.toHex(unwrapped.inner)}")
                } else {
                    log("RX cmd=0x${"%04X".format(frame.cmd)} seq=${frame.seq} payload=${BudsProtocol.toHex(frame.payload)}")
                    if (frame.cmd == BudsProtocol.CMD_NOISE_REDUCTION_ACK) {
                        BudsProtocol.modeFromNoisePayload(frame.payload)?.let { mode ->
                            mainHandler.post { listener.onModeRead(mode) }
                        }
                    }
                }
                pending = pending.copyOfRange(unwrapped.consumed, pending.size)
            }

            if (pending.size > MAX_PENDING_BYTES) {
                log("RX (discarding ${pending.size} unparseable bytes): ${BudsProtocol.toHex(pending)}")
                pending = ByteArray(0)
            }
        }
    }

    fun sendMode(mode: BudsProtocol.AncMode) {
        val out = outputStream
        if (out == null) {
            log("Cannot send ${mode.label}: not connected")
            return
        }
        Thread {
            try {
                val inner = BudsProtocol.buildModeSwitchFrame(mode)
                val frame = BudsProtocol.wrapFrame(inner)
                log("TX ${mode.label}: ${BudsProtocol.toHex(frame)}")
                out.write(frame)
                out.flush()
            } catch (e: IOException) {
                log("Write error: ${e.message}")
                mainHandler.post { listener.onDisconnected(e.message) }
            }
        }.start()
    }

    fun disconnect() {
        running = false
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
        readThread = null
    }

    private fun log(line: String) {
        mainHandler.post { listener.onLog(line) }
    }
}
