package com.lastcalled.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcalled.app.data.local.CallStats
import com.lastcalled.app.ui.common.PermissionStatus
import com.lastcalled.app.ui.home.HomeScreen
import com.lastcalled.app.ui.home.HomeUiState
import com.lastcalled.app.ui.theme.LastCalledTheme
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val recorder = UiFixtures.Recorder()

    private fun show(
        state: HomeUiState = UiFixtures.state(),
        callLog: PermissionStatus = PermissionStatus.GRANTED,
        contacts: PermissionStatus = PermissionStatus.GRANTED,
    ) = rule.setContent {
        LastCalledTheme { HomeScreen(state, callLog, contacts, recorder.actions()) }
    }

    private fun scrollTo(text: String) =
        rule.onNodeWithTag("home_list").performScrollToNode(hasText(text, substring = true))

    @Test fun showsDashboardAndPeopleWithHumanReadableTimes() {
        show()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithText("People not called recently").assertIsDisplayed()
        scrollTo("Last called 6 hours ago")
        scrollTo("Last called 2 days ago")
        scrollTo("WhatsApp voice")
        scrollTo("WhatsApp video")
        scrollTo("Never called")
    }

    @Test fun dashboardShowsCompactGaps() {
        show()
        rule.onNodeWithText("15 days").assertIsDisplayed()
    }

    @Test fun tappingPersonOpensDetails() {
        show()
        scrollTo("Rahul Sharma")
        rule.onNodeWithText("Rahul Sharma").performClick()
        assertTrue("open:c:rahul" in recorder.events)
    }

    @Test fun thresholdAndSortControls() {
        show()
        rule.onNodeWithTag("threshold_30").performClick()
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("sort_button"))
        rule.onNodeWithTag("sort_button").performClick()
        rule.onNodeWithTag("sort_LONGEST_SINCE_CALLED").performClick()
        assertEquals(listOf("threshold:30", "sort:LONGEST_SINCE_CALLED"), recorder.events)
    }

    @Test fun filtersCombine() {
        show()
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("filter_source_WHATSAPP"))
        rule.onNodeWithTag("filter_source_WHATSAPP").performClick()
        assertEquals(
            CallFilter(sources = setOf(CallSource.WHATSAPP), directions = setOf(CallDirection.OUTGOING)),
            recorder.lastFilter,
        )
    }

    @Test fun refreshSearchSettingsActions() {
        show()
        rule.onNodeWithTag("action_refresh").performClick()
        rule.onNodeWithTag("action_search").performClick()
        rule.onNodeWithTag("action_settings").performClick()
        assertEquals(listOf("refresh", "search", "settings"), recorder.events)
    }

    @Test fun emptyStateWhenCallLogPermissionMissing() {
        show(UiFixtures.state(people = emptyList(), stats = CallStats(0, 0, 0)), callLog = PermissionStatus.DENIED)
        rule.onNodeWithText("Grant call log permission to import calls.").assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(listOf("request_call_log"), recorder.events)
    }

    @Test fun permanentlyDeniedOffersSystemSettings() {
        show(UiFixtures.state(people = emptyList(), stats = CallStats(0, 0, 0)), callLog = PermissionStatus.PERMANENTLY_DENIED)
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("app_settings"), recorder.events)
    }

    @Test fun revokedPermissionShowsBannerButKeepsHistory() {
        show(callLog = PermissionStatus.DENIED, contacts = PermissionStatus.DENIED)
        rule.onNodeWithText("Call log access is off. New calls cannot be imported.").assertIsDisplayed()
        rule.onNodeWithText("Contacts access is off, so numbers are shown instead of names.").assertIsDisplayed()
        scrollTo("Rahul Sharma")
    }

    @Test fun emptyCallLog() {
        show(UiFixtures.state(people = emptyList(), stats = CallStats(0, 0, 0)))
        rule.onNodeWithText("No call history available").assertIsDisplayed()
    }

    @Test fun whatsAppUnavailableMessage() {
        show(
            UiFixtures.state(
                people = emptyList(),
                stats = CallStats(5, 0, 3),
                filter = CallFilter(sources = setOf(CallSource.WHATSAPP), directions = setOf(CallDirection.OUTGOING)),
            ),
        )
        scrollTo("WhatsApp calls are not available in your device call history.")
    }

    @Test fun noContactsWithHistory() {
        show(UiFixtures.state(people = emptyList(), stats = CallStats(5, 0, 0), hiddenUnknown = 3))
        scrollTo("No contacts with call history")
        scrollTo("No outgoing calls found")
    }

    @Test fun dashboardIsEmptyUntilPeopleAreAdded() {
        show(UiFixtures.state(notCalled = emptyList(), watchListSize = 0))
        rule.onNodeWithTag("watchlist_empty").assertIsDisplayed()
        rule.onNodeWithTag("watchlist_add_people").performClick()
        assertEquals(listOf("watchlist"), recorder.events)
    }

    @Test fun dashboardShowsOnlyListedPeopleAndCanBeEdited() {
        show(UiFixtures.state(notCalled = listOf(UiFixtures.priya), watchListSize = 3))
        rule.onNodeWithText("15 days").assertIsDisplayed()
        rule.onNodeWithTag("watchlist_manage").performClick()
        assertEquals(listOf("watchlist"), recorder.events)
    }

    @Test fun everyoneOnListCalledRecently() {
        show(UiFixtures.state(notCalled = emptyList(), watchListSize = 2))
        rule.onNodeWithText("You have called everyone on your list within this period.").assertIsDisplayed()
    }
}
