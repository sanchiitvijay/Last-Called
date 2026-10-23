# Privacy

Last Called processes very sensitive personal data: who you talk to, when, and for how long. It is designed so that **this data never leaves your device**.

## No backend, no network

- No backend, API server, cloud database, Firebase, account, login or sync.
- No analytics, telemetry, advertising or crash-reporting SDKs.
- **The app does not declare the `INTERNET` permission.** Android therefore blocks it from opening network sockets at all. This is enforced by the operating system, not just by the code.
- Guarded by tests and tooling:
  - `PrivacyGuardTest` fails the build's unit tests if the manifest gains a permission, if production code references networking APIs or logging, or if a phone-home dependency is added.
  - `scripts/verify-offline.sh` checks the permissions inside the built APK.

## What is stored, and where

| Data | Stored? | Where |
|---|---|---|
| Calls from the system call log (number, time, duration, direction, source app, voice/video) | Yes | Room database in app-private storage |
| Contacts that you have calls with (name, number(s), number labels, photo *URI*) | Yes | Same database |
| All other contacts | No | — |
| Contact photos | No (loaded on demand from the Contacts Provider) | — |
| WhatsApp call events (only if you enable notification detection) | Caller name or number, time, approximate duration, direction, voice/video | Same database |
| WhatsApp message content, other notifications | Never read | — |
| Your keep-in-touch list (person, name, number) | Yes | Same database |
| Settings | Yes | Same database (not SharedPreferences) |

App-private storage (`/data/data/com.lastcalled.app/`) cannot be read by other apps. The database is not exposed through any content provider, and no activity, service or receiver passes call data to other apps.

## Backups and device transfer

`android:allowBackup="false"`, and the data-extraction rules exclude every domain from both cloud backup and device-to-device transfer. Your call history is never copied to Google Drive or to a new phone by Android's backup system.

## Logging

The app writes **no** log output: no phone numbers, names or call details, and no exception messages, because those can contain provider data. Error states report only an exception class name in the UI. The release build also strips any `android.util.Log` calls through R8 as a safety net.

## Encryption at rest

The app relies on Android's **file-based encryption**, which is mandatory on Android 10+ (this app's minimum). App-private files are encrypted with keys bound to the device and the user's lock screen. Combined with app-private storage and disabled backups, this protects the database without the app managing keys.

An additional SQLCipher layer, with a key protected by the Android Keystore, was considered and **deliberately not added**:

- it would add a native dependency and key-management code,
- it would not protect against an attacker who can already run code as this app,
- the key would have to be available whenever the app runs, which gives little benefit over file-based encryption on an unlocked device.

This follows the guidance to prioritize a secure, maintainable design over complexity.

## Your controls

- **Settings → Reset imported call records:** deletes the calls and contact copies this app stored (your keep-in-touch list is kept).
- **Settings → Clear app data:** deletes everything this app stored, including the keep-in-touch list and settings.
- Uninstalling the app deletes its database.

**None of these delete your phone's call log or WhatsApp's call history.** They only clear this app's local database.

## Permissions

Only `READ_CALL_LOG` and `READ_CONTACTS`, plus optional notification access. See [PERMISSIONS.md](PERMISSIONS.md).
