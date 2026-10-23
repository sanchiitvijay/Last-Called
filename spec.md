Build a fully offline mobile app that helps the user keep track of when they last called people.

The main purpose of the app is simple:

The app reads the call history available on the device, identifies calls associated with contacts, and lets the user see how long it has been since they last called each person.

The user should be able to select a person and immediately see information such as:

• When I last called this person  
• How long ago that was  
• Whether the last call was a normal phone call or WhatsApp  
• Whether it was an audio or video call when that information is available  
• The previous calls with that person  
• A simple human readable value such as "2 days ago", "3 hours ago", or "18 days ago"

Important platform requirement:

Build the first version as a native Android app using Kotlin and Jetpack.

Do not assume that a third party Android app can directly read WhatsApp's private call history database. WhatsApp's own documentation says its call history is stored in its chat database and that WhatsApp call history cannot be exported. However, WhatsApp calls can appear in the device's phone call history, and Android provides access to the system CallLog through the appropriate permission. Android also has unified call history support for calling apps that integrate with the Android telecom system.

Therefore, the implementation must distinguish between:

1. System phone calls that can be read through Android CallLog
2. WhatsApp calls that are present in the system call history
3. WhatsApp calls that are not exposed through the system call history

Never fake WhatsApp data and never claim that all WhatsApp calls can be retrieved.

The app should gracefully support whatever data Android legitimately exposes.

For WhatsApp calls that are not available through CallLog, investigate whether a notification listener can be used to capture future WhatsApp call events. Treat this only as an optional best effort source because notification formats can change and historical WhatsApp calls cannot reliably be reconstructed this way.

Do not attempt to read, decrypt, modify, reverse engineer, or access WhatsApp's private database or private application storage.

The entire application must work offline.

There must be:

• No backend
• No API server
• No cloud database
• No Firebase
• No analytics
• No telemetry
• No login
• No account system
• No internet based synchronization
• No remote API calls
• No data uploads

The app should not require the INTERNET permission at runtime. All processing and storage must happen locally on the device.

Use a clean architecture that is easy to understand and maintain.

Recommended stack:

Kotlin  
Jetpack Compose  
Material 3  
Room for local persistence  
Kotlin Coroutines  
Flow  
Android Contacts Provider  
Android CallLog Provider  
Android NotificationListenerService only if required for optional future WhatsApp event detection  
Android Keystore for protecting locally stored sensitive information where appropriate

Before writing the implementation, inspect the current Android APIs and design the solution around officially supported APIs.

The application should request only the permissions that are actually required.

Potential permissions include:

READ_CALL_LOG  
READ_CONTACTS

If notification based WhatsApp detection is implemented, use Android's NotificationListenerService instead of trying to obtain private WhatsApp data.

Do not request unnecessary permissions such as SMS, location, microphone, camera, storage, or phone state unless a clearly documented feature genuinely requires them.

Core functionality:

1. Import existing call history

On first launch, explain why call log and contacts access are required.

Ask the user for permission.

After permission is granted:

Read the available device call history.

Read contacts locally.

Match phone numbers from the call log against local contacts.

Normalize phone numbers before matching so that values such as:

+91 9876543210  
09876543210  
9876543210

can be compared correctly where appropriate.

Do not assume every number belongs to a contact.

Unknown callers should still be stored, but clearly marked as unknown.

2. Detect call source

Every call should have a source such as:

PHONE  
WHATSAPP  
UNKNOWN

Do not incorrectly label every VoIP call as WhatsApp.

Use officially available metadata from Android CallLog or other supported system sources to determine the source.

Where the source cannot be reliably determined, mark it as UNKNOWN.

3. Detect call type

Store:

VOICE  
VIDEO  
UNKNOWN

Only mark a call as VIDEO when the available system information actually indicates a video call.

Do not infer video calls from assumptions.

4. Detect direction

Store:

