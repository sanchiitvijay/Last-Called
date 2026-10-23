package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.OTHER_VOIP
import com.lastcalled.domain.Fixtures.TELEPHONY
import com.lastcalled.domain.Fixtures.WHATSAPP
import com.lastcalled.domain.Fixtures.WHATSAPP_BUSINESS
import com.lastcalled.domain.logic.CallDirectionMapper
import com.lastcalled.domain.logic.CallSourceDetector
import com.lastcalled.domain.logic.CallTypeDetector
import com.lastcalled.domain.logic.SystemCallLogContract as C
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Test

class CallSourceDetectionTest {
    @Test fun `telephony phone account is PHONE`() = assertEquals(CallSource.PHONE, CallSourceDetector.detect(TELEPHONY))

    @Test fun `WhatsApp and WhatsApp Business are WHATSAPP`() {
        assertEquals(CallSource.WHATSAPP, CallSourceDetector.detect(WHATSAPP))
        assertEquals(CallSource.WHATSAPP, CallSourceDetector.detect(WHATSAPP_BUSINESS))
    }

    @Test fun `other VoIP apps are not labelled WhatsApp`() =
        assertEquals(CallSource.UNKNOWN, CallSourceDetector.detect(OTHER_VOIP))

    @Test fun `missing or malformed metadata is UNKNOWN`() {
        assertEquals(CallSource.UNKNOWN, CallSourceDetector.detect(null))
        assertEquals(CallSource.UNKNOWN, CallSourceDetector.detect(""))
        assertEquals(CallSource.UNKNOWN, CallSourceDetector.detect("/OnlyClass"))
    }

    @Test fun `package lookalikes are not WhatsApp`() =
        assertEquals(CallSource.UNKNOWN, CallSourceDetector.detect("com.whatsapp.fake/X"))
}

class CallDirectionMappingTest {
    @Test fun mapping() {
        assertEquals(CallDirection.OUTGOING, CallDirectionMapper.map(C.OUTGOING_TYPE))
        assertEquals(CallDirection.INCOMING, CallDirectionMapper.map(C.INCOMING_TYPE))
        assertEquals(CallDirection.INCOMING, CallDirectionMapper.map(C.ANSWERED_EXTERNALLY_TYPE))
        assertEquals(CallDirection.MISSED, CallDirectionMapper.map(C.MISSED_TYPE))
        assertEquals(CallDirection.REJECTED, CallDirectionMapper.map(C.REJECTED_TYPE))
        assertEquals(CallDirection.REJECTED, CallDirectionMapper.map(C.BLOCKED_TYPE))
        assertEquals(CallDirection.UNKNOWN, CallDirectionMapper.map(C.VOICEMAIL_TYPE))
        assertEquals(CallDirection.UNKNOWN, CallDirectionMapper.map(null))
        assertEquals(CallDirection.UNKNOWN, CallDirectionMapper.map(99))
    }
}

class CallTypeDetectionTest {
    @Test fun `video only when the system flag is set`() {
        assertEquals(CallType.VIDEO, CallTypeDetector.detect(C.FEATURES_VIDEO, CallSource.PHONE))
        assertEquals(CallType.VIDEO, CallTypeDetector.detect(C.FEATURES_VIDEO or 4, CallSource.WHATSAPP))
        assertEquals(CallType.VIDEO, CallTypeDetector.detect(C.FEATURES_VIDEO, CallSource.UNKNOWN))
    }

    @Test fun `known sources without video flag are voice`() {
        assertEquals(CallType.VOICE, CallTypeDetector.detect(0, CallSource.PHONE))
        assertEquals(CallType.VOICE, CallTypeDetector.detect(8, CallSource.WHATSAPP)) // Wi-Fi bit only
    }

    @Test fun `absence of information is UNKNOWN, never assumed`() {
        assertEquals(CallType.UNKNOWN, CallTypeDetector.detect(null, CallSource.PHONE))
        assertEquals(CallType.UNKNOWN, CallTypeDetector.detect(0, CallSource.UNKNOWN))
    }
}
