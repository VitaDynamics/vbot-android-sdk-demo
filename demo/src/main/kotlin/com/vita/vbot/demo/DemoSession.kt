package com.vita.vbot.demo

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.MessageLite
import com.vita.vbot.sdk.VbotAdvertisement
import com.vita.vbot.sdk.VbotCodec
import com.vita.vbot.sdk.VbotCommandEncoder
import com.vita.vbot.sdk.VbotCommands
import com.vita.vbot.sdk.VbotConnection
import com.vita.vbot.sdk.VbotContext
import com.vita.vbot.sdk.VbotDevice
import com.vita.vbot.sdk.VbotTransport
import com.vita.vbot.sdk.protocol.BmsStatusMsg
import com.vita.vbot.sdk.protocol.ChargingProtectionConfigMsg
import com.vita.vbot.sdk.protocol.CommonRespMsg
import com.vita.vbot.sdk.protocol.EnumActionType
import com.vita.vbot.sdk.protocol.MsgId
import com.vita.vbot.sdk.protocol.WifiInfoMsg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

internal enum class Verification { SENT, VERIFIED, REJECTED, TIMEOUT, ERROR }

internal data class Operation(val label: String, val result: Verification, val detail: String)

internal data class WifiNetwork(val ssid: String, val signal: Int, val connected: Boolean)

internal data class DemoUi(
    val authorizedSerial: String? = null,
    val busy: Boolean = false,
    val scanning: Boolean = false,
    val advertisements: List<VbotAdvertisement> = emptyList(),
    val operations: List<Operation> = emptyList(),
    val frameCounts: Map<Int, Int> = emptyMap(),
    val wifiNetworks: List<WifiNetwork> = emptyList(),
    val chargingProtection: ChargingProtectionConfigMsg? = null,
)

internal class DemoSession(app: Application) : AndroidViewModel(app) {
    private val host = app as? DemoProvisioning
    private var imported: DemoProvisioning? = null
    private var confirmedBle: VbotDevice? = null
    private val requestIds = AtomicInteger((System.nanoTime() and 0x3fffffff).toInt())
    private val sdk = VbotContext(app) { device, transport ->
        when (transport) {
            VbotTransport.BLE -> confirmedBle == device
            VbotTransport.AORTA -> (imported ?: host)?.authorize(device, transport) == true
        }
    }
    private val mutableUi = MutableStateFlow(DemoUi(authorizedSerial = host?.suggestedSerial))
    val ui = mutableUi.asStateFlow()
    val state = sdk.state
    val connection = sdk.connection
    private var operation: Job? = null
    private var scanner: Job? = null
    private var jogging = false

    init {
        viewModelScope.launch {
            sdk.frames.collect { frame ->
                val current = mutableUi.value
                val counters = current.frameCounts
                val reported = when (val message = frame.message) {
                    is ChargingProtectionConfigMsg -> current.copy(chargingProtection = message)
                    is WifiInfoMsg -> current.copy(wifiNetworks = (
                        current.wifiNetworks.filterNot { it.ssid == message.ssid } +
                            WifiNetwork(message.ssid, message.signal, message.connected)
                        ).sortedWith(compareByDescending<WifiNetwork> { it.connected }.thenByDescending { it.signal }).take(20))
                    else -> current
                }
                mutableUi.value = reported.copy(frameCounts = counters + (frame.id to ((counters[frame.id] ?: 0) + 1)))
            }
        }
    }

    fun nextRequestId(): Int = requestIds.incrementAndGet()

