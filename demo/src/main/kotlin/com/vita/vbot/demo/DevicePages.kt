package com.vita.vbot.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vita.vbot.sdk.SpeechRecognitionMode
import com.vita.vbot.sdk.UwbGesture
import com.vita.vbot.sdk.VbotCommandEncoder as Commands
import com.vita.vbot.sdk.VbotCommands
import com.vita.vbot.sdk.VbotConnection
import com.vita.vbot.sdk.VbotFunction
import com.vita.vbot.sdk.VbotFunctionStatus
import com.vita.vbot.sdk.VbotState
import com.vita.vbot.sdk.VbotTransport
import com.vita.vbot.sdk.protocol.AudioSpeakerMsg
import com.vita.vbot.sdk.protocol.BtClassicConnectedRespMsg
import com.vita.vbot.sdk.protocol.ChargingProtectionConfigMsg
import com.vita.vbot.sdk.protocol.DeviceSNMsg
import com.vita.vbot.sdk.protocol.EnumActionType
import com.vita.vbot.sdk.protocol.EnumBotStatus
import com.vita.vbot.sdk.protocol.EnumSpeechType
import com.vita.vbot.sdk.protocol.EnumUWBStatus
import com.vita.vbot.sdk.protocol.FirmwareVersionMsg
import com.vita.vbot.sdk.protocol.FunctionState
import com.vita.vbot.sdk.protocol.GamepadDeviceAction
import com.vita.vbot.sdk.protocol.GamepadDeviceRespMsg
import com.vita.vbot.sdk.protocol.GetIPResponseMsg
import com.vita.vbot.sdk.protocol.MappingFunc
import com.vita.vbot.sdk.protocol.MappingModeMsg
import com.vita.vbot.sdk.protocol.NavigationFunc
import com.vita.vbot.sdk.protocol.NavigationModeMsg
import com.vita.vbot.sdk.protocol.NavigationTaskState
import com.vita.vbot.sdk.protocol.RecordControlAction
import com.vita.vbot.sdk.protocol.RecordControlRespMsg
import com.vita.vbot.sdk.protocol.UwbCustomRespMsg
import com.vita.vbot.sdk.protocol.UWBStatusRespMsg
import com.vita.vbot.sdk.protocol.WifiInfoMsg
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun ControlPage(state: VbotState, connected: Boolean, ready: Boolean, session: DemoSession) {
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var speech by rememberSaveable { mutableStateOf("") }
    fun action(label: String, command: EnumActionType) {
        confirm = label to { session.send(label) { Commands.buildActionCmd(command) } }
    }
    Panel("Robot actions") {
        Detail("Robot posture", state.postureCode?.let(EnumBotStatus::forNumber)?.name?.removePrefix("E_BOT_STATUS_"))
        ActionRow {
            OutlinedButton({ action("Stand", EnumActionType.E_ACTION_STAND) }, enabled = ready) { Text("Stand") }
            OutlinedButton({ action("Lie down", EnumActionType.E_ACTION_ACTION_DOWN) }, enabled = ready) { Text("Lie down") }
            OutlinedButton({ action("Sit", EnumActionType.E_ACTION_SIT) }, enabled = ready) { Text("Sit") }
        }
        OutlinedButton(
            onClick = session::emergencyStop,
            enabled = connected,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Emergency stop") }
    }
    Panel("Joystick jog") {
        var armed by remember { mutableStateOf(false) }
        LaunchedEffect(state.serialNumber) { armed = false }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Enable short jogs", Modifier.weight(1f))
            Switch(checked = armed, onCheckedChange = { armed = it }, enabled = ready)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedButton({ session.jog("Forward", 1f, 0f) }, enabled = ready && armed) { Text("Forward") }
            ActionRow {
                OutlinedButton({ session.jog("Turn left", 0f, 1f) }, enabled = ready && armed) { Text("Turn left") }
                OutlinedButton({ session.jog("Reverse", -1f, 0f) }, enabled = ready && armed) { Text("Reverse") }
                OutlinedButton({ session.jog("Turn right", 0f, -1f) }, enabled = ready && armed) { Text("Turn right") }
            }
        }
    }
    Panel("Follow and Companion") {
        Detail("Follow", state.followStatus.label())
        Detail("Follow availability", state.followEnabledStatus.label())
        Detail("Traction", state.tractionStatus.label())
        Detail("Outdoor Companion", state.outdoorCompanionStatus.label())
        ActionRow {
            Button({
                confirm = "Start following" to {
                    session.send("Start following") { VbotCommands.follow(true) }
                }
            }, enabled = ready) { Text("Start following") }
            OutlinedButton({ session.send("Stop following") { VbotCommands.follow(false) } }, enabled = ready) {
                Text("Stop following")
            }
        }
        ActionRow {
            OutlinedButton({
                val enabled = state.tractionStatus != VbotFunctionStatus.RUNNING
                val label = if (enabled) "Enable Traction" else "Disable Traction"
                confirm = label to { session.send(label) { VbotCommands.traction(enabled) } }
            }, enabled = ready) { Text("Traction mode") }
            OutlinedButton({
                val enabled = state.outdoorCompanionStatus != VbotFunctionStatus.RUNNING
                val label = if (enabled) "Start Outdoor Companion" else "Stop Outdoor Companion"
                confirm = label to { session.send(label) { VbotCommands.outdoorCompanion(enabled) } }
            }, enabled = ready) { Text("Companion mode") }
        }
        OutlinedButton({
            session.send("Enable unlocked Follow") { Commands.buildToggleFunction(FunctionState.F_FOLLOW, true) }
        }, enabled = ready) { Text("Enable Follow") }
    }
    Panel("Text-to-speech") {
        OutlinedTextField(
            value = speech, onValueChange = { speech = it.take(200) }, Modifier.fillMaxWidth(),
            label = { Text("Text to speak") }, singleLine = true,
        )
        Button({ session.send("Send text to robot") { Commands.buildChatTextMessage(speech.trim()) } },
            enabled = ready && speech.isNotBlank()) { Text("Send text to robot") }
    }
    confirm?.let { (label, execute) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(label) },
            text = { Text("Make sure the area around the robot is clear before sending this command.") },
            confirmButton = { Button({ confirm = null; execute() }, enabled = ready) { Text("Send command") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun UwbPage(state: VbotState, ready: Boolean, session: DemoSession) {
    var name by rememberSaveable { mutableStateOf("") }
    var capability by rememberSaveable { mutableStateOf("") }
    var gesture by rememberSaveable { mutableStateOf(UwbGesture.SINGLE) }
    Panel("UWB tag") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_uwb_list_leading), null, Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(state.uwbLabel(), style = MaterialTheme.typography.titleLarge)
                val version = state.uwbFirmwareVersion?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
                Text(version?.let { "Firmware $it" } ?: "Firmware version unavailable", style = MaterialTheme.typography.bodySmall)
            }
            Image(
                painterResource(if (state.uwb?.status in setOf(EnumUWBStatus.S_CONNECTED, EnumUWBStatus.S_RANGING))
                    R.drawable.ic_state_remote else R.drawable.ic_state_remote_unconnected),
                null, Modifier.size(24.dp),
            )
        }
        Detail("Tag battery", state.uwb?.takeIf { it.status != EnumUWBStatus.S_IDLE }?.bmsStatus?.let { "$it%" })
        Detail("Charging status", state.uwb?.takeIf { it.hasCharging() }?.let { if (it.charging) "Charging" else "Not charging" })
        OutlinedButton({
            session.query("Refresh UWB status", VbotCommands::uwbStatus, UWBStatusRespMsg::class.java) { it.status.name }
        }, enabled = ready) { Text("Refresh UWB status") }
    }
    Panel("Pair and locate") {
        OutlinedTextField(name, { name = it.take(64) }, Modifier.fillMaxWidth(),
            label = { Text("Tag name") }, singleLine = true)
        ActionRow {
            OutlinedButton({
                val id = session.nextRequestId()
                session.writeWithAck("Pair UWB tag", id) { Commands.buildUwbPairMessage(name.trim(), id) }
            }, enabled = ready && name.isNotBlank()) { Text("Pair tag") }
            OutlinedButton({
                val id = session.nextRequestId()
                session.writeWithAck("Locate UWB tag", id) { Commands.buildUwbFindMessage(name.trim(), id) }
            }, enabled = ready && name.isNotBlank()) { Text("Locate tag") }
        }
    }
    Panel("Tag gestures") {
        OutlinedButton({
            session.query("Read UWB gestures", Commands::buildUwbCustomQueryMessage, UwbCustomRespMsg::class.java,
                accepted = { it.config()?.optBoolean("success") == true }) {
                it.config()?.bindingSummary() ?: "Could not read the gesture configuration"
            }
        }, enabled = ready) { Text("Read gestures") }
        ActionRow {
            UwbGesture.entries.forEach { item ->
                FilterChip(gesture == item, { gesture = item }, {
                    Text(when (item) {
                        UwbGesture.SINGLE -> "Single tap"
                        UwbGesture.DOUBLE -> "Double tap"
                        UwbGesture.TRIPLE -> "Triple tap"
                    })
                })
            }
        }
        OutlinedTextField(capability, { capability = it.take(80) }, Modifier.fillMaxWidth(),
            label = { Text("Action capability ID") }, singleLine = true)
        OutlinedButton({
            val value = capability.trim()
            session.query("Update UWB gesture", { Commands.buildUwbCustomUpdateMessage(gesture, value) },
                UwbCustomRespMsg::class.java, accepted = { response ->
                    val config = response.config()
                    config?.optBoolean("success") == true &&
                        (config.optJSONArray("bindings") == null || config.bindings()[gesture.action] == value)
                }) { it.config()?.bindingSummary() ?: "Could not read the gesture response" }
        }, enabled = ready && capability.isNotBlank()) { Text("Save gesture") }
    }
}

