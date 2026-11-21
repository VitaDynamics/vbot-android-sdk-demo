package com.vita.vbot.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vita.vbot.sdk.VbotConnection
import com.vita.vbot.sdk.VbotFunctionStatus
import com.vita.vbot.sdk.VbotState
import com.vita.vbot.sdk.protocol.EnumNetStaus
import com.vita.vbot.sdk.protocol.EnumUWBStatus

private val CanvasColor = Color(0xFFF5F4F1)
private val Ink = Color(0xFF222220)
private val Accent = Color(0xFFB64325)
private val Muted = Color(0xFF64635E)

private enum class Page(val label: String) {
    DEVICE("Device"), CONTROL("Control"), UWB("UWB"), SETTINGS("Settings"), CHECKS("Checks")
}

@Composable
internal fun DemoScreen(
    session: DemoSession,
    importConfig: () -> Unit,
    scanBle: () -> Unit,
    connectBle: (String, String) -> Unit,
) {
    val ui by session.ui.collectAsState()
    val connection by session.connection.collectAsState()
    val state by session.state.collectAsState()
    val ready = connection is VbotConnection.Connected && !ui.busy
    var page by rememberSaveable { mutableStateOf(Page.DEVICE) }
    var serial by rememberSaveable { mutableStateOf(ui.authorizedSerial.orEmpty()) }
    var address by rememberSaveable { mutableStateOf("") }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.authorizedSerial) { ui.authorizedSerial?.let { serial = it } }
    BackHandler(page != Page.DEVICE || showConnection) {
        if (showConnection) showConnection = false else page = Page.DEVICE
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        secondary = Ink,
        background = CanvasColor,
        surface = Color.White,
        onSurface = Ink,
        onSurfaceVariant = Muted,
        outline = Color(0xFF77756E),
    )) {
        Scaffold(
            containerColor = CanvasColor,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (page == Page.DEVICE) "VBot" else page.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (page == Page.DEVICE) {
                        TextButton(onClick = { showConnection = true }) {
                            Text(if (connection is VbotConnection.Connected) "Manage connection" else "Connect a robot")
                        }
                    }
                }
            },
            bottomBar = {
                Surface(color = Color.White) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Page.entries.forEach { item ->
                            Surface(
                                onClick = { page = item },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = CircleShape,
                                color = if (page == item) Ink else Color.White,
                                contentColor = if (page == item) Color.White else Muted,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            },
        ) { inset ->
            key(page) {
                Column(
                    Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (page) {
                        Page.DEVICE -> HomePage(state, connection, ready, session::refresh, { showConnection = true }) { page = it }
                        Page.CONTROL -> ControlPage(state, connection is VbotConnection.Connected, ready, session)
                        Page.UWB -> UwbPage(state, ready, session)
                        Page.SETTINGS -> SettingsPage(state, ui, ready, session)
                        Page.CHECKS -> VerificationPage(ui, ready, connection, session)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (showConnection) {
            AlertDialog(
                modifier = Modifier.widthIn(max = 480.dp),
                onDismissRequest = { showConnection = false },
                title = { Text("Connect a robot") },
                text = {
                    Column(
                        Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(connection.description())
                        OutlinedTextField(
                            serial, { serial = it }, Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("Robot serial number") }, enabled = !ui.busy,
                        )
                        ActionRow {
                            OutlinedButton(onClick = importConfig, enabled = !ui.busy) { Text("Import config") }
                            Button(
                                onClick = { session.connectAorta(serial); showConnection = false },
                                enabled = !ui.busy && serial.trim().isNotEmpty() && serial.trim() == ui.authorizedSerial,
                            ) { Text("Connect via Aorta") }
                        }
                        HorizontalDivider()
                        OutlinedTextField(
                            address, { address = it }, Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("Bluetooth address") }, enabled = !ui.busy,
                        )
                        ActionRow {
                            OutlinedButton(onClick = if (ui.scanning) session::stopScan else scanBle, enabled = !ui.busy) {
                                Text(if (ui.scanning) "Stop scan" else "Scan for BLE")
                            }
                            Button(
                                onClick = { connectBle(serial, address.trim()); showConnection = false },
                                enabled = !ui.busy && serial.isNotBlank() && isBluetoothAddress(address.trim()),
                            ) { Text("Connect via BLE") }
                        }
                        ui.advertisements.forEach { ad ->
                            TextButton(onClick = { address = ad.address }) {
                                Text("${ad.name ?: "VBot"} · ${ad.address} · ${ad.rssi} dBm")
                            }
                        }
                    }
                },
                confirmButton = {
                    if (connection is VbotConnection.Connected || connection is VbotConnection.Connecting) {
                        TextButton(onClick = { session.disconnect(); showConnection = false }) { Text("Disconnect") }
                    }
                },
                dismissButton = { TextButton(onClick = { showConnection = false }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun HomePage(
    state: VbotState,
    connection: VbotConnection,
    ready: Boolean,
    refresh: () -> Unit,
    manage: () -> Unit,
    open: (Page) -> Unit,
) {
    val connected = connection is VbotConnection.Connected
    val serial = if (connected) state.serialNumber else null
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(254.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFFF1EFEB), Color(0xFFFBFAF8))),
                ),
            ) {
                Column(Modifier.align(Alignment.TopStart).padding(18.dp)) {
                    Text(serial?.let { "VBot ${it.takeLast(4)}" } ?: "My VBot",
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(connection.description(), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                Image(
                    painterResource(R.drawable.img_vbot_dog_connected),
                    contentDescription = "VBot robot",
                    modifier = Modifier.align(Alignment.BottomCenter).height(191.dp).fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Metric("Battery", serial?.let { state.batteryPercent }?.let { "$it%" } ?: "—",
                    Modifier.weight(1f), R.drawable.ic_state_battery)
                Metric("Network", if (connected) state.networkLabel() else "—", Modifier.weight(1f))
                Metric("Follow", if (connected) state.followStatus.label() else "—", Modifier.weight(1f))
            }
        }
    }
    if (connected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(serial.orEmpty(), Modifier.weight(1f), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = refresh, enabled = ready) { Text("Refresh status") }
        }
    } else if (connection !is VbotConnection.Connecting) {
        Button(onClick = manage, Modifier.fillMaxWidth()) { Text("Add a robot") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeEntry("Remote control", "Actions and joystick", Modifier.weight(1f)) { open(Page.CONTROL) }
        HomeEntry("UWB tag", "Status and gestures", Modifier.weight(1f)) { open(Page.UWB) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeEntry("Robot settings", "Lights, sound and power", Modifier.weight(1f)) { open(Page.SETTINGS) }
        HomeEntry("Device status", "Firmware and reports", Modifier.weight(1f)) { open(Page.CHECKS) }
    }
}

@Composable
internal fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun HomeEntry(title: String, subtitle: String, modifier: Modifier, open: () -> Unit) {
    Surface(onClick = open, modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun Metric(label: String, value: String, modifier: Modifier = Modifier, icon: Int? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Image(painterResource(icon), null, Modifier.size(15.dp))
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun Detail(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(0.9f), color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "—", Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun VbotFunctionStatus.label() = when (this) {
    VbotFunctionStatus.RUNNING -> "Running"
    VbotFunctionStatus.IDLE -> "Idle"
    VbotFunctionStatus.INTERRUPTED -> "Interrupted"
    VbotFunctionStatus.UNKNOWN -> "—"
}

internal fun VbotState.networkLabel() = when (networkStatusCode?.let(EnumNetStaus::forNumber)) {
    EnumNetStaus.E_NET_STATUS_WIFI -> "Wi-Fi"
    EnumNetStaus.E_NET_STATUS_CELLUAR -> "Cellular"
    EnumNetStaus.E_NET_STATUS_OFF -> "Offline"
    else -> "—"
}

internal fun VbotState.uwbLabel() = when (uwb?.status) {
    EnumUWBStatus.S_IDLE -> "Idle"
    EnumUWBStatus.S_CONNECTED -> "Connected"
    EnumUWBStatus.S_RANGING -> "Ranging"
    else -> "—"
}

private fun VbotConnection.description() = when (this) {
    VbotConnection.Disconnected -> "Disconnected"
    is VbotConnection.Connecting -> "Connecting via ${transport.name}"
    is VbotConnection.Connected -> "Connected via ${transport.name}"
    is VbotConnection.Failed -> "${transport.name} · $reason"
}

private fun isBluetoothAddress(value: String) = Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}").matches(value)