OUTGOING  
INCOMING  
MISSED  
REJECTED  
UNKNOWN

The primary metric for this application is OUTGOING because the user wants to know when they last called someone.

5. Contact based view

The main screen should show a list of contacts who have call history.

Example:

Rahul Sharma  
Last called 2 days ago  
WhatsApp voice

Ankit  
Last called 6 hours ago  
Phone call

Priya  
Last called 15 days ago  
WhatsApp video

Mehul  
Never called

Allow sorting by:

Recently called  
Longest time since last call  
Alphabetical

The "Longest time since last call" view is important because the app's purpose is to help the user notice people they have not called for a long time.

6. Person detail screen

When the user selects a person, show:

Contact name  
Profile image if locally available  
Phone number if available  
Last outgoing call  
Time since last outgoing call  
Call source  
Call type  
Call duration

Then show call history:

Date  
Time  
Direction  
Source  
Voice or video  
Duration

Example:

Rahul Sharma

Last called:
4 days ago

WhatsApp  
Voice call  
October 3, 2026 at 8:42 PM  
Duration 12m 31s

History:

Oct 3  
Outgoing  
WhatsApp Voice  
12m 31s

Sep 28  
Incoming  
Phone  
4m 12s

Sep 20  
Outgoing  
WhatsApp Video  
18m 04s

7. Main purpose dashboard

Make the home screen useful immediately.

Show a simple summary such as:

People not called recently

Rahul  
4 days

Aman  
11 days

Priya  
23 days

Rohit  
31 days

Give the user configurable time filters such as:

Not called in 3 days  
Not called in 7 days  
Not called in 14 days  
Not called in 30 days

This should be calculated locally from stored call data.

8. Filters

Allow filtering by:

All calls  
Phone  
WhatsApp  
Outgoing  
Incoming  
Voice  
Video

Also allow combinations where practical.

Example:

WhatsApp + Outgoing

should show only WhatsApp calls that the user made.

9. Search

Add contact search.

The user should be able to type:

Rahul

and see matching contacts immediately.

Search must happen locally.

10. Refresh

Provide a Refresh button.

When pressed:

Read the latest available system call history.

Reconcile it against the local database.

Insert new calls.

Avoid duplicate records.

Update contact mappings.

Do not erase existing application data unless the user explicitly chooses to reset it.

Consider also refreshing automatically when the app comes to the foreground.

11. Local database

Design a proper Room schema.

At minimum consider entities such as:

Contact
CallRecord
AppSettings

CallRecord should contain fields conceptually similar to:

id  
contactId  
phoneNumber  
timestamp  
durationSeconds  
direction  
source  
callType  
systemCallLogId  
createdAt  
updatedAt

Use enums or sealed representations where appropriate.

The system call log ID should be used to prevent importing the same call multiple times.

12. Unknown callers

A call may not map to a local contact.

Support this cleanly.

For example:

Unknown number  
+91 XXXXXXXX

The user should still be able to inspect its call history.

Do not silently discard unmatched calls.

13. Privacy

This application processes highly sensitive personal data.

Everything must remain on the device.

Do not write call records to logs.

Do not send call data anywhere.

Do not include analytics SDKs.

Do not include crash reporting services that transmit personal data.

Do not put phone numbers into debug logs.

Clearly explain this in the Privacy documentation.

If encryption is practical, protect sensitive locally stored information using Android Keystore backed mechanisms.

Do not make the architecture unnecessarily complicated just to add encryption. Prioritize a secure and maintainable design.

14. Permissions UX

Create a proper permission onboarding flow.

For example:

Step 1
Explain that the application needs access to phone call history.

Step 2
Request READ_CALL_LOG.

Step 3
Explain that contacts access is used to convert phone numbers into contact names.

Step 4
Request READ_CONTACTS.

If optional WhatsApp notification tracking exists, explain:

"Android does not provide this app with direct access to WhatsApp's private call history. Optional notification access can help detect some future WhatsApp call events, but coverage is not guaranteed."

