package com.hananel.fast.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hananel.fast.mobile.data.PhoneData
import com.hananel.fast.mobile.demo.DemoSessions
import com.hananel.fast.mobile.devicecheck.DeviceCheckRoute
import com.hananel.fast.mobile.home.HomeScreen
import com.hananel.fast.mobile.replay.ReplayRoute
import com.hananel.fast.mobile.session.SessionRoute
import com.hananel.fast.mobile.settings.SettingsScreen
import com.hananel.fast.mobile.sync.PhoneSync
import com.hananel.fast.mobile.theme.FastTheme
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
        setContent {
            FastTheme {
                FastApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Each time the app comes forward: take in whatever the watch offered meanwhile.
        PhoneSync.onAppOpened(applicationContext)
    }
}

/**
 * Five screens and a back stack. Routes are plain strings so the stack survives
 * the activity being recreated: "home", "session/<id>", "replay/<id>", "settings",
 * "device-check".
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
        AnimatedContent(
            targetState = backStack.last(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { route ->
            when {
                route == HOME -> HomeScreen(
                    library = library,
                    watch = watch,
                    onOpen = { open(SESSION + it) },
                    onSettings = { open(SETTINGS) },
                    onLoadDemo = if (DemoSessions.AVAILABLE) {
                        { scope.launch { withContext(Dispatchers.IO) { DemoSessions.add(context) } } }
                    } else {
                        null
                    },
                )
                route.startsWith(SESSION) -> {
                    val id = route.removePrefix(SESSION)
                    SessionRoute(id, onBack = ::back, onReplay = { open(REPLAY + id) })
                }
                route.startsWith(REPLAY) -> ReplayRoute(route.removePrefix(REPLAY), onBack = ::back)
                route == SETTINGS -> SettingsScreen(library, watch, onBack = ::back, onDeviceCheck = { open(DEVICE_CHECK) })
                route == DEVICE_CHECK -> DeviceCheckRoute()
            }
        }
    }
}

private const val HOME = "home"
private const val SESSION = "session/"
private const val REPLAY = "replay/"
private const val SETTINGS = "settings"
private const val DEVICE_CHECK = "device-check"
