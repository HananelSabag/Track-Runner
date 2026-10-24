package com.hananel.fast.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hananel.fast.mobile.data.PhoneData
import com.hananel.fast.mobile.demo.DemoSessions
import com.hananel.fast.mobile.devicecheck.DeviceCheckRoute
import com.hananel.fast.mobile.home.HomeScreen
import com.hananel.fast.mobile.reference.ReferenceRecorder
import com.hananel.fast.mobile.reference.ReferenceScreen
import com.hananel.fast.mobile.reference.ReferenceService
import com.hananel.fast.mobile.replay.ReplayRoute
import com.hananel.fast.mobile.session.SessionRoute
import com.hananel.fast.mobile.settings.SettingsScreen
import com.hananel.fast.mobile.sync.PhoneSync
import com.hananel.fast.mobile.theme.FastTheme
import com.hananel.fast.mobile.ui.screenTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so the system bars always carry light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PhoneData.refresh(applicationContext)
        loadDemoIfAsked()
        setContent {
            FastTheme {
                FastApp()
            }
        }
    }

    /**
     * "Measure together with the watch" was left on, but the system ended the service
     * that waits for the watch. It can only be started while the app is in front: now.
     */
    private fun rearmMeasuringWithWatch() {
        val wanted = PhoneData.settings(applicationContext).measureWithWatch
        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (wanted && allowed && !ReferenceRecorder.armed.value) ReferenceService.arm(applicationContext)
    }

    /**
     * Development only, and deliberately without a button: `am start -n <activity> --es demo add`
     * fills the history with invented sessions so the screens can be looked at on an emulator,
     * and `--es demo remove` takes them away again. Release builds ignore it.
     */
    private fun loadDemoIfAsked() {
        if (!DemoSessions.AVAILABLE) return
        val wanted = intent?.getStringExtra(EXTRA_DEMO) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            if (wanted == DEMO_REMOVE) DemoSessions.remove(applicationContext) else DemoSessions.add(applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        rearmMeasuringWithWatch()
        // Each time the app comes forward: take in whatever the watch offered meanwhile.
        PhoneSync.onAppOpened(applicationContext)
    }
}

/**
 * Five screens and a back stack. Routes are plain strings so the stack survives
 * the activity being recreated: "home", "session/<id>", "replay/<id>", "settings",
 * "device-check", "reference".
 */
@Composable
private fun FastApp() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val library by PhoneData.library.collectAsStateWithLifecycle()
    val watch by PhoneSync.watch.collectAsStateWithLifecycle()
    val backStack: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableListOf(HOME).toMutableStateList() }

    fun open(route: String) = backStack.add(route)
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    BackHandler(enabled = backStack.size > 1) { back() }

    // A Surface, not a plain Box: it is what gives text its default colour on the dark background.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedContent(
            targetState = backStack.last(),
            transitionSpec = { screenTransition(forward = depth(targetState) >= depth(initialState), rtl = rtl) },
            label = "screen",
        ) { route ->
            when {
                route == HOME -> HomeScreen(
                    library = library,
                    watch = watch,
                    onOpen = { open(SESSION + it) },
                    onSettings = { open(SETTINGS) },
                )
                route.startsWith(SESSION) -> {
                    val id = route.removePrefix(SESSION)
                    SessionRoute(id, onBack = ::back, onReplay = { open(REPLAY + id) })
                }
                route.startsWith(REPLAY) -> ReplayRoute(route.removePrefix(REPLAY), onBack = ::back)
                route == SETTINGS -> SettingsScreen(
                    library = library,
                    watch = watch,
                    onBack = ::back,
                    onDeviceCheck = { open(DEVICE_CHECK) },
                    onReference = { open(REFERENCE) },
                )
                route == REFERENCE -> ReferenceScreen(onBack = ::back)
                route == DEVICE_CHECK -> DeviceCheckRoute()
            }
        }
    }
}

/** How far into the app a screen lies, which tells a move forward from a move back. */
private fun depth(route: String): Int = when {
    route == HOME -> 0
    route.startsWith(SESSION) || route == SETTINGS -> 1
    else -> 2
}

private const val HOME = "home"
private const val SESSION = "session/"
private const val REPLAY = "replay/"
private const val SETTINGS = "settings"
private const val DEVICE_CHECK = "device-check"
private const val REFERENCE = "reference"
private const val EXTRA_DEMO = "demo"
private const val DEMO_REMOVE = "remove"