    fun importAortaConfig(uri: Uri) = runOperation("Import Aorta config") {
        val profile = try {
            withContext(Dispatchers.IO) { ImportedProvisioning.read(getApplication<Application>().contentResolver, uri) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            error("Could not read the config. Check its JSON format and fields.")
        }
        imported = profile
        mutableUi.value = mutableUi.value.copy(authorizedSerial = profile.suggestedSerial)
        record("Import Aorta config", Verification.VERIFIED, profile.suggestedSerial)
    }

    fun connectAorta(serial: String) = connect(serial, "Connect via Aorta") { device ->
        val options = checkNotNull((imported ?: host)?.aortaOptions(device)) { "Import an Aorta config for this robot." }
        sdk.connectAorta(device, options)
    }

    fun connectBle(serial: String, address: String) = connect(serial, "Connect via BLE") { device ->
        confirmedBle = device
        try {
            sdk.connectBle(device, address)
        } finally {
            confirmedBle = null
        }
    }

    private fun connect(serial: String, label: String, open: suspend (VbotDevice) -> Unit) {
        stopScan()
        runOperation(label) {
            val device = VbotDevice(serial.trim())
            clearReports()
            open(device)
            record(label, Verification.VERIFIED, device.serialNumber)
            readOverview()
        }
    }

    fun disconnect() {
        val previous = operation
        previous?.cancel()
        stopScan()
        operation = viewModelScope.launch {
            previous?.join()
            mutableUi.value = mutableUi.value.copy(busy = true)
            try {
                sdk.disconnect()
                clearReports()
                record("Disconnect", Verification.VERIFIED, "Session closed and state cache cleared")
            } finally {
                mutableUi.value = mutableUi.value.copy(busy = false)
            }
        }
    }

    fun scanBle() {
        if (scanner?.isActive == true) return
        scanner = viewModelScope.launch {
            mutableUi.value = mutableUi.value.copy(scanning = true, advertisements = emptyList())
            try {
                withTimeoutOrNull(10_000) {
                    sdk.scanBle().collect { ad ->
                        val current = mutableUi.value
                        mutableUi.value = current.copy(
                            advertisements = (current.advertisements.filterNot { it.address == ad.address } + ad)
                                .sortedByDescending { it.rssi }.take(20),
                        )
                    }
                }
                val found = mutableUi.value.advertisements.size
                record("BLE scan", if (found > 0) Verification.VERIFIED else Verification.TIMEOUT, "$found advertisements found")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                record("BLE scan", Verification.ERROR, "Check that Bluetooth is on and Nearby devices access is allowed.")
            } finally {
                mutableUi.value = mutableUi.value.copy(scanning = false)
            }
        }
    }

    fun stopScan() { scanner?.cancel() }

    fun stopJog() {
        if (jogging) operation?.cancel()
    }

    fun permissionDenied() = record("Bluetooth permission", Verification.REJECTED, "Allow access to nearby devices to use Bluetooth.")

    fun refresh() = runOperation("Refresh device status") { readOverview() }

    fun clearWifiResults() {
        mutableUi.value = mutableUi.value.copy(wifiNetworks = emptyList())
    }

    private fun clearReports() {
        mutableUi.value = mutableUi.value.copy(frameCounts = emptyMap(), wifiNetworks = emptyList(), chargingProtection = null)
    }

    private suspend fun readOverview() {
        val label = "Refresh device status"
        val verified = coroutineScope {
            val freshState = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(5_000) {
                    val seen = mutableSetOf<Int>()
                    sdk.frames.first { frame ->
                        if (frame.message != null) seen += frame.id
                        MsgId.MESSAGE_FSM_STATE_RESP_VALUE in seen && MsgId.MESSAGE_UWB_STATUS_RESP_VALUE in seen
                    }
                } != null
            }
            val accepted = listOf(
                VbotCommands.status(),
                VbotCommands.uwbStatus(),
                VbotCommands.uwbFirmwareVersion(),
            ).count { sdk.sendFrame(it) }
            if (accepted != 3) {
                freshState.cancel()
                record(label, Verification.REJECTED, "Transport accepted $accepted of 3 queries")
                return@coroutineScope false
            }
            freshState.await().also {
                if (!it) record(label, Verification.TIMEOUT, "Timed out waiting for FSM and UWB reports")
            }
        }
        if (verified) {
            val battery = state.value.batteryPercent?.let { "$it%" } ?: "—"
            val cache = if (sdk.latestMessage(MsgId.MESSAGE_BMS_STATUS_VALUE) is BmsStatusMsg) {
                "confirmed"
            } else {
                "awaiting report"
            }
            record(label, Verification.VERIFIED, "FSM and UWB received · Battery $battery · C++ cache $cache")
        }
    }

    fun send(label: String, frame: () -> ByteArray) = runOperation(label) {
        val outgoing = frame()
        val id = VbotCodec.decode(outgoing).single().id
        val accepted = sdk.sendFrame(outgoing)
        record(label, if (accepted) Verification.SENT else Verification.REJECTED, messageName(id))
    }

