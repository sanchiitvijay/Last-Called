package com.lastcalled.app.ui.navigation

import android.Manifest
import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.lastcalled.app.AppContainer
import com.lastcalled.app.LastCalledApplication
import com.lastcalled.app.data.repository.AppSettings
import com.lastcalled.app.ui.common.RuntimePermissionState
import com.lastcalled.app.ui.common.SystemSettings
import com.lastcalled.app.ui.common.rememberRuntimePermission
import com.lastcalled.app.ui.details.PersonDetailsScreen
import com.lastcalled.app.ui.details.PersonDetailsViewModel
import com.lastcalled.app.ui.home.HomeActions
import com.lastcalled.app.ui.home.HomeScreen
import com.lastcalled.app.ui.home.HomeViewModel
import com.lastcalled.app.ui.onboarding.OnboardingScreen
import com.lastcalled.app.ui.onboarding.OnboardingStep
import com.lastcalled.app.ui.search.SearchScreen
import com.lastcalled.app.ui.search.SearchViewModel
import com.lastcalled.app.ui.settings.DataAccessScreen
import com.lastcalled.app.ui.settings.SettingsActions
import com.lastcalled.app.ui.settings.SettingsScreen
import com.lastcalled.app.ui.settings.SettingsViewModel
import com.lastcalled.app.ui.watchlist.WatchListScreen
import com.lastcalled.app.ui.watchlist.WatchListViewModel
import com.lastcalled.domain.model.PersonKey
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ACCESS = "access"
    const val WATCH_LIST = "watchlist"
    const val PERSON = "person/{key}"
    fun person(key: PersonKey) = "person/" + Uri.encode(key.value)
}

/** Permission state shared by every screen, so permission handling lives in one place. */
class AppPermissions(val callLog: RuntimePermissionState, val contacts: RuntimePermissionState, val notificationAccess: Boolean)

