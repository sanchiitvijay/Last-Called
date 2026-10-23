# Testing

Three suites. All of them run locally and offline.

| Suite | Location | Runs on | Command |
|---|---|---|---|
| Domain unit tests | `domain/src/test` | JVM | `./gradlew :domain:test` |
| App unit tests (privacy guards) | `app/src/test` | JVM | `./gradlew :app:testDebugUnitTest` |
| Integration + UI tests | `app/src/androidTest` | Emulator / device | `./gradlew :app:connectedDebugAndroidTest` |

Run everything:

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Last verified run: 90 domain tests, 4 app unit tests and 43 instrumented tests, all passing on an Android 17 (API 37) emulator.

> **Target one device.** Gradle's `connected*` and `install*` tasks run on *every* attached device, including a phone plugged in over USB. To run on the emulator only:
>
> ```bash
> ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
> ```
>
> The one test that writes to the system call log (`SystemProvidersTest.readsAndMapsCallLogRows`) is skipped automatically on real devices.

> `connectedDebugAndroidTest` uninstalls the app when it finishes, which deletes its local data on that device.

## Unit tests (`:domain`, plain JVM)

| Area | Test class |
|---|---|
| Phone number normalization | `PhoneNumberNormalizerTest` |
| Contact matching (formats, multiple numbers, duplicates, names) | `ContactMatcherTest` |
| Call source detection (Phone / WhatsApp / WhatsApp Business / other VoIP / missing) | `CallSourceDetectionTest` |
| Direction mapping (all `CallLog.Calls.TYPE` values) | `CallDirectionMappingTest` |
| Voice / video detection | `CallTypeDetectionTest` |
| Last outgoing call calculation | `LastOutgoingCallTest` |
| Time since last call, durations, DST / future clocks | `TimeSinceTest` |
| Deduplication (re-import, in-batch, reused IDs, notification vs call log) | `DeduplicationTest` |
| Filtering (combinations such as WhatsApp + Outgoing) | `FilterTest` |
| Sorting, search, "not called in N days", watch-list dashboard | `PeopleSelectorTest` |
| Keep-in-touch list: typed numbers, contacts without calls, follows contact deletion / re-adding | `WatchListTest` |
| Whole sync pipeline: rename / delete / add contacts, revoked permissions, empty log, hidden numbers, 5,000-call history, notification supersede | `SyncCallHistoryUseCaseTest` |
| Recording notification events | `RecordNotificationCallUseCaseTest` |
| WhatsApp notification parsing and call state machine | `WhatsAppNotificationTest` |

Fixtures live in `Fixtures.kt`:

- phone outgoing and incoming calls,
- missed calls,
- WhatsApp voice and video calls exposed through CallLog,
- carrier video calls,
- unknown callers,
- withheld numbers,
- duplicate records.

`InMemoryCallStore` is a test double that mirrors the Room store's semantics.

## App unit tests

`PrivacyGuardTest` checks that:

- the manifest requests exactly `READ_CALL_LOG` and `READ_CONTACTS`, and no `INTERNET`,
- production sources contain no networking APIs or logging calls,
- no analytics / crash-reporting / network dependencies are declared.

## Integration tests (instrumented)

| Area | Test class |
|---|---|
| Room persistence, unique-index dedup, paging, settings, reset; SQL "last called" agrees with the domain rule for several filters | `CallDaoTest` |
| CallLog import + contact matching end-to-end through Room (including other-VoIP ≠ WhatsApp, rename / delete) | `SyncIntegrationTest` |
| Real `CallLog` provider: inserts a row through the shell (emulator only) and checks it is read and mapped correctly; domain constants equal framework constants | `SystemProvidersTest` |
| Database migrations: v1 → v2 (`tracked_people`), and all migrations with existing data kept | `MigrationTest` |

## UI tests (Compose)

| Area | Test class |
|---|---|
| Permission flow: explain → request → explain → request → WhatsApp notice; denied and permanently-denied states; data-access screen | `PermissionFlowTest` |
| Home screen: dashboard, relative times, opening a person, thresholds, sorting, filters, refresh / search / settings actions | `HomeScreenTest` |
| Empty states: no permission, permanently denied, empty call log, WhatsApp unavailable, no contacts, no outgoing calls; revoked-permission banners | `HomeScreenTest` |
| Contact search | `SearchScreenTest` |
| Keep-in-touch picker: list, suggestions, add / remove, typed number, empty list; dashboard empty / edit states; details star | `WatchListScreenTest`, `HomeScreenTest`, `PersonDetailsScreenTest` |
| Person details: last outgoing call card, history, no outgoing calls, filter chips | `PersonDetailsScreenTest` |

Screens are stateless (state + callbacks), so UI tests don't need a database or real permissions.

## Manual testing on an emulator

```bash
RESET=1 scripts/seed-emulator.sh
```

The script refuses to run on non-emulators. It adds sample contacts and call log rows covering:

- phone calls,
- WhatsApp-attributed voice and video calls,
- another VoIP app,
- missed, rejected and incoming-only calls,
- unknown numbers,
- a 900-day-old call,
- the same number written in different formats.

Then install the app and walk through onboarding.

To check offline behaviour, enable airplane mode and disable Wi-Fi and mobile data. The app works identically, because it never uses the network.

```bash
adb shell cmd connectivity airplane-mode enable
```

## Not covered by automated tests

- Real WhatsApp notifications and real unified-call-history rows on a physical device. These need WhatsApp installed and a device that supports the feature.
- The Android 13+ restricted-settings flow for notification access. It is driven by system UI.
