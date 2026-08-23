package com.hrithikvish.ancswitch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hrithikvish.ancswitch.ui.theme.ANCSwitchTheme

class MainActivity : ComponentActivity() {

    private lateinit var connection: BudsConnection

    private var log = mutableStateOf("")
    private var connected = mutableStateOf(false)
    private var currentMode = mutableStateOf<BudsProtocol.AncMode?>(null)
    private val bondedDevices = mutableStateListOf<BluetoothDevice>()

    private fun appendLog(line: String) {
        log.value = (log.value + line + "\n").takeLast(6000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connection = BudsConnection(object : BudsConnection.Listener {
            override fun onLog(line: String) = appendLog(line)
            override fun onConnected() {
                connected.value = true
                appendLog("-- connected --")
            }
            override fun onDisconnected(reason: String?) {
                connected.value = false
                appendLog("-- disconnected${if (reason != null) " ($reason)" else ""} --")
            }
            override fun onModeRead(mode: BudsProtocol.AncMode) {
                currentMode.value = mode
                appendLog("-- device reports mode: ${mode.label} --")
            }
        })

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { refreshBondedDevices() }

        setContent {
            ANCSwitchTheme {
                TestScreen(
                    bondedDevices = bondedDevices,
                    connected = connected.value,
                    currentMode = currentMode.value,
                    logText = log.value,
                    onRequestPermission = {
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.BLUETOOTH)
                        }
                        permissionLauncher.launch(perms)
                    },
                    onRefreshDevices = { refreshBondedDevices() },
                    onConnect = { device ->
                        if (hasBtPermission()) connection.connect(device)
                    },
                    onDisconnect = { connection.disconnect() },
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
        bondedDevices.clear()
        bondedDevices.addAll(adapter.bondedDevices)
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun TestScreen(
    bondedDevices: List<BluetoothDevice>,
    connected: Boolean,
    currentMode: BudsProtocol.AncMode?,
    logText: String,
    onRequestPermission: () -> Unit,
    onRefreshDevices: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onSendMode: (BudsProtocol.AncMode) -> Unit,
) {
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("ANCSwitch — connection test") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermission) { Text("Grant BT permission") }
                Button(onClick = onRefreshDevices) { Text("Refresh bonded") }
            }

            Text("Bonded devices:", style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(bondedDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("${device.name ?: "(unknown)"} — ${device.address}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    selectedDevice = device
                                    onConnect(device)
                                }) { Text("Connect") }
                            }
                        }
                    }
                }
            }

            Text(
                "Status: ${if (connected) "connected to ${selectedDevice?.name ?: selectedDevice?.address}" else "disconnected"}" +
                    (currentMode?.let { " · mode: ${it.label}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDisconnect, enabled = connected) { Text("Disconnect") }
            }

            Text("Send ANC mode:", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BudsProtocol.AncMode.entries.take(3).forEach { mode ->
                    Button(onClick = { onSendMode(mode) }, enabled = connected) {
                        Text(mode.label)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BudsProtocol.AncMode.entries.drop(3).forEach { mode ->
                    Button(onClick = { onSendMode(mode) }, enabled = connected) {
                        Text(mode.label)
                    }
                }
            }

            Text("Log:", style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(logText.lines()) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
