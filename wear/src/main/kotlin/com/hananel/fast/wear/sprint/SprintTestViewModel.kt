package com.hananel.fast.wear.sprint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The sprint screen's link to the test, which itself lives in [SprintSession]
 * so that it survives the display going off.
 *
 * The session starts when the runner opens the screen with permission granted
 * and ends when they leave the screen — not when the screen merely stops
 * showing.
 */
class SprintTestViewModel(application: Application) : AndroidViewModel(application) {

    /** Null until the runner has been asked. */
    private val locationGranted = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<SprintUiState> =
        combine(locationGranted, SprintSession.state) { granted, session ->
            when (granted) {
                true -> session
                false -> SprintUiState.NeedsPermission(denied = true)
                null -> SprintUiState.NeedsPermission(denied = false)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SprintUiState.NeedsPermission(denied = false))

    fun onPermissionResult(granted: Boolean) {
        locationGranted.value = granted
        if (granted) SprintService.start(getApplication<Application>())
    }

    fun start() = SprintSession.start()

    /** After a timeout: switch the receiver on and search again. */
    fun searchAgain() = SprintService.start(getApplication<Application>())

    fun stop() = SprintSession.stop()

    fun retry() = SprintSession.retry()

    /** Reached when the runner swipes the sprint screen away, which is the moment to release the GPS. */
    override fun onCleared() {
        SprintService.stop(getApplication<Application>())
    }
}
