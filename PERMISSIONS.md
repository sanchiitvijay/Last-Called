# Permissions

The app requests **two runtime permissions**, plus one optional special access that the user grants in system settings. It requests nothing else: no internet, SMS, phone state, location, microphone, camera or storage.

| Permission | Kind | Required? |
|---|---|---|
| `android.permission.READ_CALL_LOG` | Runtime (dangerous) | Required to import calls |
| `android.permission.READ_CONTACTS` | Runtime (dangerous) | Optional, recommended |
| Notification access (`NotificationListenerService`) | Special access in system settings | Optional, off by default |

The merged APK also contains `com.lastcalled.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX Core adds this signature-level permission automatically to protect runtime-registered receivers. It grants no access to anything.

## READ_CALL_LOG

- **Why:** to read `CallLog.Calls`: number, date, duration, type (direction), features (video), and the PhoneAccount component (which app carried the call).
- **When asked:** onboarding step 2, after an explanation screen. It can also be requested from the home screen and from *Settings → Manage permissions*.
- **If denied:** nothing can be imported. Home shows *"Grant call log permission to import calls."* with an **Allow** button. Any history imported earlier stays visible, with a banner saying new calls can't be imported.
- **If permanently denied** (denied with "don't ask again", or denied twice): the system dialog can no longer be shown, so the app shows **Open settings** instead, which goes to the app's system settings page.
- **If revoked later** in system settings: the app re-checks on every resume, shows the banner, and keeps existing data. Refresh is disabled until access is granted again.

## READ_CONTACTS

- **Why:** to turn phone numbers into contact names, photos and number labels. The app reads only the phone-number data rows (`CommonDataKinds.Phone`) and copies only contacts that some call belongs to.
- **If denied:** calls are still imported and shown by number, marked *Not in contacts*. A banner offers to grant access.
- **If revoked later:** names already known are kept. New calls are matched against the last stored copy of those contacts. Renames and new contacts are not picked up until access is granted again.

## Notification access (optional)

- **Why:** best-effort detection of *future* WhatsApp calls that the call log doesn't include. See [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md).
- **How it is granted:** *Settings → Detect WhatsApp calls from notifications* opens Android's notification-access screen for this app. Both the in-app switch and the system access must be on. If either is off, the service ignores everything.
- **Android 13+ side-loading:** Android may grey this out as a *restricted setting* for apps not installed from an app store. Open *App info → ⋮ → Allow restricted settings*, then try again.
- **If denied / revoked:** the feature simply does nothing; Settings shows that access is missing.

## How the permission flow is implemented

All runtime-permission logic lives in `ui/common/Permissions.kt` (`rememberRuntimePermission`) and `data/system/PermissionChecker.kt`. `AppNavHost` owns one shared state per permission.

Android does not report "permanently denied" directly, so the app infers it. The status is `PERMANENTLY_DENIED` when all of these hold:

- the permission is not granted,
- the app has requested it before (persisted in `app_settings`),
- `shouldShowRequestPermissionRationale` returns false.

The status is recomputed on every `ON_RESUME`, so grants and revocations made in system settings show up immediately.

## Android version notes

| Android | Behaviour relevant to this app |
|---|---|
| 10–12 (API 29–32) | Standard runtime permissions. Calls from VoIP apps are in the call log only if the app opted in via the (now deprecated) self-managed logging extra. |
| 13+ (API 33) | *Restricted settings*: side-loaded apps may need explicit user unlock before notification access can be granted. |
| 16 (API 36) | The app starts requesting VoIP rows (`include_voip_calls=true`); older providers ignore the parameter. |
| 16.1 (API 36.1) | Unified call history: VoIP apps integrating with Jetpack Telecom 1.1+ can have their calls logged by the system and returned with `include_voip_calls`. |
| 17 (API 37) | `CONTENT_URI_WITH_VOIP_CALLS` and `INCLUDE_VOIP_CALLS_PARAM_KEY` become public constants. Users can toggle unified call history per app; turning it off purges that app's existing entries. Target SDK of this app. |

## Google Play policy

`READ_CALL_LOG` is in Google Play's restricted *SMS and Call Log* permission group. Play generally allows it only for default phone/assistant apps or approved exceptions, through a Permissions Declaration. This project is intended for **personal / side-loaded use**. Publishing on Play would require a successful declaration, which is not guaranteed for this use case.
