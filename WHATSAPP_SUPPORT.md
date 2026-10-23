# WhatsApp support

**Summary:** Last Called shows a WhatsApp call **only** when Android itself provides it. Android exposes a call either in the system call log, attributed to WhatsApp, or, if you opt in, as a live WhatsApp call notification. The app never reads, decrypts or modifies WhatsApp's private data, and it never invents a WhatsApp call.

## What WhatsApp and Android actually offer

- WhatsApp keeps its call history inside its own private, encrypted chat database. WhatsApp's documentation says call history cannot be exported. Android does not let other apps read another app's private storage.
- There is **no public WhatsApp API** for reading call history.
- What Android *does* offer is the system call log (`android.provider.CallLog`). Calling apps that integrate with the Android Telecom framework can have their calls written there.

## Source 1: the system call log (primary, automatic)

### How a WhatsApp call gets into the call log

A VoIP app's calls appear in the system call log only when the app registers them with Android's Telecom framework *and* the platform logs them:

- **Before Android 16.1:** self-managed calling apps were *not* logged by default. A `PhoneAccount` extra (`EXTRA_LOG_SELF_MANAGED_CALLS`, now deprecated) let an app opt in. Whether WhatsApp did so varied by WhatsApp version and device, so on many phones WhatsApp calls never appear in the system call log.
- **Android 16.1 (API 36.1) and later:** "unified call history". Apps using the Jetpack Telecom library 1.1+ and registering `TelecomManager.ACTION_CALL_BACK` have their calls logged automatically. The provider returns these VoIP rows only when the query sets `include_voip_calls=true` (`CallLog.Calls.INCLUDE_VOIP_CALLS_PARAM_KEY` / `CONTENT_URI_WITH_VOIP_CALLS`, public constants in API 37).
- **Android 17 (API 37):** users can turn unified call history on or off per app in system settings. When a user turns it off for an app, Android **permanently purges** that app's existing call log entries.
- Google is rolling this out gradually and lets dialers use allowlists. Public reporting in 2026 lists WhatsApp and Google Meet among the participating apps, but availability differs by device, region, dialer and app version.

### What this app does

1. On Android 16 and later it queries `content://call_log/calls?include_voip_calls=true`. If the provider rejects that query, it falls back to the plain URI. Settings shows whether VoIP rows were requested in the last refresh.
2. For each row it reads `PHONE_ACCOUNT_COMPONENT_NAME` (the `ComponentName` of the PhoneAccount the call was logged against). The call is labelled **WhatsApp** only if that component's package is `com.whatsapp` or `com.whatsapp.w4b` (WhatsApp Business).
3. **Video** is reported only when the row's `FEATURES` contains `FEATURES_VIDEO`.
4. Calls from any other VoIP app (Telegram, Meet, Signal, …) are shown with source **Unknown app**. They are never labelled WhatsApp.

### What can and cannot be known

| Question | Answer |
|---|---|
| Is a WhatsApp call in my call log imported? | Yes, if Android returns it to this app with WhatsApp's PhoneAccount |
| Are WhatsApp calls that Android didn't log imported? | No. They don't exist anywhere this app can legitimately read |
| Can past WhatsApp calls be recovered? | Only those still in the system call log |
| Is a non-dialer app guaranteed to receive VoIP rows on 16.1+? | No. It depends on the platform build and dialer integration. The app asks, and uses what it gets |
| Voice vs video for WhatsApp rows | From `FEATURES_VIDEO`, which the platform sets from the call's video state |

If you filter by WhatsApp and no WhatsApp rows were ever imported, the app says so: *"WhatsApp calls are not available in your device call history."*

## Source 2: notification listener (optional, opt-in, best effort)

For devices where WhatsApp calls do not reach the call log, the app can watch **future** WhatsApp call notifications. This is off by default and needs two explicit steps:

1. Settings → *Detect WhatsApp calls from notifications*.
2. Granting notification access to Last Called in Android's system settings.

### What it inspects and stores

- Every posted notification is first checked for its package (`com.whatsapp`, `com.whatsapp.w4b`) and category (`Notification.CATEGORY_CALL`). **Anything else is ignored without reading its content.** Messages are never read.
- From a call notification it reads only:
  - the title (caller's name, or number),
  - `EXTRA_CALL_TYPE` from `CallStyle` (incoming / ongoing),
  - whether a full-screen intent is attached,
  - the short status text, used only to look for the words "voice"/"audio" or "video".
- When the call notification goes away, one `CallRecord` is stored: source `WHATSAPP`, origin `WHATSAPP_NOTIFICATION`, start time, approximate duration, direction, type, plus either the matched contact or the caller name / number. Nothing else from the notification is kept.

### How calls are interpreted

| Notifications seen | Recorded as |
|---|---|
| Ongoing call with no ringing before it | `OUTGOING`. Duration counts from when the notification appeared, so it includes dialling time |
| Ringing, then ongoing (same caller) | `INCOMING`. Duration counts from when the call became ongoing |
| Ringing that disappears without becoming ongoing | `MISSED`. A declined call looks the same from outside |
| Text says "video" (English) | `VIDEO`; "voice"/"audio" → `VOICE`; other languages → `UNKNOWN` |

Records from this source show "≈" in history, and the details screen notes that they are approximate.

If the same call later appears in the system call log, the system row replaces the notification record (same contact, same source, start within 2 minutes, compatible direction). The reverse also holds: a notification event is not stored if the call log already has the call.

### Why it is only best effort

- WhatsApp's notification layout, wording and use of `CallStyle` are not a documented interface and can change in any update.
- Notifications cannot reconstruct **historical** calls.
- If Android kills the app's process mid-call, that call is not recorded (call state is held in memory only).
- Caller identification relies on the name WhatsApp shows. It matches a contact only by an exact, unambiguous name (or by number when a number is shown). Two contacts with the same name → not matched.
- Group calls appear under the group name.
- On Android 13+ side-loaded apps may need *Allow restricted settings* before notification access can be granted. Some regions' Play Protect protections may block installing side-loaded apps that declare a notification listener (see [LIMITATIONS.md](LIMITATIONS.md)).

## What this app will never do

- Read, copy, decrypt or modify WhatsApp's database, backups, media or private files.
- Use accessibility services, screen scraping, root, or reverse engineering of WhatsApp.
- Read WhatsApp message notifications.
- Infer that a call was WhatsApp just because it was VoIP.
- Claim that all WhatsApp calls can be retrieved.

## How this was verified

- The provider query with `include_voip_calls=true` was exercised on an Android 17 (API 37) emulator, where the platform accepted it.
- WhatsApp-attributed rows were **simulated** on the emulator: rows inserted with WhatsApp's PhoneAccount component. They were imported, labelled WhatsApp, and voice/video was detected from `FEATURES`.
- The notification parser and call-state tracker are covered by unit tests built from the notification shapes described above.
- **Not verified:** a real WhatsApp installation on a physical device with unified call history. Real-world coverage must be checked on the target phone.
