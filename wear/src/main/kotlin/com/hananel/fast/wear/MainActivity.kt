package com.hananel.fast.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
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
import com.hananel.fast.wear.settings.AboutRoute
import com.hananel.fast.wear.settings.EditAgeRoute
import com.hananel.fast.wear.settings.SettingsRoute
import com.hananel.fast.wear.sprint.SprintSession
import com.hananel.fast.wear.sprint.SprintTestRoute
import com.hananel.fast.wear.sprint.SprintUiState
import com.hananel.fast.wear.sync.SyncEngine
import com.hananel.fast.wear.theme.FastTheme
import com.hananel.fast.wear.ui.Ambient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Present only while something is being measured; see [stayInFrontWhileMeasuring]. */
    private var ambientObserver: AmbientLifecycleObserver? = null

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            Log.i(AMBIENT_TAG, "dimmed, staying in front")
            Ambient.set(true)
        }

        override fun onExitAmbient() {
            Log.i(AMBIENT_TAG, "lit again")
            Ambient.set(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stayInFrontWhileMeasuring()
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

    /**
     * When the watch dims itself — the wrist drops, a palm covers it — an ordinary app
     * gives way to the watch face. While a training is recorded or a speed test is
     * running, this one stays: the system keeps it in front in its low-power mode and
     * the screens show a plain readout (see [Ambient]). Raising the wrist is then
     * enough to see the speed.
     *
     * Only while measuring. Otherwise the menu would sit on a dimmed display for as
     * long as the watch lay on a table.
     */
    private fun stayInFrontWhileMeasuring() {
        lifecycleScope.launch {
            combine(RecordingSession.state, SprintSession.state) { recording, test ->
                recording is RecordingUiState.Recording || test is SprintUiState.Countdown || test is SprintUiState.Running
            }.distinctUntilChanged().collect { measuring ->
                if (measuring) attachAmbient() else detachAmbient()
            }
        }
    }

    private fun attachAmbient() {
        if (ambientObserver != null) return
        try {
            ambientObserver = AmbientLifecycleObserver(this, ambientCallback).also(lifecycle::addObserver)
            Log.i(AMBIENT_TAG, "measuring: the app will stay in front when the watch dims")
        } catch (e: RuntimeException) {
            // A watch without the ambient service simply behaves as before.
            Log.w(AMBIENT_TAG, "Ambient mode is not available", e)
        }
    }

    private fun detachAmbient() {
        val observer = ambientObserver ?: return
        ambientObserver = null
        lifecycle.removeObserver(observer)
        // Removing an observer does not tell it the activity has gone anywhere, and it would go on
        // holding the app in front. It is walked through the rest of a lifecycle by hand instead.
        val state = lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.RESUMED)) observer.onPause(this)
        if (state.isAtLeast(Lifecycle.State.STARTED)) observer.onStop(this)
        observer.onDestroy(this)
        Ambient.set(false)
        Log.i(AMBIENT_TAG, "not measuring: back to an ordinary app")
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
                        onSpeedTest = { navController.navigate(Route.SPRINT_TEST) },
                        onHistory = { navController.navigate(Route.HISTORY) },
                        onSettings = { navController.navigate(Route.SETTINGS) },
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
            composable(Route.SETTINGS) {
                SettingsRoute(
                    onEditAge = { navController.navigate(Route.EDIT_AGE) },
                    onAbout = { navController.navigate(Route.ABOUT) },
                )
            }
            composable(Route.ABOUT) { AboutRoute(onDeviceCheck = { navController.navigate(Route.DEVICE_CHECK) }) }
            composable(Route.EDIT_AGE) { EditAgeRoute(onDone = { navController.popBackStack() }) }
            composable(Route.DEVICE_CHECK) { DeviceCheckRoute() }
        }
    }
}

private const val AMBIENT_TAG = "FastAmbient"

private object Route {
    const val ARG_KIND = "kind"
    const val ARG_ID = "id"

    const val HOME = "home"
    const val RECORD = "record/{$ARG_KIND}"
    const val HISTORY = "history"
    const val SESSION = "session/{$ARG_ID}"
    const val SPRINT_TEST = "sprint-test"
    const val SETTINGS = "settings"
    const val EDIT_AGE = "settings/age"
    const val ABOUT = "settings/about"
    const val DEVICE_CHECK = "device-check"

    fun record(kind: ActivityKind) = "record/${kind.name}"

    fun session(id: String) = "session/$id"
}
