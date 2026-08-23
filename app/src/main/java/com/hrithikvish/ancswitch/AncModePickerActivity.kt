package com.hrithikvish.ancswitch

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small floating dialog launched from the Quick Settings tile — lists the everyday ANC
 * modes for one-tap selection instead of cycling through them. Owns its own
 * [BudsConnection] for as long as it's on screen; connects on create, disconnects on
 * destroy.
 */
class AncModePickerActivity : ComponentActivity(), BudsConnection.Listener {

    private var connection: BudsConnection? = null
    private var pendingMode: BudsProtocol.AncMode? = null
    private val status = mutableStateOf("Connecting…")
    private val sentMode = mutableStateOf<BudsProtocol.AncMode?>(null)
    private val needsPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sentMode.value = BudsUtil.lastSavedMode(this)

        if (!BudsUtil.hasBtPermission(this)) {
            needsPermission.value = true
            status.value = "Bluetooth permission needed"
        } else {
            BudsUtil.findBudsDevice(this) { device ->
                if (device == null) {
                    status.value = "No paired Buds found"
                } else {
                    connection = BudsConnection(this).also { it.connect(device) }
                }
            }
        }

        setContent {
            AncPickerTheme {
                PickerScreen(
                    status = status.value,
                    sentMode = sentMode.value,
                    needsPermission = needsPermission.value,
                    onPick = ::pick,
                    onOpenApp = ::openApp,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connection?.disconnect()
    }

    private fun pick(mode: BudsProtocol.AncMode) {
        sentMode.value = mode
        val conn = connection
        if (conn?.isConnected == true) {
            conn.sendMode(mode)
            saveMode(mode)
            finishSoon()
        } else {
            pendingMode = mode
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun finishSoon() {
        window.decorView.postDelayed({ finish() }, 250)
    }

    private fun saveMode(mode: BudsProtocol.AncMode) {
        getSharedPreferences(AncTileService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(AncTileService.KEY_LAST_MODE, mode.name)
            .apply()
        // The tile only refreshes on its own onStartListening() lifecycle — nudge it to
        // re-read the saved mode now instead of waiting for the next organic panel open.
        TileService.requestListeningState(this, ComponentName(this, AncTileService::class.java))
    }

    // --- BudsConnection.Listener — all callbacks land on the main thread already ---

    override fun onLog(line: String) {}

    override fun onConnected() {
        status.value = "Connected"
        val toSend = pendingMode
        if (toSend != null) {
            pendingMode = null
            connection?.sendMode(toSend)
            saveMode(toSend)
            finishSoon()
        }
    }

    override fun onDisconnected(reason: String?) {
        status.value = reason ?: "Disconnected"
    }

    override fun onModeRead(mode: BudsProtocol.AncMode) {}
}

@Composable
private fun AncPickerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme: ColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ANC_STRONG (0x08) was never physically confirmed and turned out to be a no-op on the
// real device — ANC_WEAK (0x02) is the bit that was actually verified to engage real ANC
// during the on-device swap test, so that's what "ANC" points at here.
private val PICKER_MODES = listOf(
    BudsProtocol.AncMode.OFF,
    BudsProtocol.AncMode.TRANSPARENCY,
    BudsProtocol.AncMode.ANC_WEAK,
)

@Composable
private fun PickerScreen(
    status: String,
    sentMode: BudsProtocol.AncMode?,
    needsPermission: Boolean,
    onPick: (BudsProtocol.AncMode) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* swallow taps so they don't fall through to the scrim */ },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "ANC Mode",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
                )
                if (needsPermission) {
                    ModeRow(
                        label = "Grant Bluetooth permission",
                        selected = false,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onOpenApp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PICKER_MODES.forEach { mode ->
                            val selected = mode == sentMode
                            ModeRow(
                                label = BudsUtil.displayLabel(mode),
                                selected = selected,
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                onClick = { onPick(mode) }
                            )
                        }
                    }
                }
                Text(
                    status,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = contentColor
        )
    }
}
