package com.hrithikvish.ancswitch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
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

    private val connectionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var running = false

    val isConnected: Boolean get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        running = true
        connectionScope.launch {
            var sock: BluetoothSocket? = null
            try {
                log("Opening RFCOMM socket to ${device.address} (UUID ${BudsProtocol.SERVICE_UUID})")
                sock = withContext(Dispatchers.IO) {
                    try {
                        device.createRfcommSocketToServiceRecord(BudsProtocol.SERVICE_UUID).also { it.connect() }
                    } catch (e: IOException) {
                        log("Secure connect failed (${e.message}), retrying insecure")
                        device.createInsecureRfcommSocketToServiceRecord(BudsProtocol.SERVICE_UUID).also { it.connect() }
                    }
                }
                socket = sock
                outputStream = sock.outputStream
                log("Connected to ${device.name ?: device.address}")
                listener.onConnected()
                
                withContext(Dispatchers.IO) {
                    readLoop(sock.inputStream)
                }
            } catch (e: IOException) {
                log("Connection error: ${e.message}")
                listener.onDisconnected(e.message)
            } finally {
                withContext(Dispatchers.IO) {
                    try { sock?.close() } catch (_: IOException) {}
                }
            }
        }
    }

    private suspend fun readLoop(input: InputStream) {
        val buffer = ByteArray(512)
        var pending = ByteArray(0)
        while (running && currentCoroutineContext().isActive) {
            val read = try {
                input.read(buffer)
            } catch (e: IOException) {
                log("Read error: ${e.message}")
                withContext(Dispatchers.Main) { listener.onDisconnected(e.message) }
                return
            }
            if (read <= 0) {
                withContext(Dispatchers.Main) { listener.onDisconnected("stream closed") }
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
                    if (frame.cmd == BudsProtocol.CMD_NOISE_REDUCTION_ACK || frame.cmd == BudsProtocol.CMD_STATUS_PUSH) {
                        BudsProtocol.modeFromNoisePayload(frame.payload)?.let { mode ->
                            withContext(Dispatchers.Main) { listener.onModeRead(mode) }
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
        connectionScope.launch(Dispatchers.IO) {
            try {
                val inner = BudsProtocol.buildModeSwitchFrame(mode)
                val frame = BudsProtocol.wrapFrame(inner)
                log("TX ${mode.label}: ${BudsProtocol.toHex(frame)}")
                out.write(frame)
                out.flush()
            } catch (e: IOException) {
                log("Write error: ${e.message}")
                withContext(Dispatchers.Main) { listener.onDisconnected(e.message) }
            }
        }
    }

    fun readMode() {
        val out = outputStream
        if (out == null) {
            log("Cannot read mode: not connected")
            return
        }
        connectionScope.launch(Dispatchers.IO) {
            try {
                val inner = BudsProtocol.buildModeReadFrame()
                val frame = BudsProtocol.wrapFrame(inner)
                log("TX Read Mode: ${BudsProtocol.toHex(frame)}")
                out.write(frame)
                out.flush()
            } catch (e: IOException) {
                log("Write error: ${e.message}")
                withContext(Dispatchers.Main) { listener.onDisconnected(e.message) }
            }
        }
    }

    fun disconnect() {
        running = false
        connectionScope.coroutineContext.cancelChildren()
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
    }

    private fun log(line: String) {
        connectionScope.launch { listener.onLog(line) }
    }
}
