# Limitations

These are the limits of what Android and WhatsApp allow, and of this implementation.

## WhatsApp

- **Only calls Android exposes.** WhatsApp's own call history is private to WhatsApp and cannot be exported or read by other apps. If your device's call log doesn't contain a WhatsApp call, this app can't show it. Details: [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md).
- **Unified call history is new and uneven.** VoIP calls in the system call log need Android 16.1+, a participating dialer and app version, and the user's per-app setting (Android 17). Rollout is gradual, and whether a non-dialer app receives those rows can vary by device.
- **Turning off unified call history for WhatsApp in Android 17 settings purges those entries from the system log.** Calls this app already imported stay, but no new ones arrive.
- **Notification detection is approximate and fragile.**
  - Future calls only.
  - Duration includes dialling time for outgoing calls.
  - Missed and declined calls look the same.
  - Voice/video wording is English-only.
  - It can break whenever WhatsApp changes its notifications.
  - It is lost if the process dies mid-call.
- **Real WhatsApp behaviour was not verified on a physical device** as part of this build. Emulator tests use simulated WhatsApp-attributed call log rows.

## System call log

- **Android trims the call log.** AOSP keeps a limited number of recent entries per account (commonly 500), and OEMs may differ. Calls trimmed before the app's first import can't be recovered. After that, the app keeps its own copy.
- **Other VoIP apps** are shown with source *Unknown app*. Only the platform's telephony stack (`com.android.phone`) is labelled *Phone*, and only WhatsApp's packages are labelled *WhatsApp*.
- **Missing metadata:** rows without a PhoneAccount component (some OEMs, or very old entries) have source *Unknown*. Rows without `FEATURES` have type *Unknown*.
- **Blocked calls** are shown as *Rejected*; voicemail rows are *Unknown* direction.
- **Dual SIM:** both SIMs are labelled *Phone*; the app doesn't distinguish SIMs.

## Contacts and numbers

- **Number matching is heuristic but conservative.** Two numbers match if:
  - both international forms are equal, or
  - at least 7 trailing significant digits agree and they are not known to be in different countries.

  This can merge two different short local numbers whose last 7+ digits coincide. It is rare but possible.
- **Duplicate contacts** with the same number: the call is attributed to one of them, chosen deterministically by lookup key.
- **Same name, different contacts:** notification-derived WhatsApp calls (which carry only a name) are left unmatched rather than guessed.
- **Without contacts access** the app shows numbers. Previously known names remain but are not refreshed.

## Platform and distribution

- **Google Play policy** restricts `READ_CALL_LOG`, so the app is built for personal / side-loaded use.
- **Android 13+ restricted settings:** a side-loaded app must be allowed "restricted settings" before notification access can be granted.
- **Play Protect fraud protection pilots** (for example in India, Singapore, Thailand and Brazil) can block installing apps from browsers, messaging apps or file managers when they declare a notification listener. Installing with `adb` / Android Studio is not affected. If blocked, remove the `<service>` from the manifest to build without the optional feature.
- **No background sync.** The app refreshes when opened or brought to the foreground. It doesn't run periodic background jobs, so the home screen is only as fresh as the last time you opened it.
- **Minimum Android 10.** Older devices are not supported.

## Time

- "x days ago" uses elapsed time (24-hour periods), not calendar days. A call 23 hours ago late yesterday evening shows "23 hours ago", not "yesterday".
- If the device clock is wrong, or changes, labels follow the device clock. Timestamps in the future are shown as "just now".