@Composable
fun LastCalledApp(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val current = settings ?: return // Room answers within milliseconds; render nothing until then.

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val callLog = rememberRuntimePermission(
        Manifest.permission.READ_CALL_LOG,
        requestedBefore = current.callLogPermissionRequested,
        onRequested = { scope.launch { container.settingsRepository.markCallLogRequested() } },
        onResult = { granted -> if (granted) container.syncManager.refresh() },
    )
    val contacts = rememberRuntimePermission(
        Manifest.permission.READ_CONTACTS,
        requestedBefore = current.contactsPermissionRequested,
        onRequested = { scope.launch { container.settingsRepository.markContactsRequested() } },
        // Re-match numbers to names as soon as contacts access is granted.
        onResult = { granted -> if (granted && container.permissions.hasCallLog()) container.syncManager.refresh() },
    )
    var notificationAccess by remember { mutableStateOf(container.permissions.hasNotificationAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationAccess = container.permissions.hasNotificationAccess()
    }
    val permissions = AppPermissions(callLog, contacts, notificationAccess)

    if (!current.onboardingCompleted) {
        Onboarding(container, permissions)
        return
    }
    val nav = rememberNavController()
    AppNavigation(nav, container, current, permissions)
}

@Composable
private fun Onboarding(container: AppContainer, permissions: AppPermissions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    OnboardingScreen(
        step = step,
        callLogStatus = permissions.callLog.status,
        contactsStatus = permissions.contacts.status,
        onNext = { step = OnboardingStep.entries[(step.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)] },
        onRequestCallLog = permissions.callLog::request,
        onRequestContacts = permissions.contacts::request,
        onOpenAppSettings = { SystemSettings.openAppDetails(context) },
        onFinish = {
            scope.launch {
                container.settingsRepository.setOnboardingCompleted(true)
                if (container.permissions.hasCallLog()) container.syncManager.refresh()
            }
        },
    )
}

@Composable
private fun AppNavigation(nav: NavHostController, container: AppContainer, settings: AppSettings, permissions: AppPermissions) {
    val context = LocalContext.current
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm = viewModel { HomeViewModel(container) }
            val state by vm.state.collectAsStateWithLifecycle()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoPane = maxWidth >= 840.dp
                val listWidth = maxWidth * 0.42f
                val actions = HomeActions(
                    onPersonClick = { key -> if (twoPane) selected = key.value else nav.navigate(Routes.person(key)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onRefresh = vm::refresh,
                    onFilterChange = vm::setFilter,
                    onSortChange = vm::setSortOrder,
                    onThresholdChange = vm::setThreshold,
                    onIncludeUnknownChange = vm::setIncludeUnknown,
                    onRequestCallLog = permissions.callLog::request,
                    onRequestContacts = permissions.contacts::request,
                    onOpenAppSettings = { SystemSettings.openAppDetails(context) },
                    onManageWatchList = { nav.navigate(Routes.WATCH_LIST) },
                )
                val selectedKey = selected?.let(PersonKey::parse)
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        Surface(Modifier.width(listWidth).fillMaxHeight()) {
                            HomeScreen(state, permissions.callLog.status, permissions.contacts.status, actions, selectedKey)
                        }
                        VerticalDivider()
                        if (selectedKey != null) PersonPane(container, selectedKey, onBack = null)
                    }
                } else {
                    HomeScreen(state, permissions.callLog.status, permissions.contacts.status, actions)
                }
            }
        }
        composable(Routes.PERSON) { entry ->
            val key = entry.arguments?.getString("key")?.let { PersonKey.parse(Uri.decode(it)) }
            if (key == null) {
                nav.popBackStack()
                return@composable
            }
            PersonPane(container, key, onBack = { nav.popBackStack() })
        }
        composable(Routes.WATCH_LIST) {
            val vm = viewModel { WatchListViewModel(container) }
            val state by vm.state.collectAsStateWithLifecycle()
            WatchListScreen(state, vm::setQuery, { vm.add(it) }, { vm.remove(it) }, { vm.addNumber() }, onBack = { nav.popBackStack() })
        }
        composable(Routes.SEARCH) {
            val vm = viewModel { SearchViewModel(container) }
            val state by vm.state.collectAsStateWithLifecycle()
            SearchScreen(state, vm::setQuery, onPersonClick = { nav.navigate(Routes.person(it)) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val app = context.applicationContext as LastCalledApplication
            val vm = viewModel { SettingsViewModel(app, container) }
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                hasNotificationAccess = permissions.notificationAccess,
                hasCallLog = permissions.callLog.isGranted,
                actions = SettingsActions(
                    onBack = { nav.popBackStack() },
                    onRefresh = vm::refresh,
                    onManagePermissions = { nav.navigate(Routes.ACCESS) },
                    onNotificationDetectionChange = { enabled ->
                        vm.setNotificationDetection(enabled)
                        if (enabled && !permissions.notificationAccess) SystemSettings.openNotificationListenerSettings(context)
                    },
                    onOpenNotificationAccess = { SystemSettings.openNotificationListenerSettings(context) },
                    onThresholdChange = vm::setThreshold,
                    onResetRecords = vm::resetImportedRecords,
                    onClearData = vm::clearAppData,
                ),
            )
        }
        composable(Routes.ACCESS) {
            DataAccessScreen(
                callLog = permissions.callLog.status,
                contacts = permissions.contacts.status,
                notificationAccess = permissions.notificationAccess,
                onRequestCallLog = permissions.callLog::request,
                onRequestContacts = permissions.contacts::request,
                onOpenAppSettings = { SystemSettings.openAppDetails(context) },
                onOpenNotificationAccess = { SystemSettings.openNotificationListenerSettings(context) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun PersonPane(container: AppContainer, key: PersonKey, onBack: (() -> Unit)?) {
    val vm = viewModel(key = "person-${key.value}") { PersonDetailsViewModel(container, key) }
    val state by vm.state.collectAsStateWithLifecycle()
    val history = vm.history.collectAsLazyPagingItems()
    PersonDetailsScreen(state, history, vm::setFilter, onBack, onTrackedChange = { vm.setTracked(it) })
}