private fun UwbCustomRespMsg.config(): JSONObject? = runCatching { JSONObject(info) }.getOrNull()

private fun JSONObject.bindings(): Map<Int, String> = buildMap {
    val entries = optJSONArray("bindings") ?: return@buildMap
    for (i in 0 until entries.length()) {
        val entry = entries.optJSONObject(i) ?: continue
        val gesture = entry.optInt("gesture")
        if (gesture in 1..3) put(gesture, entry.optString("capability_id"))
    }
}

private fun JSONObject.bindingSummary(): String {
    if (!optBoolean("success")) return "Robot rejected the gesture request"
    val names = listOf("Single tap", "Double tap", "Triple tap")
    return bindings().entries.joinToString(" · ") { (gesture, capability) -> "${names[gesture - 1]} $capability" }
        .ifBlank { "Robot acknowledged the gesture request" }
}

@Composable
internal fun SettingsPage(state: VbotState, ui: DemoUi, ready: Boolean, session: DemoSession) {
    Panel("Display and sound") {
        LevelSetting("Volume", state.volume, ready) { value ->
            val id = session.nextRequestId()
            session.writeWithAck("Set volume", id) { Commands.buildVolumeMessage(value, id) }
        }
        LevelSetting("Screen brightness", state.screenBrightness, ready) { value ->
            val id = session.nextRequestId()
            session.writeWithAck("Set screen brightness", id) { Commands.buildScreenBrightnessMessage(value, id) }
        }
        LevelSetting("Front light brightness", state.frontLightBrightness, ready) { value ->
            val id = session.nextRequestId()
            session.writeWithAck("Set front light brightness", id) { Commands.buildLightBrightnessMessage(value, value > 0, id) }
        }
        ActionRow {
            OutlinedButton({
                val id = session.nextRequestId()
                session.query("Check audio output", { Commands.buildAudioSpeakerQueryMessage(id) }, AudioSpeakerMsg::class.java,
                    { it.id == id }) { it.name.ifBlank { "Output status ${it.status}" } }
            }, enabled = ready) { Text("Check audio output") }
            OutlinedButton({
                val id = session.nextRequestId()
                session.query("Check Bluetooth speakers", { Commands.buildBluetoothSpeakerQuery(id) }, BtClassicConnectedRespMsg::class.java,
                    { it.hasId() && it.id == id }) { "${it.status.name} · ${it.devicesCount} connected" }
            }, enabled = ready) { Text("Check Bluetooth speakers") }
        }
    }
    Panel("Robot settings") {
        ToggleSetting("Head touch", state, VbotFunction.TOUCH_HEAD, ready, session, Commands::buildTouchHeadMessage)
        ToggleSetting("Rear touch", state, VbotFunction.TOUCH_REAR, ready, session, Commands::buildTouchButtMessage)
        ToggleSetting("Visual interaction", state, VbotFunction.VISION_DETECTION, ready, session, Commands::buildVisionDetection)
        ToggleSetting("Proactive interaction", state, VbotFunction.PROACTIVE_INTERACTION, ready, session, Commands::buildProactiveInteraction)
        ToggleSetting("Auto light in low light", state, VbotFunction.LOW_LIGHT_AUTO_LAMP, ready, session, Commands::buildLowLightAutoLamp)
        ToggleSetting("Lie down when idle", state, VbotFunction.AUTO_LIE_DOWN, ready, session, Commands::buildAutoLieDown)
        ToggleSetting("Save power while charging", state, VbotFunction.POWER_SAVE_WHILE_CHARGING, ready, session, Commands::buildPowerSavingAtCharge)
        ToggleSetting("Save power while lying down", state, VbotFunction.POWER_SAVE_WHILE_LYING, ready, session, Commands::buildPowerSavingWhenIdle)
        ToggleSetting("Turn off ear lights in power-saving mode", state, VbotFunction.EAR_LIGHT_OFF_IN_POWER_SAVE, ready, session, Commands::buildPowerSavingEarLightOff)
    }
    Panel("Voice") {
        Detail("Speech recognition", state.functionStatus(VbotFunction.SPEECH_RECOGNITION).label())
        ActionRow {
            listOf("Off" to SpeechRecognitionMode.OFF, "Baidu" to SpeechRecognitionMode.BAIDU,
                "Doubao" to SpeechRecognitionMode.DOUBAO).forEach { (label, mode) ->
                OutlinedButton({
                    val id = session.nextRequestId()
                    session.writeWithAck("Speech recognition · $label", id) {
                        Commands.buildVoiceMessage(EnumSpeechType.E_RECOGNITION, mode, id)
                    }
                }, enabled = ready) { Text(label) }
            }
        }
        val timeout = state.micSilenceTimeoutSeconds
        var selected by remember(timeout) { mutableFloatStateOf((timeout ?: 15).toFloat()) }
        Detail("Voice silence timeout", timeout?.let { "$it s" })
        Slider(selected, { selected = it }, enabled = ready && timeout != null, valueRange = 15f..180f)
        OutlinedButton({
            val id = session.nextRequestId()
            session.writeWithAck("Set voice silence timeout", id) { Commands.buildMicSilenceTimeout(selected.roundToInt(), id) }
        }, enabled = ready && timeout != null) { Text("Apply timeout ${selected.roundToInt()} s") }
    }
    ChargingSettings(ui.chargingProtection, ready, session)
    NetworkSettings(ui.wifiNetworks, ready, session)
}

