# VBot Android SDK Demo

This English Android app demonstrates how to connect to an authorized VBot with Aorta or BLE, inspect robot and UWB state, send commands, and view the corresponding responses. The app consumes the binary SDK from GitHub Packages.

- Maven dependency `com.vita.vbot:vbot-sdk:0.2.3`
- [SDK integration and API reference](docs/SDK-API.md)
- [Command encoder reference](docs/COMMANDS.md)
- [GitHub Packages](https://github.com/VitaDynamics/vbot-android-sdk-demo/packages)
- [0.2.3 AAR release asset](https://github.com/VitaDynamics/vbot-android-sdk-demo/releases/tag/v0.2.3)

## Build and install

Use JDK 21 and Android SDK 36. Run the app on an `arm64-v8a` Android device with API 24 or newer.

Use a GitHub account with access to this private repository and a classic personal access token with `read:packages`. If your organization uses SAML SSO, authorize the token for VitaDynamics. Set `vbotPackagesUser` and `vbotPackagesToken` in your user-level `~/.gradle/gradle.properties`, or set the environment variables `VBOT_PACKAGES_USER` and `VBOT_PACKAGES_TOKEN`. Keep the token in your local credential configuration. [GitHub Packages authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry).

```sh
./gradlew :demo:assembleDebug
adb install -r demo/build/outputs/apk/debug/demo-debug.apk
```

## Connect and verify

1. Open **Connect a robot**. For Aorta, import the JSON profile supplied by the device owner, then tap **Connect via Aorta**. The demo keeps the profile in memory for the current process.
2. For BLE, enter the authorized robot serial number, grant the requested permissions, and tap **Scan for BLE**. Select the robot's advertisement or enter its Bluetooth address and tap **Connect via BLE**.
3. On **Device**, tap **Refresh status**. Use **UWB** for tag status and gestures, **Settings** for robot settings, and **Checks** for responses and received packet counts.
4. On **Checks**, **Sent** indicates transport acceptance. **Verified** indicates a matching report or a successful protocol acknowledgment. Use robot state and on-site observation to confirm movement.

The Aorta profile has the following shape. Replace the example values with settings supplied for your test environment. The `zenohConfig` field can also be a JSON5 string.

```json
{
  "serialNumber": "APPROVED_ROBOT_SERIAL",
  "operatorId": "assigned-lab-operator",
  "zenohConfig": {
    "mode": "client",
    "connect": {"endpoints": ["tcp/router.example.invalid:7447"]}
  }
}
```

## Distribution

The release asset is the prebuilt SDK AAR. GitHub Actions checks it against `distribution/sha256.txt`, publishes it with the dependency metadata in `distribution/pom.xml`, retrieves the published package, and builds the Demo using the registry dependency. [Bundled third-party notices](docs/third-party/).
