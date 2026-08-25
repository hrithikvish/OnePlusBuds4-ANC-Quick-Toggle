package com.hrithikvish.ancswitch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hrithikvish.ancswitch.ui.components.AncAppBar
import com.hrithikvish.ancswitch.ui.components.AncGhostButton
import com.hrithikvish.ancswitch.ui.components.AncIcons
import com.hrithikvish.ancswitch.ui.components.AncPrimaryButton
import com.hrithikvish.ancswitch.ui.components.DashedHintCard
import com.hrithikvish.ancswitch.ui.components.DeviceCard
import com.hrithikvish.ancswitch.ui.components.EmptyState
import com.hrithikvish.ancswitch.ui.components.ErrorBanner
import com.hrithikvish.ancswitch.ui.components.LogPanel
import com.hrithikvish.ancswitch.ui.components.ModeGrid
import com.hrithikvish.ancswitch.ui.components.SectionLabel
import com.hrithikvish.ancswitch.ui.components.StatusDotState
import com.hrithikvish.ancswitch.ui.components.StatusPill
import com.hrithikvish.ancswitch.ui.theme.ANCSwitchTheme
import com.hrithikvish.ancswitch.ui.theme.AncTheme

// TODO: wire to a remote config flag instead of a hardcoded constant.
private const val SHOW_LOG_PANEL = false

/** Everything the connection console screen needs to render, as one snapshot instead of a pile
 * of individual mutableStateOf fields — every update is a single `.copy(...)` assignment. */
data class ConsoleUiState(
    val log: String = "",
    val connected: Boolean = false,
    val isLinking: Boolean = false,
    val dropped: Boolean = false,
    val dropReason: String? = null,
    val currentMode: BudsProtocol.AncMode? = null,
    val selectedDevice: BluetoothDevice? = null,
    val hasPermission: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
)

class MainActivity : ComponentActivity() {

    private lateinit var connection: BudsConnection

    private var uiState = mutableStateOf(ConsoleUiState())

    /** Distinguishes a user-initiated Disconnect tap from a real drop, so onDisconnected only
     * flips [ConsoleUiState.dropped] when a previously-connected session dies unexpectedly. */
    private var explicitDisconnect = false

