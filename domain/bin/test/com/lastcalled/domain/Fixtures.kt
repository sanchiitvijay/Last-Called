package com.lastcalled.domain

import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.DevicePhoneNumber
import com.lastcalled.domain.logic.RawCallLogEntry
import com.lastcalled.domain.logic.SystemCallLogContract
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import com.lastcalled.domain.model.RecordOrigin

/** Shared, deterministic test data. NOW is a fixed instant so time-based assertions are stable. */
object Fixtures {
    const val NOW = 1_791_000_000_000L // 2026-10-03T…Z
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    const val TELEPHONY = "com.android.phone/com.android.services.telephony.TelephonyConnectionService"
    const val WHATSAPP = "com.whatsapp/com.whatsapp.voipcalling.SelfManagedConnectionService"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b/com.whatsapp.voipcalling.SelfManagedConnectionService"
    const val OTHER_VOIP = "org.telegram.messenger/org.telegram.messenger.voip.TelegramConnectionService"

    val rahul = contact("rahul", "Rahul Sharma", "+91 98765 43210" to "+919876543210")
    val ankit = contact("ankit", "Ankit", "098111 22233" to "+919811122233")
    val priya = contact("priya", "Priya", "+919900112233" to "+919900112233", "080 1234 5678" to "+918012345678")
    val mehul = contact("mehul", "Mehul", "9822001122" to null)
    val all = listOf(rahul, ankit, priya, mehul)

    fun contact(key: String, name: String, vararg numbers: Pair<String, String?>) = DeviceContact(
        lookupKey = key,
        deviceContactId = key.hashCode().toLong(),
        displayName = name,
        photoThumbnailUri = null,
        phoneNumbers = numbers.map { DevicePhoneNumber(it.first, it.second, "Mobile") },
    )

    private var nextId = 1L

    /** A system call log row. */
    fun raw(
        number: String?,
        type: Int = SystemCallLogContract.OUTGOING_TYPE,
        agoMillis: Long = DAY,
        duration: Long = 60,
        component: String? = TELEPHONY,
        features: Int? = 0,
        id: Long = nextId++,
        presentation: Int? = SystemCallLogContract.PRESENTATION_ALLOWED,
        e164: String? = null,
    ) = RawCallLogEntry(id, number, NOW - agoMillis, duration, type, features, component, presentation, e164)

    // Fixture rows by category, as named in the testing requirements.
    fun phoneOutgoing(agoMillis: Long = DAY) = raw("+919876543210", agoMillis = agoMillis)
    fun phoneIncoming(agoMillis: Long = DAY) = raw("09876543210", SystemCallLogContract.INCOMING_TYPE, agoMillis)
    fun missed(agoMillis: Long = DAY) = raw("9876543210", SystemCallLogContract.MISSED_TYPE, agoMillis, duration = 0)
    fun whatsAppVoice(agoMillis: Long = DAY) = raw("+919876543210", agoMillis = agoMillis, component = WHATSAPP)
    fun whatsAppVideo(agoMillis: Long = DAY) =
        raw("+919876543210", agoMillis = agoMillis, component = WHATSAPP, features = SystemCallLogContract.FEATURES_VIDEO)
    fun carrierVideo(agoMillis: Long = DAY) = raw("+919876543210", agoMillis = agoMillis, features = SystemCallLogContract.FEATURES_VIDEO)
    fun unknownCaller(agoMillis: Long = DAY) = raw("+14155550123", SystemCallLogContract.INCOMING_TYPE, agoMillis)

    fun record(
        person: PersonKey = PersonKey.Contact("rahul"),
        agoMillis: Long = DAY,
        direction: CallDirection = CallDirection.OUTGOING,
        source: CallSource = CallSource.PHONE,
        type: CallType = CallType.VOICE,
        origin: RecordOrigin = RecordOrigin.SYSTEM_CALL_LOG,
        id: Long = 0,
        systemId: Long? = if (origin == RecordOrigin.SYSTEM_CALL_LOG) nextId++ else null,
    ) = CallRecord(
        id = id,
        personKey = person,
        contactKey = (person as? PersonKey.Contact)?.lookupKey,
        phoneNumber = "+919876543210",
        normalizedNumber = "+919876543210",
        displayNameHint = null,
        timestamp = NOW - agoMillis,
        durationSeconds = 60,
        direction = direction,
        source = source,
        callType = type,
        origin = origin,
        systemCallLogId = systemId,
        sourcePackage = null,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