@Composable
private fun ChargingSettings(report: ChargingProtectionConfigMsg?, ready: Boolean, session: DemoSession) {
    var chosen by remember(report?.chargeLimitPercent) { mutableIntStateOf(report?.chargeLimitPercent ?: 100) }
    Panel("Charging protection") {
        OutlinedButton({
            val id = session.nextRequestId()
            session.query("Read charging protection", { Commands.buildChargingProtectionMessage(id) },
                ChargingProtectionConfigMsg::class.java, { it.hasId() && it.id == id }) {
                "Charge limit ${it.chargeLimitPercent}% · Scheduled optimization ${if (it.scheduledChargingOptEnabled) "on" else "off"}"
            }
        }, enabled = ready) { Text("Read charging protection") }
        Detail("Charge limit", report?.let { "${it.chargeLimitPercent}%" })
        ActionRow {
            (80..100 step 5).forEach { value ->
                FilterChip(chosen == value, { chosen = value }, { Text("$value%") }, enabled = ready && report != null)
            }
        }
        OutlinedButton({
            val id = session.nextRequestId()
            session.writeWithAck("Set charge limit", id) {
                Commands.buildChargingProtectionMessage(id, chargeLimitPercent = chosen)
            }
        }, enabled = ready && report != null) { Text("Apply $chosen% charge limit") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Scheduled charging optimization", Modifier.weight(1f))
            Switch(report?.scheduledChargingOptEnabled == true, { enabled ->
                val id = session.nextRequestId()
                session.writeWithAck("Scheduled charging optimization", id) {
                    Commands.buildChargingProtectionMessage(id, scheduledChargingEnabled = enabled)
                }
            }, enabled = ready && report != null)
        }
    }
}

