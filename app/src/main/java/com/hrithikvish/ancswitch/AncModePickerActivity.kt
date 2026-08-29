package com.hrithikvish.ancswitch

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.hrithikvish.ancswitch.ui.components.AncIcons
import com.hrithikvish.ancswitch.ui.theme.ANCSwitchTheme
import com.hrithikvish.ancswitch.ui.theme.AncEase
import com.hrithikvish.ancswitch.ui.theme.AncPalette
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncSpring
import com.hrithikvish.ancswitch.ui.theme.AncTheme

/**
 * Dialog launched from the Quick Settings tile — lists the everyday ANC modes for one-tap
 * selection instead of cycling through them. Owns its own [BudsConnection] for as long as
 * it's on screen; connects on create, disconnects on destroy.
 */
class AncModePickerActivity : ComponentActivity(), BudsConnection.Listener {

    private var connection: BudsConnection? = null
    private var pendingMode: BudsProtocol.AncMode? = null
    private val status = mutableStateOf("")
    private val deviceName = mutableStateOf<String?>(null)
    private val isConnected = mutableStateOf(false)
    private val sentMode = mutableStateOf<BudsProtocol.AncMode?>(null)
    private val needsPermission = mutableStateOf(false)

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sentMode.value = BudsUtil.lastSavedMode(this)
        status.value = getString(R.string.picker_connecting)

        if (!BudsUtil.hasBtPermission(this)) {
            needsPermission.value = true
            status.value = getString(R.string.picker_permission_needed)
        } else {
            BudsUtil.findBudsDevice(this) { device ->
                if (device == null) {
                    status.value = getString(R.string.picker_no_buds_found)
                } else {
                    deviceName.value = device.name ?: device.address
                    connection = BudsConnection(this).also { it.connect(device) }
                }
            }
        }

        setContent {
            ANCSwitchTheme {
                PickerDialog(
                    status = status.value,
                    deviceName = deviceName.value,
                    isConnected = isConnected.value,
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
            BudsUtil.saveMode(this, mode)
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
        lifecycleScope.launch {
            delay(250)
            finish()
        }
    }

    // --- BudsConnection.Listener — all callbacks land on the main thread already ---

    override fun onLog(line: String) {}

    override fun onConnected() {
        status.value = getString(R.string.picker_connected)
        isConnected.value = true
        val toSend = pendingMode
        if (toSend != null) {
            pendingMode = null
            connection?.sendMode(toSend)
            BudsUtil.saveMode(this, toSend)
            finishSoon()
        } else {
            connection?.readMode()
        }
    }

    override fun onDisconnected(reason: String?) {
        isConnected.value = false
        status.value = reason ?: getString(R.string.picker_disconnected)
    }

    override fun onModeRead(mode: BudsProtocol.AncMode) {
        sentMode.value = mode
    }
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
private fun PickerDialog(
    status: String,
    deviceName: String?,
    isConnected: Boolean,
    sentMode: BudsProtocol.AncMode?,
    needsPermission: Boolean,
    onPick: (BudsProtocol.AncMode) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AncTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ink0.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(AncShapes.lg)
                .background(colors.ink2)
                .border(1.dp, colors.line, AncShapes.lg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* swallow taps so they don't fall through to the scrim */ }
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Text(
                stringResource(R.string.picker_title),
                style = AncTheme.type.bodyStrong,
                color = colors.paper0,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
            )

            if (needsPermission) {
                DialogOption(
                    label = stringResource(R.string.picker_grant_permission),
                    icon = AncIcons.BrokenSignal,
                    selected = false,
                    colors = colors,
                    onClick = onOpenApp,
                )
            } else {
                val context = LocalContext.current
                PICKER_MODES.forEach { mode ->
                    DialogOption(
                        label = BudsUtil.displayLabel(context, mode),
                        icon = AncIcons.forMode(mode),
                        selected = mode == sentMode,
                        colors = colors,
                        onClick = { onPick(mode) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) colors.paper0 else colors.paper3),
                )
                Text(
                    if (isConnected) stringResource(R.string.picker_connected_device, deviceName ?: "") else status,
                    style = AncTheme.type.eyebrow,
                    color = colors.paper3,
                )
            }
        }
    }
}

@Composable
private fun DialogOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    colors: AncPalette,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) colors.paper0 else colors.ink3, tween(300, easing = AncEase), label = "sheetOptionBg")
    val fg by animateColorAsState(if (selected) colors.ink0 else colors.paper1, tween(300, easing = AncEase), label = "sheetOptionFg")
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(300, easing = AncSpring), label = "sheetCheckAlpha")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(AncShapes.md)
            .background(bg)
            .border(1.dp, if (selected) bg else colors.line, AncShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = AncTheme.type.buttonLabel, color = fg, modifier = Modifier.weight(1f))
        Icon(
            AncIcons.Check,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp).alpha(checkAlpha),
        )
    }
}
