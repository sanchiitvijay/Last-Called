# Architecture

## Goals that shaped the design

1. **Correctness and transparency over coverage.** Every call shown comes from a real system signal. Nothing is guessed: unknown stays unknown.
2. **Offline by construction.** There is no network code and no `INTERNET` permission, so the guarantee is enforced by the OS, not by discipline.
3. **Business rules testable without Android.** The rules for number matching, classification, dedup and "last called" live in a plain Kotlin module.
4. **Small and understandable.** One app module, one pure-Kotlin domain module, manual dependency injection, no framework the project doesn't need.

## Modules

```
:domain   Pure Kotlin/JVM (no Android dependency)
          model/         CallRecord, PersonKey, CallFilter, enums
          logic/         normalization, contact matching, classification,
                         dedup, last-call rule, time-since, sorting/search
          sync/          SyncCallHistoryUseCase + the interfaces it needs
          notification/  WhatsApp call-notification parser and tracker

:app      Android application
          data/system/      CallLog + Contacts provider readers, permission checks
          data/local/       Room entities, DAOs, database, CallStore implementation
          data/repository/  read-side repositories, settings, SyncManager
          notifications/    NotificationListenerService (optional)
          ui/               Compose screens + ViewModels, theme, navigation
          AppContainer      manual dependency wiring
```

The `:domain` module cannot import `android.*`, so the build enforces the separation of business logic from framework code.

## Layers and data flow

```
Compose UI (stateless screens)
   ↑ StateFlow<UiState>        ↓ user actions
ViewModels (Home, Details, Search, Settings)
   ↑ Flow                      ↓ commands
Repositories / SyncManager (app)  ──uses──▶  Use cases (domain)
   ↑                                           │
Room (DAO queries)   ◀──── CallStore ──────────┤
Android CallLog provider ◀── SystemCallLogSource
Android Contacts provider ◀── ContactsSource
```

- **Write path (import):** `SyncManager` → `SyncCallHistoryUseCase` (domain) → `SystemCallLogSource` / `ContactsSource` (implemented in `data/system`) → `CallStore` (implemented by `RoomCallStore`).
- **Read path (display):** ViewModels → `CallHistoryRepository` → Room DAO queries returning `Flow`. Room re-emits automatically after each import, so the UI never has to be told to reload.

## The import pipeline

`SyncCallHistoryUseCase` is the single place that writes imported data:

```
read system call log rows (RawCallLogEntry)
 → normalize numbers (PhoneNumberNormalizer)
 → resolve contact (ContactMatcher via PersonResolver)
 → source  (CallSourceDetector: PhoneAccount component package)
 → direction (CallDirectionMapper: CallLog.Calls.TYPE)
 → type (CallTypeDetector: FEATURES_VIDEO)
 → CallRecord (CallRecordMapper)
 → deduplicate (CallDeduplicator: (systemCallLogId, date))
 → supersede matching WhatsApp notification records
 → re-resolve existing records against current contacts
 → store atomically (CallStore.applySync → one Room transaction)
```

"Last called" is **derived, never stored**: the DAO computes the latest matching call per person with SQL, and the UI turns the timestamp into "3 hours ago" at render time, with a 30-second ticker so labels stay fresh. `LastCallCalculator` is the reference implementation of the rule, and an instrumented test checks that the SQL agrees with it for several filters.

## Keep-in-touch list (watch list)

The "People not called recently" dashboard only covers people the user explicitly added. Entries live in `tracked_people` and use the same `PersonKey` as call records, so an added person joins their call history automatically. They can be added three ways:

- from contacts (even with no calls yet),
- from people with call history,
- by typing a number. `WatchListUseCase` resolves the number to a contact or existing caller when possible.

Each entry also stores the number. Every sync re-resolves entries the same way it re-resolves call records, so the list follows a person when they are saved as a contact, renamed or deleted. `PeopleSelector.watchList` turns entries into summaries; entries with no calls are reported as "Never called".

## Key design decisions

| Decision | Why |
|---|---|
| **Group calls by `PersonKey`** (`c:<lookupKey>`, `n:<number>`, `w:<name>`, `h:`) | One key type covers contacts, unknown numbers, notification-only names and hidden numbers. Unknown callers are first-class, never dropped. |
| **Contact identity = `LOOKUP_KEY`** | The Contacts Provider's stable reference survives contact re-aggregation better than `_ID`. |
| **Watch list stored by `PersonKey` + number** | The same key as call history means no joins or duplication; the number lets entries follow contact changes. |
| **Re-match every record on every sync** (when contacts are readable) | Renames, deletions, newly added contacts and duplicates resolve themselves without migrations or special cases. It costs one pass over a lightweight projection. |
| **Keep matching against the last stored snapshot when contacts access is revoked** | Revoking contacts shouldn't make every name vanish; new calls still match known numbers. |
| **Never delete imported calls on refresh** | Android trims its call log. The app's value grows with time. |
| **Dedup key `(systemCallLogId, date)` with a unique index** | Re-imports are idempotent, and a reused ID (after a provider reset) is not mistaken for an old call. |
| **Suffix-based number matching (≥ 7 digits) with an E.164 equality guard** | Matches `+91 98765 43210`, `098765 43210` and `9876543210` without a phone-number library, and never matches numbers from two different countries. |
| **Source only from the PhoneAccount component** | It is the only system metadata that says *which app* carried a call. Unknown packages stay `UNKNOWN`. A VoIP call is never assumed to be WhatsApp. |
| **Settings in Room, not SharedPreferences** | Single private store, transactional updates, observable as `Flow`. |
| **Manual DI (`AppContainer`)** | About ten objects. A DI framework would add more concepts than it removes. |
| **Stateless screens + ViewModels** | Screens take plain state and callbacks, so UI tests need no database or permissions. |
| **Paging for per-person history** | A person can have thousands of calls; only visible pages are loaded. The home list is already aggregated per person by SQL. |
| **Permissions centralized** | `PermissionChecker` (data layer) and `rememberRuntimePermission` (UI) are the only places that touch permissions; `AppNavHost` owns the shared state. |
| **No SQLCipher** | See [PRIVACY.md](PRIVACY.md#encryption-at-rest). Android file-based encryption already protects app-private storage; an extra layer was not worth the complexity. |

## Threading

- Provider reads run on `Dispatchers.IO` inside the data sources.
- Imports run in an application-scoped coroutine (`AppContainer.appScope`), serialized by a mutex in `SyncManager`. A manual refresh requested during an import schedules exactly one more run.
- Room queries are main-safe (`suspend` / `Flow`).

## Lifecycle triggers

- `ProcessLifecycleOwner` `ON_START` → `refreshIfStale()` (throttled to 30 s).
- A permission granted → refresh.
- Refresh button / Settings → `refresh()`.
- Device reboot needs no handling: nothing runs in the background, and the next app start refreshes. The notification listener is re-bound by the system after reboot if the user enabled it.

## Optional WhatsApp notification listener

`WhatsAppCallListenerService` is a `NotificationListenerService`. It returns immediately unless the in-app setting is on, the notification comes from `com.whatsapp` / `com.whatsapp.w4b`, and the notification category is `call`. Parsing and the ringing/active/ended state machine are pure Kotlin in `:domain/notification`, so they are unit-tested. See [WHATSAPP_SUPPORT.md](WHATSAPP_SUPPORT.md).

## Responsive UI

At widths ≥ 840 dp the home destination shows a list-detail layout: the people list on the left and the selected person on the right. Narrower screens navigate to a separate details screen.
