package com.hananel.fast.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hananel.fast.wear.devicecheck.DeviceCheckRoute
import com.hananel.fast.wear.home.HomeScreen
import com.hananel.fast.wear.sprint.SprintHistoryRoute
import com.hananel.fast.wear.sprint.SprintTestRoute
import com.hananel.fast.wear.theme.FastTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    AppScaffold {
        // Swiping right goes back, as everywhere on the watch. Each destination owns its
        // view model, so leaving the sprint test releases the GPS receiver.
        SwipeDismissableNavHost(navController = navController, startDestination = Route.HOME) {
            composable(Route.HOME) {
                HomeScreen(
                    onSprintTest = { navController.navigate(Route.SPRINT_TEST) },
                    onSprintHistory = { navController.navigate(Route.SPRINT_HISTORY) },
                    onDeviceCheck = { navController.navigate(Route.DEVICE_CHECK) },
                )
            }
            composable(Route.SPRINT_TEST) { SprintTestRoute() }
            composable(Route.SPRINT_HISTORY) { SprintHistoryRoute() }
            composable(Route.DEVICE_CHECK) { DeviceCheckRoute() }
        }
    }
}

private object Route {
    const val HOME = "home"
    const val SPRINT_TEST = "sprint-test"
    const val SPRINT_HISTORY = "sprint-history"
    const val DEVICE_CHECK = "device-check"
}