    fun jog(label: String, forward: Float, yaw: Float) = runOperation(label) {
        jogging = true
        var sent = 0
        var timestamp = 0L
        try {
            while (sent < 3) {
                timestamp = maxOf(timestamp + 1, System.currentTimeMillis())
                if (!sdk.sendFrame(VbotCommandEncoder.buildMoveCommand(
                        bodyX = forward, bodyYaw = yaw, timestamp = timestamp,
                    ))) break
                sent++
                delay(90)
            }
        } finally {
            jogging = false
            if (timestamp > 0L) {
                withContext(NonCancellable) {
                    val stop = VbotCommandEncoder.buildMoveCommand(timestamp = maxOf(timestamp + 1, System.currentTimeMillis()))
                    if (!sdk.sendFrame(stop)) record("Center joystick", Verification.REJECTED, "The transport did not accept the command")
                }
            }
        }
        record(label, if (sent > 0) Verification.SENT else Verification.REJECTED, "Sent $sent of 3 jog frames")
    }

    fun emergencyStop() {
        val previous = operation
        previous?.cancel()
        runOperation("Emergency stop", after = previous) {
            val accepted = sdk.sendFrame(VbotCommandEncoder.buildActionCmd(EnumActionType.E_PASSIVE))
            record("Emergency stop", if (accepted) Verification.SENT else Verification.REJECTED, "E_PASSIVE")
        }
    }

    fun <T : MessageLite> query(
        label: String,
        frame: () -> ByteArray,
        type: Class<T>,
        matches: (T) -> Boolean = { true },
        accepted: (T) -> Boolean = { true },
        describe: (T) -> String,
    ) = runOperation(label) {
        awaitReport(label, frame(), type, matches) {
            (if (accepted(it)) Verification.VERIFIED else Verification.REJECTED) to describe(it)
        }
    }

    fun writeWithAck(label: String, id: Int, frame: () -> ByteArray) = runOperation(label) {
        awaitReport(label, frame(), CommonRespMsg::class.java, { it.id == id }) {
            if (it.result) Verification.VERIFIED to "Robot acknowledged request ${it.id}"
            else Verification.REJECTED to "Robot rejected request ${it.id}"
        }
    }

    private suspend fun <T : MessageLite> awaitReport(
        label: String,
        outgoing: ByteArray,
        type: Class<T>,
        matches: (T) -> Boolean,
        describe: (T) -> Pair<Verification, String>,
    ) = coroutineScope {
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(5_000) {
                val frame = sdk.frames.first { type.isInstance(it.message) && matches(type.cast(it.message)!!) }
                type.cast(frame.message)
            }
        }
        if (!sdk.sendFrame(outgoing)) {
            reply.cancel()
            record(label, Verification.REJECTED, "The transport did not accept the command")
        } else {
            val response = reply.await()
            if (response == null) record(label, Verification.TIMEOUT, "Command sent. No matching response received.")
            else {
                val (status, detail) = describe(response)
                record(label, status, detail)
            }
        }
    }

    private fun runOperation(label: String, after: Job? = null, action: suspend () -> Unit) {
        if (after == null && operation?.isActive == true) return
        operation = viewModelScope.launch {
            after?.join()
            mutableUi.value = mutableUi.value.copy(busy = true)
            try {
                action()
            } catch (_: TimeoutCancellationException) {
                record(label, Verification.TIMEOUT, "Connection timed out")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failure = connection.value as? VbotConnection.Failed
                record(label, Verification.ERROR, failure?.reason ?: error.message ?: "Operation failed")
            } finally {
                mutableUi.value = mutableUi.value.copy(busy = false)
            }
        }
    }

    private fun record(label: String, result: Verification, detail: String) {
        val current = mutableUi.value
        mutableUi.value = current.copy(operations = (listOf(Operation(label, result, detail)) + current.operations).take(40))
        Log.i("VbotDemo", "$label $result $detail")
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.Default).launch { sdk.close() }
        super.onCleared()
    }
}

internal fun messageName(id: Int): String =
    MsgId.forNumber(id)?.name?.removePrefix("MESSAGE_") ?: "Message $id"
