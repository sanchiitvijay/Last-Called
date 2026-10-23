package com.lastcalled.domain

import com.lastcalled.domain.Fixtures.NOW
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.notification.CallNotificationSnapshot
import com.lastcalled.domain.notification.CallNotificationTracker
import com.lastcalled.domain.notification.CallPhase
import com.lastcalled.domain.notification.WhatsAppCallNotificationParser as Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppNotificationTest {
    private fun snap(
        key: String = "k1",
        pkg: String = "com.whatsapp",
        category: String? = "call",
        callType: Int? = null,
        fullScreen: Boolean = false,
        title: String? = "Rahul Sharma",
        text: String? = "Ongoing voice call",
        time: Long = NOW,
    ) = CallNotificationSnapshot(key, pkg, category, callType, fullScreen, title, text, time)

    @Test fun `ignores other apps and non-call notifications`() {
        assertNull(Parser.parse(snap(pkg = "com.example.chat")))
        assertNull(Parser.parse(snap(category = "msg")))
        assertNull(Parser.parse(snap(category = null)))
        assertFalse(Parser.isCandidate("com.whatsapp", "msg"))
    }

    @Test fun `call style type decides the phase`() {
        assertEquals(CallPhase.RINGING, Parser.parse(snap(callType = Parser.CALL_STYLE_INCOMING))!!.phase)
        assertEquals(CallPhase.ACTIVE, Parser.parse(snap(callType = Parser.CALL_STYLE_ONGOING))!!.phase)
    }

    @Test fun `without call style a full screen intent means ringing`() {
        assertEquals(CallPhase.RINGING, Parser.parse(snap(fullScreen = true))!!.phase)
        assertEquals(CallPhase.ACTIVE, Parser.parse(snap(fullScreen = false))!!.phase)
    }

    @Test fun `call type only from explicit wording`() {
        assertEquals(CallType.VIDEO, Parser.callTypeFromText("Incoming video call"))
        assertEquals(CallType.VOICE, Parser.callTypeFromText("Ongoing voice call"))
        assertEquals(CallType.UNKNOWN, Parser.callTypeFromText("Llamada entrante"))
        assertEquals(CallType.UNKNOWN, Parser.callTypeFromText(null))
    }

    private val tracker = CallNotificationTracker()

    @Test fun `active without ringing is an outgoing call`() {
        tracker.onPosted(Parser.parse(snap(callType = 2, time = NOW))!!)
        val e = tracker.onRemoved("k1", NOW + 95_000)!!
        assertEquals(CallDirection.OUTGOING, e.direction)
        assertEquals(95, e.durationSeconds)
        assertEquals(CallType.VOICE, e.callType)
        assertEquals("Rahul Sharma", e.counterpart)
    }

    @Test fun `ringing then active is an incoming call even if WhatsApp swaps the notification`() {
        tracker.onPosted(Parser.parse(snap(key = "ring", callType = 1, text = "Incoming video call", time = NOW))!!)
        tracker.onPosted(Parser.parse(snap(key = "active", callType = 2, text = "Ongoing call", time = NOW + 5_000))!!)
        assertNull(tracker.onRemoved("ring", NOW + 6_000))
        val e = tracker.onRemoved("active", NOW + 65_000)!!
        assertEquals(CallDirection.INCOMING, e.direction)
        assertEquals(NOW, e.startedAtMillis)
        assertEquals(60, e.durationSeconds)
        assertEquals(CallType.VIDEO, e.callType)
    }

    @Test fun `ringing that ends without being answered is missed`() {
        tracker.onPosted(Parser.parse(snap(callType = 1))!!)
        val e = tracker.onRemoved("k1", NOW + 30_000)!!
        assertEquals(CallDirection.MISSED, e.direction)
        assertEquals(0, e.durationSeconds)
    }

    @Test fun `updates to the same notification do not reset the start`() {
        tracker.onPosted(Parser.parse(snap(callType = 2, time = NOW))!!)
        tracker.onPosted(Parser.parse(snap(callType = 2, time = NOW + 10_000))!!)
        assertEquals(20, tracker.onRemoved("k1", NOW + 20_000)!!.durationSeconds)
    }

    @Test fun `unknown removal is ignored`() = assertNull(tracker.onRemoved("nope", NOW))
}
