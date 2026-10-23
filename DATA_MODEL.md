# Data model

Database: Room, file `last_called.db` in the app's private database directory. Current schema version: **2**. The exported schema JSON is in [`app/schemas/`](app/schemas/).

```
contacts 1 ──── * contact_phone_numbers        (FK lookupKey, ON DELETE CASCADE)
contacts 1 ┄┄┄┄ * call_records                 (logical: call_records.contactKey = contacts.lookupKey)
tracked_people  * ┄┄┄┄ 1 person (logical: tracked_people.personKey = call_records.personKey)
app_settings    single row (id = 0)
```

`call_records.contactKey` is a *logical* reference, not a foreign key. The sync pipeline maintains it: every refresh re-resolves each record and removes contacts no record references any more. Without a foreign key, a deleted contact can never cascade-delete call history.

## `call_records`

One row per call the app knows about.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | App-local ID |
| `personKey` | TEXT NOT NULL | Grouping key; see *PersonKey* below |
| `contactKey` | TEXT NULL | `contacts.lookupKey` of the matched contact, or NULL for unknown callers |
| `phoneNumber` | TEXT NULL | Number as the system reported it; NULL for withheld numbers and name-only notification records |
| `normalizedNumber` | TEXT NULL | E.164 when known (`+919876543210`), otherwise significant digits |
| `displayNameHint` | TEXT NULL | Caller name shown in a WhatsApp notification; set only for notification-derived records without a number |
| `timestamp` | INTEGER NOT NULL | Call start, epoch milliseconds (UTC) |
| `durationSeconds` | INTEGER NOT NULL | 0 for missed / rejected / unanswered calls |
| `direction` | TEXT NOT NULL | `OUTGOING`, `INCOMING`, `MISSED`, `REJECTED`, `UNKNOWN` |
| `source` | TEXT NOT NULL | `PHONE`, `WHATSAPP`, `UNKNOWN` |
| `callType` | TEXT NOT NULL | `VOICE`, `VIDEO`, `UNKNOWN` |
| `origin` | TEXT NOT NULL | `SYSTEM_CALL_LOG` or `WHATSAPP_NOTIFICATION` |
| `systemCallLogId` | INTEGER NULL | `CallLog.Calls._ID` for imported rows; NULL for notification records |
| `sourcePackage` | TEXT NULL | Package that owns the PhoneAccount (e.g. `com.android.phone`, `com.whatsapp`) |
| `createdAt` / `updatedAt` | INTEGER NOT NULL | Epoch ms; `updatedAt` changes when the record is re-matched |

Indexes:

- `(personKey, timestamp)`: per-person aggregation and history paging.
- `(timestamp)`: time-window lookups during dedup.
- `(contactKey)`
- **unique** `(systemCallLogId, timestamp)`: prevents importing a system row twice. SQLite treats NULLs as distinct, so notification records never collide.

Enums are stored **by name**, so reordering enum constants cannot corrupt data. Unknown names read back as `UNKNOWN`.

### Mapping from `CallLog.Calls`

| CallLog value | Stored as |
|---|---|
| `TYPE` 2 OUTGOING | `OUTGOING` |
| `TYPE` 1 INCOMING, 7 ANSWERED_EXTERNALLY | `INCOMING` |
| `TYPE` 3 MISSED | `MISSED` |
| `TYPE` 5 REJECTED, 6 BLOCKED | `REJECTED` |
| `TYPE` 4 VOICEMAIL, other | `UNKNOWN` |
| `FEATURES & FEATURES_VIDEO` | `VIDEO` |
| no video bit, source PHONE / WHATSAPP | `VOICE` |
| no video bit, source UNKNOWN, or `FEATURES` NULL | `UNKNOWN` |
| `PHONE_ACCOUNT_COMPONENT_NAME` package `com.android.phone` | `PHONE` |
| … `com.whatsapp` or `com.whatsapp.w4b` | `WHATSAPP` |
| anything else / NULL | `UNKNOWN` |
| `NUMBER_PRESENTATION` ≠ ALLOWED | number dropped, `personKey = h:` |

### PersonKey

