# VBot Android SDK API

## Add the dependency

SDK 0.2.3 targets Android API 24 or newer on `arm64-v8a`. The reference consumer uses Kotlin 2.3.21 and compileSdk 36. The Maven artifact includes the C++ and Zenoh native libraries and declares its transitive dependencies.

Copy [vbot-sdk.gradle.kts](../gradle/vbot-sdk.gradle.kts) into the consumer project's `gradle/` directory. Keep the existing repositories in the consumer's `settings.gradle.kts` and apply the script after `dependencyResolutionManagement`.

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

apply(from = "gradle/vbot-sdk.gradle.kts")
```

Add the dependency to the Android module's `build.gradle.kts`.

```kotlin
dependencies {
    implementation("com.vita.vbot:vbot-sdk:0.2.3")
}
```

The script downloads the AAR and its POM over HTTPS without GitHub credentials. On the first build it verifies the SHA-256 checksum of each file and stores both in a local Maven cache. Offline builds require the SDK and its transitive dependencies to be cached. [SDK release assets](https://github.com/VitaDynamics/vbot-android-sdk-demo/releases/tag/v0.2.3)

## Connect to a robot

Types are in `com.vita.vbot.sdk`. Generated Protobuf types and `MsgId` are in `com.vita.vbot.sdk.protocol`.

`VbotContext(context, accessProvider)` owns one active robot session. The host implements `VbotAccessProvider.authorize(device, transport)` using its authorized-device inventory or lab provisioning. Pass the exact device serial number to `VbotDevice`.

| Method | Behavior |
| --- | --- |
| `scanBle(): Flow<VbotAdvertisement>` | Collect BLE advertisements. Each has a nullable name, address, and RSSI. Cancel collection to stop scanning. |
| `connectBle(device, address)` or `connectBle(device, peripheral)` | Open the BLE GATT session. A matching serial-number report is required before the connection is ready. |
| `connectAorta(device, options)` | Open an Aorta session and acquire the control lease. By default, the SDK requires confirmation from firmware that supports leases. |
| `disconnect()` | End the active session and clear its state and cache. The context can reconnect. |
| `close()` | Release the active link and native session. Use a new context for subsequent work. |

Observe the `connection` state flow for `Disconnected`, `Connecting`, `Connected`, or `Failed`. Connection calls can throw for authorization, transport, or timeout failures. Serial confirmation and leases are handled inside the SDK.

The SDK manifest merges the necessary BLE declarations. At runtime, request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on Android 12 and newer. On Android 11 and older, request `ACCESS_FINE_LOCATION` before scanning.

For Aorta use `VbotAortaOptions(operatorId) { device -> provisionConfig(device) }`. The operator ID must be nonblank and at most 255 UTF-8 bytes. The provider returns an environment-specific Zenoh JSON5 configuration. `VbotAortaOptions.withProtectedConfig` also accepts a base configuration and a callback that can apply protected settings with `VbotAortaConfigTarget.insertJson5(path, value)`. Pass the result of each setting operation back to the SDK. Enable `allowLegacyFirmware` only when your lab workflow explicitly supports firmware without lease confirmation.

```kotlin
import android.content.Context
import com.vita.vbot.sdk.VbotAccessProvider
import com.vita.vbot.sdk.VbotAortaOptions
import com.vita.vbot.sdk.VbotContext
import com.vita.vbot.sdk.VbotDevice
import com.vita.vbot.sdk.VbotState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

suspend fun readRobotState(
    context: Context,
    serialNumber: String,
    accessProvider: VbotAccessProvider,
    options: VbotAortaOptions,
): VbotState {
    val client = VbotContext(context, accessProvider)
    try {
        client.connectAorta(VbotDevice(serialNumber), options)
        client.refreshState()
        return withTimeout(8_000) {
            client.state.first { it.fsm != null && it.uwb != null }
        }
    } finally {
        withContext(NonCancellable) { client.close() }
    }
}
```

## Read robot state

- `state: StateFlow<VbotState>` is the current immutable snapshot. Use it for battery, network and posture codes, firmware versions, UWB and FSM reports, follow state, brightness, volume, and other decoded fields.
- `refreshState()` sends requests for FSM, UWB status, and UWB firmware. Wait for the reports you need. Battery and other values can arrive in separate reports.
- `VbotState.functionStatus(VbotFunction)` returns `IDLE`, `RUNNING`, `INTERRUPTED`, or `UNKNOWN`. `systemStatus(byteIndex)` exposes individual decoded system values. Nullable fields indicate data that has yet to be reported.
- `frames: SharedFlow<VbotCodec.Frame>` is the live response stream. Start collection before sending commands that expect a fast reply. Use `latestMessage(messageId)` for the most recent cached typed message of a particular ID.

UWB status can be `S_IDLE`, `S_CONNECTED`, or `S_RANGING`. The Aorta or BLE connection is the command and report transport. The robot's UWB ranging target uses the robot's supported pairing and ranging workflow. Starting Follow requires the feature to be unlocked and its target ready.

## Send and confirm commands

`send(message: MessageLite)` encodes and sends a generated Protobuf message. `sendFrame(frame: ByteArray)` sends one complete frame produced by `VbotCommands`, `VbotCommandEncoder`, or `VbotCodec`. A return value of `true` indicates transport acceptance. For firmware operations with acknowledgments, match the response request ID. For operations that report state, use the relevant state transition.

This example sends a volume command with an application-assigned request ID and waits for its `CommonRespMsg` that carries the matching ID.

```kotlin
import com.vita.vbot.sdk.VbotCommandEncoder
import com.vita.vbot.sdk.VbotContext
import com.vita.vbot.sdk.protocol.CommonRespMsg
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

suspend fun setVolume(client: VbotContext, percent: Int, requestId: Int): Boolean =
    coroutineScope {
        require(percent in 0..100)
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                client.frames.map { it.message }.filterIsInstance<CommonRespMsg>()
                    .first { it.id == requestId }
            }
        }
        if (client.sendFrame(VbotCommandEncoder.buildVolumeMessage(percent, requestId))) {
            response.await().result
        } else {
            response.cancel()
            false
        }
    }
```

`setFollow(enabled)` sends the Follow start or stop command. Observe `followStatus` to confirm the resulting state. Watch for state changes and handle timeouts on either transport. See the [command reference](COMMANDS.md) for builder families.

## Encode and decode frames

| API | Purpose |
| --- | --- |
| `VbotCodec.encode(message)` | Encodes a bound Protobuf type and selects its protocol ID. |
| `VbotCodec.encode(id, message)` | Encodes with the supplied `MsgId`. Supply the corresponding generated message type. |
| `VbotCodec.encodeRaw(id, payload)` | Frames a raw payload with a 16-bit message ID. |
| `VbotCodec.decode(packet)` | Decodes complete frames from a byte array. A trailing incomplete frame raises an error. |
| `VbotCodec.StreamDecoder.feed(chunk)` | Decodes complete frames and retains an incomplete tail. Use one instance per ordered stream and call `reset()` when the stream is replaced. |

Each `Frame` has an `id`, raw `payload`, and nullable typed `message`. Unknown IDs and malformed typed payloads can still carry raw bytes. The wire header is a little-endian 16-bit message ID followed by a little-endian 16-bit payload length. The C++ core handles command assembly, wire decoding, and state projection. The Kotlin API exposes the typed objects and coroutine flows.
