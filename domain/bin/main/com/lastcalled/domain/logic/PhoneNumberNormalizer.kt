package com.lastcalled.domain.logic

/**
 * A phone number reduced to forms that can be compared.
 *
 * @property raw the number exactly as the system reported it.
 * @property e164 international form ("+919876543210") when it can be determined without guessing:
 *   the raw value was already international, or the platform supplied a normalized value.
 * @property significantDigits digits used for suffix comparison: the E.164 digits when known,
 *   otherwise the dialled digits with national trunk prefix zeros removed.
 */
data class NormalizedNumber(
    val raw: String,
    val e164: String?,
    val significantDigits: String,
) {
    /** Grouping key for numbers that are not in contacts. */
    val canonical: String get() = e164 ?: significantDigits.ifEmpty { raw }

    /** Short numbers (service codes) are only compared exactly. */
    val isShort: Boolean get() = significantDigits.length < PhoneNumberNormalizer.MIN_MATCH

    /** Index key: the last [PhoneNumberNormalizer.MIN_MATCH] digits. */
    val indexKey: String
        get() = if (isShort) significantDigits else significantDigits.takeLast(PhoneNumberNormalizer.MIN_MATCH)
}

/**
 * Offline phone number normalization and comparison.
 *
 * Matching follows the same idea as Android's own caller-ID matching (compare a minimum number of
 * trailing digits), with one safety rule on top: two numbers that are both known in international
 * form must be identical, so "+91 98765 43210" never matches "+1 987 654 3210".
 *
 * Examples that compare equal: "+91 9876543210", "09876543210", "9876543210", "0091 98765-43210".
 */
object PhoneNumberNormalizer {
    const val MIN_MATCH = 7

    private val separators = Regex("[\\s\\-().\\u00A0/]")
    private val e164Pattern = Regex("^\\+[1-9][0-9]{6,14}$")

    /**
     * @param raw number as stored by the system or contacts provider.
     * @param platformE164 an E.164 value supplied by the platform (e.g. CallLog's cached normalized
     *   number or Contacts' NORMALIZED_NUMBER). Ignored when malformed.
     */
    fun normalize(raw: String?, platformE164: String? = null): NormalizedNumber? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val compact = trimmed.replace(separators, "")
        val digits = compact.filter { it.isDigit() }
        if (digits.isEmpty()) return NormalizedNumber(trimmed, null, "")

        val ownE164 = when {
            compact.startsWith("+") && compact.drop(1).all { it.isDigit() } -> "+$digits"
            compact.startsWith("00") && compact.all { it.isDigit() } -> "+" + digits.drop(2)
            else -> null
        }?.takeIf { e164Pattern.matches(it) }

        val e164 = ownE164 ?: platformE164?.trim()?.takeIf { e164Pattern.matches(it) }
        val significant = e164?.drop(1) ?: digits.trimStart('0').ifEmpty { digits }
        return NormalizedNumber(trimmed, e164, significant)
    }

    /**
     * Strength of the match between two numbers: 0 = different numbers, higher is stronger.
     *  - 3: identical international numbers
     *  - 2: identical significant digits
     *  - 1: one is a suffix of the other with at least [MIN_MATCH] digits (e.g. national vs. international)
     */
    fun matchStrength(a: NormalizedNumber, b: NormalizedNumber): Int {
        if (a.e164 != null && b.e164 != null) return if (a.e164 == b.e164) 3 else 0
        val x = a.significantDigits
        val y = b.significantDigits
        if (x.isEmpty() || y.isEmpty()) return 0
        if (x == y) return 2
        if (a.isShort || b.isShort) return 0
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return if (long.endsWith(short)) 1 else 0
    }

    fun matches(a: NormalizedNumber, b: NormalizedNumber): Boolean = matchStrength(a, b) > 0

    /** True when a string looks like a dialable phone number rather than a name. */
    fun looksLikePhoneNumber(text: String): Boolean {
        val compact = text.trim().replace(separators, "")
        if (compact.isEmpty()) return false
        val body = compact.removePrefix("+")
        return body.length >= 3 && body.all { it.isDigit() }
    }
}