| Prefix | Meaning | Example |
|---|---|---|
| `c:` | Matched contact (lookup key) | `c:3789r1-2F4D…` |
| `n:` | Not in contacts, grouped by canonical number | `n:+14155550123` |
| `w:` | WhatsApp notification with a name but no number, not matching a contact | `w:Family group` |
| `h:` | Withheld / private / unavailable number | `h:` |

## `contacts`

A copy of the device contacts that **at least one stored call belongs to**. The app does not copy the rest of the address book.

| Column | Type | Notes |
|---|---|---|
| `lookupKey` | TEXT PK | `ContactsContract.Contacts.LOOKUP_KEY` |
| `deviceContactId` | INTEGER | `Contacts._ID` at the last sync |
| `displayName` | TEXT | Current primary display name |
| `photoThumbnailUri` | TEXT NULL | `content://` URI. The photo itself is not copied; it is loaded from the Contacts Provider when shown |
| `updatedAt` | INTEGER | Last sync that refreshed this row |

Replaced on every sync while contacts access is granted, so a renamed contact shows its new name. A deleted contact disappears, and its calls become unknown-number calls.

## `contact_phone_numbers`

| Column | Type | Notes |
|---|---|---|
| `lookupKey` | TEXT, PK part, FK → `contacts.lookupKey` (CASCADE) | |
| `number` | TEXT, PK part | As saved in the contact |
| `e164` | TEXT NULL | `Phone.NORMALIZED_NUMBER`, or computed offline by `PhoneNumberUtils` |
| `label` | TEXT NULL | e.g. "Mobile" |

Used to show a contact's numbers and to keep matching new calls when contacts access is later revoked.

## `tracked_people` (since v2)

The user's keep-in-touch list. Only these people appear in "People not called recently".

| Column | Type | Notes |
|---|---|---|
| `personKey` | TEXT PK | Same format as `call_records.personKey` (`c:`, `n:`, `w:`). Hidden numbers can't be tracked |
| `displayName` | TEXT NULL | Name when added, updated on sync from the contact; used when there are no calls |
| `phoneNumber` | TEXT NULL | Number as entered or saved, used to re-resolve the entry when contacts change |
| `normalizedNumber` | TEXT NULL | E.164 or significant digits |
| `addedAt` | INTEGER | Epoch ms |

"Reset imported call records" keeps this list; "Clear app data" deletes it.

## `app_settings`

Single row with `id = 0`. A missing row means defaults.

| Column | Default | Notes |
|---|---|---|
| `onboardingCompleted` | false | |
| `defaultThresholdDays` | 7 | 3 / 7 / 14 / 30 |
| `notificationDetectionEnabled` | false | Opt-in for the WhatsApp notification listener |
| `callLogPermissionRequested` | false | Distinguishes "never asked" from "permanently denied" |
| `contactsPermissionRequested` | false | Same, for contacts |
| `lastSyncAt` | NULL | Epoch ms of the last completed import |
| `lastSyncRequestedVoip` | false | Whether the last import asked the platform for VoIP rows |
| `sortOrder` | `RECENTLY_CALLED` | `RECENTLY_CALLED`, `LONGEST_SINCE_CALLED`, `ALPHABETICAL` |

## Derived values (never stored)

- **Last called** = `MAX(timestamp)` over the person's records with `direction = 'OUTGOING'` (plus any source / type filter).
- **Time since last call** = now − that timestamp, rendered as "just now / n minutes / hours / days / years ago". Elapsed time comes from epoch milliseconds, so timezone and DST changes don't affect it. Dates are formatted in the current timezone at display time.

## Migrations

`AppDatabase.MIGRATIONS` contains `MIGRATION_1_2` (creates `tracked_people`). Destructive fallback is **disabled on purpose**: imported history can be older than anything the system still holds, so losing it on upgrade is not acceptable. To change the schema:

1. Bump `AppDatabase.VERSION`.
2. Add a `Migration(n, n+1)` to `MIGRATIONS`.
3. Build to export `app/schemas/…/<n+1>.json`.

`MigrationTest` then validates the path from version 1 to the latest automatically.
