# VBot Android SDK Demo

This English Android app demonstrates how to connect to an authorized VBot with Aorta or BLE, inspect robot and UWB state, send commands, and view the corresponding responses. Gradle retrieves the binary SDK from this repository's private GitHub release.

- Maven dependency `com.vita.vbot:vbot-sdk:0.2.3`
- [SDK integration and API reference](docs/SDK-API.md)
- [Command encoder reference](docs/COMMANDS.md)
- [0.2.3 AAR and POM](https://github.com/VitaDynamics/vbot-android-sdk-demo/releases/tag/v0.2.3)

## Build and install

Use JDK 21, Android SDK 36, and GitHub CLI. Run the app on an `arm64-v8a` Android device with API 24 or newer.

Sign in to GitHub CLI with an account that has read access to this private repository. In CI, set `GH_TOKEN` to a credential with repository Contents read access. If Gradle cannot find the executable, pass `-PvbotGitHubCli=/absolute/path/to/gh`. [GitHub CLI authentication](https://cli.github.com/manual/gh_auth_login)

```sh
gh auth login --hostname github.com
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

On the first build, Gradle downloads the prebuilt AAR and its POM, verifies their SHA-256 checksums, and stores them in a local Maven cache. Later builds reuse the verified files. GitHub Actions runs the same download and build, then compares the SDK native library in the APK with the library in the AAR. [Bundled third-party notices](docs/third-party/)
