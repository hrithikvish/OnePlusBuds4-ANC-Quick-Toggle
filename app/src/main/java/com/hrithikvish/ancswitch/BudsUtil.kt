package com.hrithikvish.ancswitch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object BudsUtil {
    fun hasBtPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Prefers the bonded "Buds" device that's actively connected right now (checked via the
     * A2DP profile — bonded alone doesn't mean it's powered on / in range). Falls back to any
     * bonded device matching by name if nothing shows up as connected, so this still works if
     * audio happens to be routed elsewhere when the RFCOMM channel is still reachable.
     * Async because BluetoothAdapter.getProfileProxy is; callback lands on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun findBudsDevice(context: Context, callback: (BluetoothDevice?) -> Unit) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            callback(null)
            return
        }
        val bondedFallback = adapter.bondedDevices.firstOrNull { it.name.isBudsDevice() }
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val connected = proxy.connectedDevices.firstOrNull { it.name.isBudsDevice() }
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                callback(connected ?: bondedFallback)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    private fun String?.isBudsDevice(): Boolean = this?.contains("Buds", ignoreCase = true) == true

    /**
     * User-facing name for a mode in the tile/picker UI — distinct from [BudsProtocol.AncMode.label],
     * which stays as the plain protocol-table name for the MainActivity debug screen.
     */
    fun displayLabel(context: Context, mode: BudsProtocol.AncMode): String =
        if (mode == BudsProtocol.AncMode.ANC_WEAK) {
            context.getString(R.string.mode_label_noise_cancellation)
        } else {
            mode.label
        }

    /** The mode last successfully sent, persisted so the tile and picker agree on it across launches. */
    fun lastSavedMode(context: Context): BudsProtocol.AncMode? {
        val name = context.getSharedPreferences(AncTileService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(AncTileService.KEY_LAST_MODE, null) ?: return null
        return BudsProtocol.AncMode.entries.firstOrNull { it.name == name }
    }

    /** Persists the mode and nudges the Quick Settings tile to refresh. */
    fun saveMode(context: Context, mode: BudsProtocol.AncMode) {
        context.getSharedPreferences(AncTileService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(AncTileService.KEY_LAST_MODE, mode.name)
            }
        // The tile only refreshes on its own onStartListening() lifecycle — nudge it to
        // re-read the saved mode now instead of waiting for the next organic panel open.
        TileService.requestListeningState(context, ComponentName(context, AncTileService::class.java))
    }
}
