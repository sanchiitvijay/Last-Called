# Building and installing

## Requirements

| Tool | Version |
|---|---|
| JDK | 17 or newer (built with 21) |
| Android SDK | Platform 37 (`compileSdk 37`), build-tools 36+ |
| Gradle | Wrapper included (9.8.1). No global install needed |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 (Compose compiler plugin), KSP 2.3.12 for Room |
| Android Studio | Optional. Any version supporting AGP 9.4 |

Point Gradle at your SDK with `ANDROID_HOME`, or a `local.properties` file (not committed) containing:

```properties
sdk.dir=/path/to/Android/sdk
```

The first build downloads Gradle and dependencies, which needs network access *on the build machine*. The app itself never uses the network.

## Build

Debug APK, output at `app/build/outputs/apk/debug/app-debug.apk`:

```bash
./gradlew :app:assembleDebug
```

Release APK (minified with R8; signed with the debug key so it installs locally), output at `app/build/outputs/apk/release/app-release.apk`:

```bash
./gradlew :app:assembleRelease
```

To distribute, add your own `signingConfig` in `app/build.gradle.kts`.

## Install on a device or emulator

Enable *Developer options → USB debugging* on the phone, connect it, then:

```bash
./gradlew :app:installDebug
```

`installDebug` installs on every connected device. With an emulator and a phone attached, choose one with `ANDROID_SERIAL=<serial>` (see `adb devices`).

Or install an APK you built:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Installing with `adb` / Android Studio avoids the side-loading restrictions described in [LIMITATIONS.md](LIMITATIONS.md). If you copy the APK to the phone and install it from a file manager, Android 13+ may need *Allow restricted settings* before the optional notification access can be granted.

## Try it on an emulator with sample data

Start an emulator (API 29+; tested on API 37), then:

```bash
RESET=1 scripts/seed-emulator.sh
```

```bash
./gradlew :app:installDebug
```

The seed script only runs against emulators.

## Verify the offline guarantees

```bash
./gradlew :app:assembleRelease
```

```bash
scripts/verify-offline.sh
```

The script checks that the APK requests only `READ_CALL_LOG` and `READ_CONTACTS` (plus AndroidX's internal signature permission), no network permission, and that production sources contain no networking or logging code. The same rules run as a unit test (`PrivacyGuardTest`).

## Run tests

See [TESTING.md](TESTING.md).

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Project layout

```
app/        Android app (UI, Room, system data sources, notification listener)
  schemas/  Exported Room schemas (used by migration tests)
domain/     Pure Kotlin business logic + unit tests
scripts/    seed-emulator.sh, verify-offline.sh
gradle/     Version catalog (libs.versions.toml) and wrapper
```
