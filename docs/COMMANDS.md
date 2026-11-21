# Command encoder reference

Each builder returns a complete `ByteArray` frame. Send it through `VbotContext.sendFrame`. Firmware and transport support vary by robot. Associate a request ID only with the response contract of that command. The SDK encodes the value passed by your app, including an explicitly present zero or false where the message supports it.

## Common commands

| Builder on `VbotCommands` | Purpose |
| --- | --- |
| `status()`, `serialNumber()` | Request the FSM or reported device serial number. |
| `uwbStatus()`, `uwbFirmwareVersion()`, `firmwareVersion(tag)` | Query UWB status, UWB firmware, or a named firmware component. |
| `follow(enabled)`, `traction(enabled)`, `outdoorCompanion(enabled)` | Start or stop the corresponding robot mode. |
| `stand()`, `lieDown()` | Request a posture change. |

## Command groups

The following methods are on `VbotCommandEncoder`. Protocol enums and messages used as arguments are in `com.vita.vbot.sdk.protocol`.

| Group | Builders |
| --- | --- |
| Motion | `buildActionCmd` (typed action or integer), `buildBodyActionCmd`, `buildHeadActionCmd`, `buildResetHeadCmd`, `buildMoveCommand`, `buildTimestampedCommand` |
| Device queries | `buildGetBotStatus`, `buildDeviceSNMessage`, `buildGetIpMessage`, `buildFirmwareVersionMessage`, `buildFunctionDetailMessage` |
| Follow and motion settings | `buildFollowingSpeedMessage`, `buildTractionInertiaMessage`, `buildToggleFunction`, `buildAvoidObstacleMessage`, `buildAvoidObstacleForAction` |
| UWB tag | `buildUwbPairMessage`, `buildUwbFindMessage`, `buildUwbCustomQueryMessage`, `buildUwbCustomUpdateMessage` |
| Gamepad | `buildGamepadDeviceRequest`, `buildJoyCustomQueryMessage`, `buildJoyCustomUpdateMessage` |
| Display, lighting and audio | `buildVolumeMessage`, `buildScreenBrightnessMessage`, `buildLightBrightnessMessage`, `buildIspAdjustMessage`, `buildLowLightAutoLamp`, `buildAudioSpeakerQueryMessage` |
| Bluetooth speakers | `buildBluetoothSpeakerScan`, `buildBluetoothSpeakerQuery`, `buildBluetoothSpeakerConnect`, `buildBluetoothSpeakerDisconnect` |
| Voice and interaction | `buildVoiceMessage` (Boolean or `SpeechRecognitionMode`), `buildMicSilenceTimeout`, `buildChatTextMessage`, `buildVoiceTTS`, `buildAgentModeMessage`, `buildTouchHeadMessage`, `buildTouchButtMessage`, `buildWakeUpTurn`, `buildVisionDetection`, `buildProactiveInteraction` |
| Charging and power | `buildChargeCmd`, `buildChargingProtectionMessage`, `buildPowerSavingMessage`, `buildPowerSavingAtCharge`, `buildPowerSavingWhenIdle`, `buildTalkInPowerSaving`, `buildPowerSavingEarLightOff`, `buildPowerOffMessage`, `buildIdleDetection`, `buildAutoLieDown` |
| Wireless and transport | `buildGetWifiListMessage`, `buildWifiMessage`, `buildSwitchWifiMessage`, `buildSwitchApCmd`, `buildSwitchAorta`, `buildSwitchRtcMessage`, `buildSwitchAudioCmd`, `buildSwitchPhoneAudioCmd`, `buildSwitchVideoCmd` |
| Photo, video and recording | `buildTakePhotoCmd`, `buildPhotoAcknowledgeMessage`, `buildDvrMessage`, `buildDeleteVideoMessage`, `buildRecordControlMessage`, `buildTriggerMessage`, `buildAvatarTriggerMessage`, `buildRecordingModeMessage` |
| Maps and navigation | `buildMapRequestMessage`, `buildMapPreviewRequest`, `buildMappingMessage`, `buildNavigationMessage` |
| Onboarding and modes | `buildUserGuideAuthorizationMessage`, `buildDeviceBindRequest`, `buildStoreModeMessage`, `buildPublicTestMessage`, `buildShowcaseMode`, `buildRoamingModeMessage`, `buildRegenerateDanceMessage` |

## Requests and reports

- For an FSM and UWB refresh, use `VbotContext.refreshState()` and collect `VbotContext.state`. Query individual components with `VbotCommands` if you need separate results.
- Use `buildChargingProtectionMessage(requestId)` for a read. For a write, supply the settings to change. The charge limit accepts 80, 85, 90, 95, or 100 percent. Scheduling boundaries are seconds from 0 through 86,399.
- Volume, backlight, and charging-protection writes can report completion through `CommonRespMsg`. Charging protection can also be queried by matching the ID in `ChargingProtectionConfigMsg`.
- UWB status is reported by `UWBStatusRespMsg`. Gesture queries and updates return `UwbCustomRespMsg`. The Demo shows how to check its success field and reported gesture bindings.
- Gamepad, navigation, mapping, and recording queries have feature-specific reports. A matching response can still represent a rejection or a device error.
- For actual movement, establish the intended operating mode and confirm the outcome through robot state and on-site observation.

See [SDK API](SDK-API.md) for connection, reply correlation, and stream decoding examples.