Never mislead the user.

Handle:

Permission granted  
Permission denied  
Permission permanently denied  
Permission revoked later

with useful UI and settings shortcuts.

15. WhatsApp handling

This is especially important.

Do not build the app around the assumption that WhatsApp exposes a public API for reading another app's historical call log.

The implementation should first use Android's supported call history APIs.

Determine whether the specific WhatsApp call is represented in the device's CallLog.

If it is:

Import it.

If it is not:

Do not invent it.

Optionally implement NotificationListenerService for future events, behind an explicit setting.

The notification listener should:

Only inspect notifications needed for recognizing call activity.

Extract the minimum information required.

Store the event locally.

Avoid storing notification contents unnecessarily.

Document that notification based detection is best effort and can stop working if WhatsApp changes its notification format.

16. Android version compatibility

Choose a sensible minimum Android version.

Use modern Android APIs where possible.

Document:

Minimum supported Android version  
Target SDK  
Required permissions  
Optional permissions  
Android versions where unified call history behavior differs

Pay particular attention to newer Android call log integration and restrictions.

17. UI design

Keep the UI simple and practical.

The application is not a social network.

The core workflow should be:

Open app  
See people you have not called recently  
Tap a person  
See when you last called them  
View call history

Recommended screens:

Home  
Person Details  
Search  
Settings  
Permissions / Data Access

Use Material 3 and Jetpack Compose.

Make the UI responsive for different Android screen sizes.

Use proper loading, empty, error, and permission states.

18. Empty states

Examples:

"No call history available"

"Grant call log permission to import calls."

"No contacts with call history"

"No outgoing calls found"

"WhatsApp calls are not available in your device call history."

19. Settings

Include:

Refresh call history  
Manage permissions  
Enable or disable notification based detection  
Choose default time threshold  
Clear app data  
Reset imported call records

Clearly explain that clearing app data does not delete the user's actual phone or WhatsApp call history. It only clears this application's local database.

20. Architecture

Use a clean architecture with clear separation between:

UI  
ViewModel  
Domain  
Repositories  
Local data sources  
Android system data sources

For example:

Compose UI
   ↓
ViewModel
   ↓
Use Cases
   ↓
Repository
   ↓
Room + Android CallLog + Contacts Provider

Keep Android framework code isolated from business logic wherever practical.

21. Data processing

Create a pipeline such as:

Read system call records
→ Normalize phone numbers
→ Resolve contacts
→ Determine source
→ Determine direction
→ Determine call type
→ Convert to domain model
→ Deduplicate
→ Store in Room
→ Recalculate latest outgoing call per contact
→ Display results

Do not calculate "last called" purely from UI state.

It should be derived from persisted call records.

22. Last called calculation

For every person, calculate:

latestOutgoingCall

This must consider only calls where:

direction == OUTGOING

Then display:

lastCalledAt

and calculate:

timeSinceLastCall

based on the current device time.

Do not store a continuously changing value such as "3 days ago" in the database.

Store the timestamp and calculate the human readable duration dynamically.

23. Important edge cases

Handle:

Multiple phone numbers for the same contact  
Contacts with no phone numbers  
Unknown numbers  
Duplicate contacts  
Country code differences  
Deleted contacts  
Contact renamed after a call  
Calls with zero duration  
Missed calls  
Rejected calls  
Very old calls  
Large call histories  
Permission revoked  
Empty CallLog  
WhatsApp calls unavailable in CallLog  
Notification access unavailable  
Device reboot  
Database migration  
Timezone changes  
Daylight saving changes where relevant

24. Performance

Call history can contain many records.

Do not load the entire database into Compose state unnecessarily.

Use Room queries to retrieve only what is required.

Use paging if required for large histories.

Perform imports asynchronously.

Keep UI responsive while syncing.

25. Testing

Write unit tests for:

Phone number normalization  
Contact matching  
Call source detection  
Direction mapping  
Last outgoing call calculation  
Time since last call calculation  
Deduplication  
Filtering  
Sorting

Write integration tests for:

CallLog import  
Room persistence  
Contact matching  
Database migrations

Add UI tests for:

Permission flow  
Home screen  
Contact search  
Person details  
Filters  
Empty states

Create test fixtures representing:

Phone calls  
WhatsApp calls exposed through CallLog  
Unknown calls  
Incoming calls  
Outgoing calls  
Missed calls  
Video calls where supported  
Duplicate records

26. Documentation

Create proper documentation in the repository.

At minimum create:

README.md

ARCHITECTURE.md

DATA_MODEL.md

PERMISSIONS.md

PRIVACY.md

WHATSAPP_SUPPORT.md

LIMITATIONS.md

TESTING.md

BUILD.md

README.md should explain:

What the application does  
Why it is useful  
Supported platforms  
How to build it  
How to install it locally  
Required permissions  
How data is stored  
How the refresh process works  
Known limitations

ARCHITECTURE.md should explain the complete application architecture and important design decisions.

DATA_MODEL.md should document every Room entity and relationship.

PERMISSIONS.md should explain every permission, why it is needed, and what happens when it is denied.

PRIVACY.md should clearly state that the app is designed to keep call data on the device and has no backend or cloud synchronization.

WHATSAPP_SUPPORT.md should clearly explain exactly how WhatsApp call information is obtained, what works, what does not work, and why.

LIMITATIONS.md should be honest about Android and WhatsApp limitations.

Do not write documentation claiming functionality that the Android APIs do not actually provide.

27. Offline requirement validation

The final application must continue to function with:

WiFi disabled  
Mobile data disabled  
Airplane mode enabled, except for the underlying phone system itself  
No backend available

The application must not depend on network connectivity for any feature.

Search the source code and manifest and verify that there is no unnecessary network dependency.

Do not add INTERNET permission unless an absolutely unavoidable system component requires it. The preferred implementation should not need it.

28. Security

Do not expose the Room database through external components.

Use app private storage.

Do not export activities, services, providers, or receivers unless required.

Do not expose call data through intents unnecessarily.

Do not store sensitive information in SharedPreferences when Room or protected storage is more appropriate.

Do not print phone numbers, contact names, or call details into logs.

29. Implementation quality

Do not build everything inside one Activity.

Do not put database logic inside Compose UI.

Do not put permission handling randomly throughout the application.

Create reusable components and clear interfaces.

Use dependency injection only if it improves maintainability. Keep the project understandable.

Prefer straightforward code over unnecessary abstractions.

30. Development process

First inspect and document the actual capabilities of the current Android APIs.

Then create the project structure.

Then implement the local database.

Then implement CallLog import.

Then implement contact matching.

Then implement last outgoing call calculation.

Then implement the UI.

Then implement optional WhatsApp notification tracking only if it is technically and legally appropriate.

Then add tests.

Then complete the documentation.

After implementation, review the entire project and verify:

The app builds successfully.

The app works without internet.

Call history permissions work correctly.

Contact matching works.

Duplicate calls are not inserted.

Last outgoing call is calculated correctly.

The UI handles missing permissions.

WhatsApp limitations are clearly communicated.

No unsupported WhatsApp private data access is being attempted.

31. Deliverables

Provide a complete runnable Android Studio project.

Include:

Source code  
Gradle configuration  
Room database  
Compose UI  
Permission handling  
CallLog importer  
Contacts importer  
Call processing logic  
Tests  
Documentation  
Sample test data  
Clear local build instructions

The final application should feel like a small, polished utility whose single purpose is:

"Who haven't I called recently?"

The user should be able to open the app and immediately understand who they have not called for a while, select that person, and see exactly when their most recent outgoing call happened.

Prioritize correctness and transparency over pretending that unsupported WhatsApp data is available.
