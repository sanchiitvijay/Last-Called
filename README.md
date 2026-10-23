# Last Called

**Who haven't I called recently?**

Last Called is a small, fully offline Android app. It reads your phone's call history, matches numbers to your contacts, and shows how long it has been since you last called each person, with the people you haven't called for a while at the top.

- Add the people you want to keep in touch with (from your contacts, people you have calls with, or a typed number). The dashboard shows which of *them* you haven't called in 3, 7, 14 or 30 days. Everyone else stays out of it.
- Tap a person to see when your most recent **outgoing** call was ("4 days ago"), whether it was a phone call or a WhatsApp call, voice or video, its date, time and duration, and the full call history.
- Everything is computed on the device. There is no account, no server, no analytics and no internet permission.

## Why it is useful

Your dialer shows *recent calls*. It does not answer *"when did I last call Mum?"* or *"who have I not called in a month?"*. Last Called is built for that one question. It also keeps its own copy of your history, so calls stay visible after Android trims old entries from the system call log.

## Supported platforms

| | |
|---|---|
| Platform | Android phones and tablets (Kotlin, Jetpack Compose, Material 3) |
| Minimum Android | Android 10 (API 29) |
| Target / compile SDK | Android 17 (API 37) |
| Network | None. The app has no `INTERNET` permission |

See [PERMISSIONS.md](PERMISSIONS.md) for how behaviour differs between Android versions.

## Build and install

Requirements: JDK 17+ (21 tested), the Android SDK with platform 37. Android Studio is optional.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installDebug
```

Full instructions, including release builds, the emulator seed script, and offline verification, are in [BUILD.md](BUILD.md).

## Permissions

| Permission | Required? | Used for |
|---|---|---|
| `READ_CALL_LOG` | Yes, for importing | Reading the system call history |
| `READ_CONTACTS` | Recommended | Turning numbers into names and photos |
| Notification access | Optional, off by default | Best-effort detection of *future* WhatsApp calls the call log doesn't include |

The app explains each permission before asking, works with whatever you grant, and points you to system settings when a permission is blocked. Details: [PERMISSIONS.md](PERMISSIONS.md).

## How data is stored

- Calls are copied into a Room (SQLite) database in the app's private storage. It is excluded from cloud backup and device-to-device transfer.
- Only contacts that some call belongs to, or that you added to your keep-in-touch list, are copied (name, photo URI, numbers), and they are refreshed on every sync.
- Your keep-in-touch list is stored in the same database.
- "Last called" and "x days ago" are never stored. They are computed from the stored call timestamps every time they are shown.
- Details: [DATA_MODEL.md](DATA_MODEL.md) and [PRIVACY.md](PRIVACY.md).

## How refresh works

A refresh runs on app start, whenever the app comes to the foreground (at most every 30 seconds), when a permission is granted, and when you tap **Refresh**:

1. Read the system call log (on Android 16.1+ also asking for VoIP calls; see [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md)).
2. Read contacts and normalize every phone number.
3. Classify each call: source (Phone / WhatsApp / Unknown), direction, voice or video.
4. Skip rows already imported. Identity is the system call log ID plus the call date.
5. Insert new calls and re-match existing calls to contacts, which picks up renamed, added and deleted contacts.
6. Never delete history, even if the system no longer has it.

Nothing is erased unless you choose **Settings → Reset imported call records** or **Clear app data**. Neither touches your phone's or WhatsApp's own call history.

## WhatsApp, honestly

Android gives other apps no access to WhatsApp's private call history, and this app never tries to get it. WhatsApp calls are shown only when Android itself lists them in the call log and attributes them to WhatsApp, which depends on your Android version, dialer and WhatsApp version. An optional notification listener can record some *future* WhatsApp calls. Read [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md) before relying on WhatsApp data.

## Known limitations

The short version:

- WhatsApp coverage depends on the device.
- Google Play restricts `READ_CALL_LOG`, so this is meant for personal or side-loaded use.
- Android trims old call log entries before the first import.
- Notification-based detection is approximate.

Full list: [LIMITATIONS.md](LIMITATIONS.md).

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, layers, data flow, design decisions |
| [DATA_MODEL.md](DATA_MODEL.md) | Every Room entity, column and relationship |
| [PERMISSIONS.md](PERMISSIONS.md) | Each permission, why, and what happens when it is denied; Android version differences |
| [PRIVACY.md](PRIVACY.md) | What is stored, where, and what never leaves the device |
| [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md) | Exactly how WhatsApp call data is (and isn't) obtained |
| [LIMITATIONS.md](LIMITATIONS.md) | Platform and policy limitations |
| [TESTING.md](TESTING.md) | Test suites and how to run them |
| [BUILD.md](BUILD.md) | Build, install, verify |
