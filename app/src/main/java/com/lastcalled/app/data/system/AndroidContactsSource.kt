package com.lastcalled.app.data.system

import android.content.ContentResolver
import android.content.res.Resources
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.lastcalled.domain.logic.DeviceContact
import com.lastcalled.domain.logic.DevicePhoneNumber
import com.lastcalled.domain.sync.ContactsSource
import com.lastcalled.domain.sync.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads phone numbers from the local Contacts Provider (READ_CONTACTS). One query over the phone
 * data table; results are grouped into contacts in memory. Contacts without a phone number are not
 * returned because they can never match a call.
 */
class AndroidContactsSource(
    private val resolver: ContentResolver,
    private val resources: Resources,
    private val permissions: PermissionChecker,
    private val country: DeviceCountry,
) : ContactsSource {

    override suspend fun readContacts(): SourceResult<List<DeviceContact>> = withContext(Dispatchers.IO) {
        if (!permissions.hasContacts()) return@withContext SourceResult.PermissionMissing
        try {
            val cursor = resolver.query(Phone.CONTENT_URI, PROJECTION, null, null, null)
                ?: return@withContext SourceResult.Failed("Contacts query returned null")
            val byKey = LinkedHashMap<String, MutableContact>()
            cursor.use { c ->
                val contactId = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val lookup = c.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
                val name = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val number = c.getColumnIndexOrThrow(Phone.NUMBER)
                val normalized = c.getColumnIndexOrThrow(Phone.NORMALIZED_NUMBER)
                val photo = c.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
                val type = c.getColumnIndexOrThrow(Phone.TYPE)
                val label = c.getColumnIndexOrThrow(Phone.LABEL)
                while (c.moveToNext()) {
                    val key = c.stringOrNull(lookup) ?: continue
                    val raw = c.stringOrNull(number) ?: continue
                    val contact = byKey.getOrPut(key) {
                        MutableContact(key, c.getLong(contactId), c.stringOrNull(name), c.stringOrNull(photo))
                    }
                    val e164 = c.stringOrNull(normalized)?.takeIf { it.startsWith("+") } ?: country.toE164(raw)
                    val typeLabel = c.intOrNull(type)?.let { t ->
                        Phone.getTypeLabel(resources, t, c.stringOrNull(label))?.toString()
                    }
                    contact.numbers += DevicePhoneNumber(raw, e164, typeLabel)
                }
            }
            SourceResult.Available(
                byKey.values.map {
                    DeviceContact(it.lookupKey, it.id, it.name ?: it.numbers.first().number, it.photo, it.numbers)
                },
            )
        } catch (e: SecurityException) {
            SourceResult.PermissionMissing
        } catch (e: RuntimeException) {
            SourceResult.Failed(e.javaClass.simpleName)
        }
    }

    private class MutableContact(val lookupKey: String, val id: Long, val name: String?, val photo: String?) {
        val numbers = mutableListOf<DevicePhoneNumber>()
    }

    private companion object {
        val PROJECTION = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.NORMALIZED_NUMBER,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.TYPE,
            Phone.LABEL,
        )
    }
}
