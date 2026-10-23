package com.lastcalled.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastcalled.app.data.TestData
import com.lastcalled.app.data.repository.LabeledNumber
import com.lastcalled.app.data.repository.PersonDetails
import com.lastcalled.app.ui.common.PermissionStatus
import com.lastcalled.app.ui.details.PersonDetailsScreen
import com.lastcalled.app.ui.details.PersonDetailsUiState
import com.lastcalled.app.ui.onboarding.OnboardingScreen
import com.lastcalled.app.ui.onboarding.OnboardingStep
import com.lastcalled.app.ui.search.SearchScreen
import com.lastcalled.app.ui.search.SearchUiState
import com.lastcalled.app.ui.settings.DataAccessScreen
import com.lastcalled.app.ui.theme.LastCalledTheme
import com.lastcalled.app.ui.watchlist.WatchCandidate
import com.lastcalled.app.ui.watchlist.WatchListScreen
import com.lastcalled.app.ui.watchlist.WatchListUiState
import com.lastcalled.domain.model.CallDirection
import com.lastcalled.domain.model.CallFilter
import com.lastcalled.domain.model.CallRecord
import com.lastcalled.domain.model.CallSource
import com.lastcalled.domain.model.CallType
import com.lastcalled.domain.model.PersonKey
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun walksThroughExplainRequestExplainRequestAndWhatsAppNotice() {
        val events = mutableListOf<String>()
        var step by mutableStateOf(OnboardingStep.WELCOME)
        var callLog by mutableStateOf(PermissionStatus.NOT_REQUESTED)
        rule.setContent {
            LastCalledTheme {
                OnboardingScreen(
                    step = step,
                    callLogStatus = callLog,
                    contactsStatus = PermissionStatus.NOT_REQUESTED,
                    onNext = { step = OnboardingStep.entries[step.ordinal + 1] },
                    onRequestCallLog = { events += "call_log"; callLog = PermissionStatus.GRANTED },
                    onRequestContacts = { events += "contacts" },
                    onOpenAppSettings = { events += "settings" },
                    onFinish = { events += "finish" },
                )
            }
        }
        rule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_call_log").assertIsDisplayed()
        rule.onNodeWithText("Not requested yet").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_allow").performClick()
        rule.onNodeWithText("Allowed").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_contacts").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithText(
            "Android does not provide this app with direct access to WhatsApp's private call history",
            substring = true,
        ).assertIsDisplayed()
        rule.onNodeWithTag("onboarding_finish").performClick()
        assertEquals(listOf("call_log", "finish"), events)
    }

    @Test fun deniedAndPermanentlyDeniedStates() {
        var status by mutableStateOf(PermissionStatus.DENIED)
        val events = mutableListOf<String>()
        rule.setContent {
            LastCalledTheme {
                OnboardingScreen(OnboardingStep.CALL_LOG, status, PermissionStatus.NOT_REQUESTED, {}, { events += "ask" }, {}, { events += "settings" }, {})
            }
        }
        rule.onNodeWithText("Not allowed. You can ask again.").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_allow").performClick()
        status = PermissionStatus.PERMANENTLY_DENIED
        rule.onNodeWithText("Blocked. Allow it from the app's system settings.").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_open_settings").performClick()
        assertEquals(listOf("ask", "settings"), events)
    }

    @Test fun dataAccessScreenShowsEveryPermission() {
        rule.setContent {
            LastCalledTheme {
                DataAccessScreen(PermissionStatus.GRANTED, PermissionStatus.PERMANENTLY_DENIED, false, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("Call log (required)").assertIsDisplayed()
        rule.onNodeWithText("Allowed").assertIsDisplayed()
        rule.onNodeWithText("Blocked. Allow it from the app's system settings.").assertIsDisplayed()
        rule.onNodeWithText("Notification access (optional)").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun typingSearchesAndShowsMatches() {
        val queries = mutableListOf<String>()
        var state by mutableStateOf(SearchUiState())
        rule.setContent {
            LastCalledTheme {
                SearchScreen(state, { q ->
                    queries += q
                    state = SearchUiState(q, if ("rah" in q.lowercase()) listOf(UiFixtures.rahul) else emptyList(), UiFixtures.NOW)
                }, {}, {})
            }
        }
        rule.onNodeWithTag("search_field").performTextInput("Rahul")
        rule.onNodeWithText("Rahul Sharma").assertIsDisplayed()
        rule.onNodeWithText("Last called 2 days ago").assertIsDisplayed()
        assertEquals("Rahul", queries.last())
    }

    @Test fun noResults() {
        rule.setContent { LastCalledTheme { SearchScreen(SearchUiState("zzz", emptyList(), 0), {}, {}, {}) } }
        rule.onNodeWithTag("search_empty").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class PersonDetailsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val key = PersonKey.Contact("rahul")
    private val now = TestData.NOW

    private val whatsAppOut = TestData.call(key, 4.0, CallDirection.OUTGOING, CallSource.WHATSAPP, CallType.VOICE, duration = 751).copy(id = 1)
    private val phoneIn = TestData.call(key, 9.0, CallDirection.INCOMING, CallSource.PHONE, duration = 252).copy(id = 2)
    private val videoOut = TestData.call(key, 17.0, CallDirection.OUTGOING, CallSource.WHATSAPP, CallType.VIDEO, duration = 1084).copy(id = 3)

    private fun show(
        latestOutgoing: CallRecord?,
        history: List<CallRecord>,
        onFilter: (CallFilter) -> Unit = {},
        tracked: Boolean = false,
        onTracked: (Boolean) -> Unit = {},
    ) {
        val details = PersonDetails(key, "Rahul Sharma", null, null, listOf(LabeledNumber("+91 98765 43210", "Mobile")), latestOutgoing, history.firstOrNull())
        rule.setContent {
            LastCalledTheme {
                val items = flowOf(PagingData.from(history)).collectAsLazyPagingItems()
                PersonDetailsScreen(PersonDetailsUiState(false, details, now, CallFilter.ALL, tracked), items, onFilter, onBack = {}, onTrackedChange = onTracked)
            }
        }
    }

    @Test fun showsLastOutgoingCallDetailsAndHistory() {
        show(whatsAppOut, listOf(whatsAppOut, phoneIn, videoOut))
        rule.onNodeWithTag("details_name").assertIsDisplayed()
        rule.onNodeWithText("+91 98765 43210 · Mobile").assertIsDisplayed()
        rule.onNodeWithTag("last_called_value").assertIsDisplayed()
        rule.onNodeWithText("4 days ago").assertIsDisplayed()
        rule.onNodeWithText("Voice call").assertIsDisplayed()
        rule.onNodeWithText("Duration 12m 31s").assertIsDisplayed()
        rule.onNodeWithTag("details_list").performScrollToNode(hasText("18m 04s"))
        rule.onAllNodesWithTag("history_row").assertCountEquals(3)
    }

    @Test fun noOutgoingCalls() {
        show(null, listOf(phoneIn))
        rule.onNodeWithText("No outgoing calls found").assertIsDisplayed()
        rule.onNodeWithText("Most recent call of any kind: 9 days ago").assertIsDisplayed()
    }

    @Test fun starTogglesKeepInTouchList() {
        val changes = mutableListOf<Boolean>()
        show(whatsAppOut, listOf(whatsAppOut), onTracked = { changes += it })
        rule.onNodeWithContentDescription("Add to list").performClick()
        assertEquals(listOf(true), changes)
    }

    @Test fun historyFilterChipsReportChanges() {
        var last: CallFilter? = null
        show(whatsAppOut, listOf(whatsAppOut), onFilter = { last = it })
        rule.onNodeWithTag("details_list").performScrollToNode(hasText("Video"))
        rule.onNodeWithTag("filter_type_VIDEO").performClick()
        assertEquals(CallFilter(types = setOf(CallType.VIDEO)), last)
    }
}

@RunWith(AndroidJUnit4::class)
class WatchListScreenTest {
    @get:Rule val rule = createComposeRule()

    private val onList = WatchCandidate(PersonKey.Contact("rahul"), "Rahul Sharma", "+91 98765 43210", null, tracked = true)
    private val suggestion = WatchCandidate(PersonKey.Contact("priya"), "Priya", null, null, tracked = false)

    @Test fun showsListAndSuggestionsAndAddsAndRemoves() {
        val events = mutableListOf<String>()
        rule.setContent {
            LastCalledTheme {
                WatchListScreen(
                    WatchListUiState(onList = listOf(onList), candidates = listOf(suggestion)),
                    {}, { events += "add:${it.title}" }, { events += "remove:${it.value}" }, { events += "number" }, {},
                )
            }
        }
        rule.onNodeWithText("On your list").assertIsDisplayed()
        rule.onNodeWithText("Rahul Sharma").assertIsDisplayed()
        rule.onNodeWithText("People you have calls with").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove from list").performClick()
        rule.onNodeWithContentDescription("Add to list").performClick()
        assertEquals(listOf("remove:c:rahul", "add:Priya"), events)
    }

    @Test fun typedNumberCanBeAdded() {
        var added = false
        var state by mutableStateOf(WatchListUiState())
        rule.setContent {
            LastCalledTheme {
                WatchListScreen(state, { q -> state = WatchListUiState(query = q, addableNumber = q) }, {}, {}, { added = true }, {})
            }
        }
        rule.onNodeWithTag("watchlist_search").performTextInput("+44 7700 900123")
        rule.onNodeWithText("Add number +44 7700 900123").performClick()
        assertEquals(true, added)
    }

    @Test fun emptyListExplainsHowToAdd() {
        rule.setContent { LastCalledTheme { WatchListScreen(WatchListUiState(), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Your list is empty", substring = true).assertIsDisplayed()
    }
}
