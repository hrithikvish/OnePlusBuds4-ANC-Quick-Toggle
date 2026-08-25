package com.hrithikvish.ancswitch

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile — shows the last mode we know about and, on tap, opens
 * [AncModePickerActivity], a small floating dialog listing every mode for one-tap
 * selection. The tile itself never touches Bluetooth; the picker owns the one
 * connection, on demand, for as long as it's on screen.
 */
class AncTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, AncModePickerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_anc_tile)
        tile.label = getString(R.string.anc_tile_label)
        val mode = BudsUtil.lastSavedMode(this)
        tile.state = if (mode != null && mode != BudsProtocol.AncMode.OFF) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.subtitle = mode?.let { BudsUtil.displayLabel(this, it) } ?: getString(R.string.tile_subtitle_default)
        tile.updateTile()
    }

    companion object {
        const val PREFS_NAME = "anc_tile"
        const val KEY_LAST_MODE = "last_mode"
    }
}