    /** Keeps [ConsoleUiState.bluetoothEnabled] live if the user flips the radio from Quick
     * Settings while this screen is open, instead of only checking once at launch. */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val enabled = isBluetoothEnabled()
            uiState.value = uiState.value.copy(bluetoothEnabled = enabled)
            if (enabled) refreshBondedDevices()
        }
    }

    private fun appendLog(line: String) {
        uiState.value = uiState.value.copy(log = (uiState.value.log + line + "\n").takeLast(6000))
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        uiState.value = uiState.value.copy(
            hasPermission = hasBtPermission(),
            bluetoothEnabled = isBluetoothEnabled(),
        )
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        connection = BudsConnection(object : BudsConnection.Listener {
            override fun onLog(line: String) = appendLog(line)
            override fun onConnected() {
                uiState.value = uiState.value.copy(connected = true, isLinking = false, dropped = false)
                appendLog("-- connected --")
            }
            override fun onDisconnected(reason: String?) {
                val wasConnected = uiState.value.connected
                val stillDropped = wasConnected && !explicitDisconnect
                uiState.value = uiState.value.copy(
                    connected = false,
                    isLinking = false,
                    dropped = stillDropped,
                    dropReason = if (stillDropped) reason else uiState.value.dropReason,
                )
                explicitDisconnect = false
                appendLog("-- disconnected${if (reason != null) " ($reason)" else ""} --")
            }
            override fun onModeRead(mode: BudsProtocol.AncMode) {
                uiState.value = uiState.value.copy(currentMode = mode)
                appendLog("-- device reports mode: ${mode.label} --")
            }
        })

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val granted = hasBtPermission()
            uiState.value = uiState.value.copy(hasPermission = granted)
            if (granted) refreshBondedDevices()
        }

        setContent {
            ANCSwitchTheme {
                ConsoleScreen(
                    state = uiState.value,
                    onRequestPermission = {
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.BLUETOOTH)
                        }
                        permissionLauncher.launch(perms)
                    },
                    onEnableBluetooth = {
                        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onRefreshDevices = { refreshBondedDevices() },
                    onConnect = { device ->
                        if (hasBtPermission()) {
                            explicitDisconnect = false
                            uiState.value = uiState.value.copy(
                                selectedDevice = device,
                                isLinking = true,
                                dropped = false,
                            )
                            connection.connect(device)
                        }
                    },
                    onDisconnect = {
                        explicitDisconnect = true
                        connection.disconnect()
                    },
                    onSendMode = { mode -> connection.sendMode(mode) }
                )
            }
        }

        if (hasBtPermission()) refreshBondedDevices()
    }

    private fun hasBtPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        if (!hasBtPermission()) return
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return
        uiState.value = uiState.value.copy(bondedDevices = adapter.bondedDevices.toList())
    }

    private fun isBluetoothEnabled(): Boolean {
        val manager = getSystemService(BluetoothManager::class.java)
        return manager?.adapter?.isEnabled == true
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.disconnect()
        unregisterReceiver(bluetoothStateReceiver)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConsoleScreen(
    state: ConsoleUiState,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onSendMode: (BudsProtocol.AncMode) -> Unit,
) {
    val colors = AncTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ink1)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        AncAppBar(
            subtitle = stringResource(R.string.console_subtitle),
            versionLabel = stringResource(R.string.console_version_label),
        )
        Box(modifier = Modifier.weight(1f)) {
            when {
                !state.hasPermission -> EmptyState(
                    icon = AncIcons.Bluetooth,
                    title = stringResource(R.string.no_permission_title),
                    body = stringResource(R.string.no_permission_body),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    action = {
                        AncPrimaryButton(stringResource(R.string.grant_bluetooth_access), onClick = onRequestPermission)
                    },
                )

                !state.bluetoothEnabled -> EmptyState(
                    icon = AncIcons.Bluetooth,
                    title = stringResource(R.string.bluetooth_off_title),
                    body = stringResource(R.string.bluetooth_off_body),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    action = {
                        AncPrimaryButton(stringResource(R.string.turn_on_bluetooth), onClick = onEnableBluetooth)
                    },
                )

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val statusText = when {
                        state.isLinking -> stringResource(R.string.status_connecting)
                        state.dropped -> stringResource(R.string.status_connection_lost)
                        state.connected -> stringResource(R.string.status_connected_to, state.selectedDevice?.displayName() ?: "")
                        else -> stringResource(R.string.status_not_connected)
                    }
                    val statusDot = when {
                        state.isLinking -> StatusDotState.PULSE
                        state.dropped -> StatusDotState.WARN
                        state.connected -> StatusDotState.ON
                        else -> StatusDotState.NEUTRAL
                    }
                    StatusPill(statusText, statusDot)

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AncGhostButton(stringResource(R.string.refresh_bonded), onClick = onRefreshDevices, small = true)
                        if (state.connected) {
                            AncGhostButton(
                                text = stringResource(R.string.disconnect),
                                onClick = onDisconnect,
                                small = true,
                                emphasized = true,
                            )
                        }
                    }

                    SectionLabel(stringResource(R.string.section_bonded_devices))
                    if (state.bondedDevices.isEmpty()) {
                        DashedHintCard(stringResource(R.string.no_bonded_devices))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.bondedDevices.forEach { device ->
                                val isSelected = device.address == state.selectedDevice?.address
                                DeviceCard(name = device.displayName(), address = device.address) {
                                    when {
                                        isSelected && state.isLinking -> AncPrimaryButton(
                                            text = stringResource(R.string.connect),
                                            onClick = {},
                                            loading = true,
                                            small = true,
                                        )
                                        isSelected && state.connected -> Text(
                                            text = stringResource(R.string.device_connected),
                                            style = AncTheme.type.buttonLabel,
                                            color = colors.paper3,
                                        )
                                        isSelected && state.dropped -> AncGhostButton(
                                            text = stringResource(R.string.reconnect),
                                            onClick = { onConnect(device) },
                                            small = true,
                                            emphasized = true,
                                        )
                                        else -> AncPrimaryButton(
                                            text = stringResource(R.string.connect),
                                            onClick = { onConnect(device) },
                                            small = true,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SectionLabel(stringResource(R.string.section_send_anc_mode))
                    ModeGrid(
                        selectedMode = state.currentMode,
                        enabled = state.connected,
                        onSelect = onSendMode,
                    )

                    if (SHOW_LOG_PANEL) {
                        SectionLabel(stringResource(R.string.section_log))
                        LogPanel(
                            text = state.log,
                            showCursor = state.connected,
                            modifier = Modifier.height(220.dp),
                        )
                    }

                    if (state.dropped) {
                        ErrorBanner(
                            message = stringResource(R.string.connection_dropped) + (state.dropReason?.let { " · $it" } ?: ""),
                            actionText = stringResource(R.string.retry),
                            onAction = { state.selectedDevice?.let(onConnect) },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.displayName(): String = name ?: address
