package com.hananel.fast.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.devicecheck.DeviceCheckRoute
import com.hananel.fast.wear.history.HistoryRoute
import com.hananel.fast.wear.history.SessionDetailRoute
import com.hananel.fast.wear.home.HomeActions
import com.hananel.fast.wear.home.HomeScreen
import com.hananel.fast.wear.record.RecordRoute
import com.hananel.fast.wear.record.RecordingSession
import com.hananel.fast.wear.record.RecordingUiState
import com.hananel.fast.wear.settings.EditAgeRoute
import com.hananel.fast.wear.settings.SettingsRoute
import com.hananel.fast.wear.sprint.SprintHistoryRoute
import com.hananel.fast.wear.sprint.SprintTestRoute
import com.hananel.fast.wear.sync.SyncEngine
import com.hananel.fast.wear.theme.FastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            // A recording whose process died is adopted here, then everything unconfirmed is offered to the phone.
            WearData.store(applicationContext).recoverOrphans(RecordingSession.activeId)
            SyncEngine.syncPending(applicationContext)
        }
        setContent {
            FastTheme {
                FastWearApp()
            }
        }
    }
}

@Composable
private fun FastWearApp() {
    val navController = rememberSwipeDismissableNavController()
    val recording by RecordingSession.state.collectAsStateWithLifecycle()
    val recordingKind = (recording as? RecordingUiState.Recording)?.kind

    // Opened from the watch face while a training is being recorded: go straight back to it.
    // If the recording screen is already on top (a restored activity), it is not opened twice.
    LaunchedEffect(Unit) {
        (RecordingSession.state.value as? RecordingUiState.Recording)?.let {
            navController.navigate(Route.record(it.kind)) { launchSingleTop = true }
        }
    }

    AppScaffold {
        // Swiping right goes back, as everywhere on the watch. Each destination owns its view
        // model, so leaving a screen that only warmed the GPS up releases the receiver.
        SwipeDismissableNavHost(navController = navController, startDestination = Route.HOME) {
            composable(Route.HOME) {
                HomeScreen(
                    recording = recordingKind,
                    actions = HomeActions(
                        onRecord = { navController.navigate(Route.record(it)) },
                        onResume = { recordingKind?.let { navController.navigate(Route.record(it)) } },
                        onSprintTest = { navController.navigate(Route.SPRINT_TEST) },
                        onHistory = { navController.navigate(Route.HISTORY) },
                        onSprintHistory = { navController.navigate(Route.SPRINT_HISTORY) },
                        onSettings = { navController.navigate(Route.SETTINGS) },
                        onDeviceCheck = { navController.navigate(Route.DEVICE_CHECK) },
                    ),
                )
            }
            composable(Route.RECORD) { entry ->
                val kind = entry.arguments?.getString(Route.ARG_KIND)
                    ?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } }
                    ?: ActivityKind.RUN
                RecordRoute(kind, onDone = { navController.popBackStack(Route.HOME, inclusive = false) })
            }
            composable(Route.HISTORY) { HistoryRoute(onOpen = { navController.navigate(Route.session(it)) }) }
            composable(Route.SESSION) { entry -> SessionDetailRoute(entry.arguments?.getString(Route.ARG_ID).orEmpty()) }
            composable(Route.SPRINT_TEST) { SprintTestRoute() }
            composable(Route.SPRINT_HISTORY) { SprintHistoryRoute() }
            composable(Route.SETTINGS) { SettingsRoute(onEditAge = { navController.navigate(Route.EDIT_AGE) }) }
            composable(Route.EDIT_AGE) { EditAgeRoute(onDone = { navController.popBackStack() }) }
            composable(Route.DEVICE_CHECK) { DeviceCheckRoute() }
        }
    }
}

private object Route {
    const val ARG_KIND = "kind"
    const val ARG_ID = "id"

    const val HOME = "home"
    const val RECORD = "record/{$ARG_KIND}"
    const val HISTORY = "history"
    const val SESSION = "session/{$ARG_ID}"
    const val SPRINT_TEST = "sprint-test"
    const val SPRINT_HISTORY = "sprint-history"
    const val SETTINGS = "settings"
    const val EDIT_AGE = "settings/age"
    const val DEVICE_CHECK = "device-check"

    fun record(kind: ActivityKind) = "record/${kind.name}"

    fun session(id: String) = "session/$id"
}
