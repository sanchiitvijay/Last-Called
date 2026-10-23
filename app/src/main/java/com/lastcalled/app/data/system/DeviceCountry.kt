package com.lastcalled.app.data.system

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Country used to interpret national numbers. Read from the network / SIM country (no permission
 * required) with the device locale as fallback. Nothing is looked up online.
 */
class DeviceCountry(private val context: Context) {
    val iso: String by lazy {
        val tm = context.getSystemService(TelephonyManager::class.java)
        listOfNotNull(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.length == 2 }
            ?.uppercase(Locale.ROOT)
            ?: "US"
    }

    /** E.164 form using the platform's bundled (offline) number metadata, or null if not parseable. */
    fun toE164(number: String?, countryIso: String? = null): String? {
        if (number.isNullOrBlank()) return null
        val iso = countryIso?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: this.iso
        return runCatching { PhoneNumberUtils.formatNumberToE164(number, iso) }.getOrNull()
    }
}