@Composable
private fun NetworkSettings(networks: List<WifiNetwork>, ready: Boolean, session: DemoSession) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    Panel("Wi-Fi") {
        OutlinedButton({
            session.clearWifiResults()
            session.query("Scan for Wi-Fi on robot", Commands::buildGetWifiListMessage, WifiInfoMsg::class.java) {
                if (it.connected) "Robot reported its connected network" else "Received a scan result"
            }
        }, enabled = ready) { Text("Scan for Wi-Fi on robot") }
        networks.forEach { network ->
            TextButton({ ssid = network.ssid }, enabled = ready && network.ssid.isNotBlank()) {
                Text("${network.ssid} · ${if (network.connected) "Connected" else "Signal ${network.signal}"}")
            }
        }
        OutlinedTextField(ssid, { ssid = it.take(32) }, Modifier.fillMaxWidth(),
            label = { Text("Wi-Fi network name") }, singleLine = true)
        OutlinedTextField(password, { password = it.take(64) }, Modifier.fillMaxWidth(),
            label = { Text("Wi-Fi password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedButton({ confirm = true }, enabled = ready && ssid.isNotBlank()) { Text("Change robot Wi-Fi") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Change robot Wi-Fi") },
            text = { Text("Changing networks may disconnect the current Aorta session.") },
            confirmButton = {
                Button({
                    val value = password
                    password = ""
                    confirm = false
                    session.send("Change robot Wi-Fi") { Commands.buildWifiMessage(ssid.trim(), value, session.nextRequestId()) }
                }, enabled = ready) { Text("Send") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LevelSetting(label: String, reported: Int?, ready: Boolean, apply: (Int) -> Unit) {
    var selected by remember(reported) { mutableFloatStateOf((reported ?: 0).coerceIn(0, 100).toFloat()) }
    Detail(label, reported?.let { "$it%" })
    Slider(selected, { selected = it }, enabled = ready && reported != null, valueRange = 0f..100f)
    OutlinedButton({ apply(selected.roundToInt()) }, enabled = ready && reported != null) {
        Text("Apply ${selected.roundToInt()}%")
    }
    HorizontalDivider()
}

@Composable
private fun ToggleSetting(
    label: String,
    state: VbotState,
    function: VbotFunction,
    ready: Boolean,
    session: DemoSession,
    command: (Boolean) -> ByteArray,
) {
    val status = state.functionStatus(function)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(status.label(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(status == VbotFunctionStatus.RUNNING, { enabled ->
            session.send(label) { command(enabled) }
        }, enabled = ready && status != VbotFunctionStatus.UNKNOWN)
    }
}

@Composable
internal fun VerificationPage(ui: DemoUi, ready: Boolean, connection: VbotConnection, session: DemoSession) {
    val state by session.state.collectAsState()
    Panel("Device reports") {
        Image(painterResource(R.drawable.img_device_state_dog), contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(130.dp))
        Detail("Session serial number", (connection as? VbotConnection.Connected)?.device?.serialNumber)
        Detail("Battery", state.batteryPercent?.let { "$it%" })
        Detail("Network", state.networkLabel())
        Detail("FSM", state.fsm?.let { "Functions ${it.functionStatus.size()} B · System ${it.systemStatus.size()} B" })
        Detail("UWB", state.uwbLabel())
        state.firmwareVersions.forEach { (tag, version) -> Detail(tag, version) }
        ActionRow {
            Button(session::refresh, enabled = ready) { Text("Read status") }
            OutlinedButton({
                session.query("Read robot serial number", VbotCommands::serialNumber, DeviceSNMsg::class.java) { it.sn }
            }, enabled = ready && (connection as? VbotConnection.Connected)?.transport == VbotTransport.BLE) {
                Text("Read serial number (BLE)")
            }
            OutlinedButton({
                session.query("Read robot IP", Commands::buildGetIpMessage, GetIPResponseMsg::class.java) { it.ip }
            }, enabled = ready) { Text("Read IP") }
        }
        OutlinedButton({
            session.query("Read UWB firmware", VbotCommands::uwbFirmwareVersion, FirmwareVersionMsg::class.java,
                { it.tag == "uwb_tag" && it.hasVersion() },
                { it.version.isNotBlank() && !it.version.equals("unknown", ignoreCase = true) }) {
                it.version.takeUnless { value -> value.isBlank() || value.equals("unknown", ignoreCase = true) }
                    ?: "Robot responded without a valid firmware version"
            }
        }, enabled = ready) { Text("Read UWB firmware") }
    }
    Panel("Additional checks") {
        ActionRow {
            OutlinedButton({
                val id = UUID.randomUUID().toString()
                session.query("Read gamepad list", {
                    Commands.buildGamepadDeviceRequest(id, GamepadDeviceAction.GAMEPAD_DEVICE_ACTION_LIST)
                }, GamepadDeviceRespMsg::class.java, { it.requestId == id && it.terminal }, { it.success }) {
                    if (it.success) "${it.devicesCount} gamepads" else it.errorCode.ifBlank { "Robot reported a failure" }
                }
            }, enabled = ready) { Text("Gamepads") }
            OutlinedButton({
                val id = session.nextRequestId()
                session.query("Read mapping status", { Commands.buildMappingMessage(id, MappingFunc.F_QUERY) },
                    MappingModeMsg::class.java, { it.id == id }) { it.status.name }
            }, enabled = ready) { Text("Mapping status") }
        }
        ActionRow {
            OutlinedButton({
                val id = session.nextRequestId()
                session.query("Read navigation status", { Commands.buildNavigationMessage(id, NavigationFunc.N_QUERY) },
                    NavigationModeMsg::class.java, { it.id == id }, { it.taskState != NavigationTaskState.NAV_TASK_REJECTED }) {
                    it.error.ifBlank { it.taskState.name }
                }
            }, enabled = ready) { Text("Navigation status") }
            OutlinedButton({
                val id = UUID.randomUUID().toString()
                session.query("Read recording status", {
                    Commands.buildRecordControlMessage(RecordControlAction.RECORD_CONTROL_STATUS, id)
                }, RecordControlRespMsg::class.java, { it.requestId == id }, { it.status == 0 }) {
                    if (it.status != 0) "Robot status code ${it.status}" else if (it.recording) "Recording" else "Not recording"
                }
            }, enabled = ready) { Text("Recording status") }
        }
    }
    Panel("Operation results") {
        if (ui.operations.isEmpty()) Text("Connect a robot to see command and response results here.")
        ui.operations.take(15).forEach { operation ->
            val color = when (operation.result) {
                Verification.VERIFIED -> Color(0xFF286843)
                Verification.SENT -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            }
            val title = when (operation.result) {
                Verification.VERIFIED -> "Verified"
                Verification.SENT -> "Sent"
                Verification.REJECTED -> "Rejected"
                Verification.TIMEOUT -> "Timed out"
                Verification.ERROR -> "Failed"
            }
            Text("${operation.label} · $title", color = color, style = MaterialTheme.typography.labelLarge)
            Text(operation.detail, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
    }
    if (ui.frameCounts.isNotEmpty()) {
        Panel("SDK packets received") {
            ui.frameCounts.toSortedMap().forEach { (id, count) -> Detail(messageName(id), "$count") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
