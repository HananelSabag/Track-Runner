package com.hananel.fast.wear.record

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.gnss.AcquisitionTracker
import com.hananel.fast.wear.sensors.hasHeartRatePermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface RecordScreenState {
    /** [denied] is true once the request has been turned down. */
    data class NeedsPermission(val denied: Boolean) : RecordScreenState

    /** Asked once, before the first football session, to estimate the maximum heart rate. */
    data object AskAge : RecordScreenState

    data class Session(val state: RecordingUiState) : RecordScreenState
}

/**
 * The recording screen's link to the training, which itself lives in
 * [RecordingSession] so that it survives the display going off.
 *
 * Before a session can start, this decides what still stands in the way: a
 * permission, or the one-time age question.
 */
class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private enum class Gate { PERMISSION, PERMISSION_DENIED, AGE, OPEN }

    private val settings = WearData.settings(application)
    private val gate = MutableStateFlow(Gate.PERMISSION)
    private var kind = ActivityKind.RUN

    val state: StateFlow<RecordScreenState> =
        combine(gate, RecordingSession.state) { gate, session ->
            when {
                // A session already under way is shown whatever else is true.
                session is RecordingUiState.Recording || session is RecordingUiState.Finished || session is RecordingUiState.TimedOut ->
                    RecordScreenState.Session(session)
                gate == Gate.PERMISSION -> RecordScreenState.NeedsPermission(denied = false)
                gate == Gate.PERMISSION_DENIED -> RecordScreenState.NeedsPermission(denied = true)
                gate == Gate.AGE -> RecordScreenState.AskAge
                // The service is starting; show an empty search rather than a blank screen.
                session is RecordingUiState.Idle ->
                    RecordScreenState.Session(RecordingUiState.Locating(kind, AcquisitionTracker.NOTHING_YET, 0))
                else -> RecordScreenState.Session(session)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecordScreenState.NeedsPermission(denied = false))

    /** Called when the screen opens. */
    fun enter(kind: ActivityKind) {
        this.kind = kind
        if (RecordingSession.isRecording) return
        val context = getApplication<Application>()
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        // Heart rate is asked for once; the training goes ahead without it if refused.
        if (hasLocation && (hasHeartRatePermission(context) || settings.sensorsAsked)) proceed() else gate.value = Gate.PERMISSION
    }

    fun onPermissionResult(locationGranted: Boolean) {
        settings.sensorsAsked = true
        if (locationGranted) proceed() else gate.value = Gate.PERMISSION_DENIED
    }

    /** [age] is null when the player skipped the question. */
    fun onAge(age: Int?) {
        settings.ageAsked = true
        if (age != null) settings.ageYears = age
        proceed()
    }

    fun start() = RecordingSession.start()

    fun finish() = RecordingSession.finish()

    fun dismiss() = RecordingSession.dismiss()

    /** After a timed-out search: switch the receiver on and search again. */
    fun searchAgain() = proceed()

    private fun proceed() {
        if (kind == ActivityKind.FOOTBALL && !settings.ageAsked) {
            gate.value = Gate.AGE
            return
        }
        gate.value = Gate.OPEN
        RecordingService.start(getApplication<Application>(), kind)
    }

    /**
     * Reached when the screen is swiped away. A warm-up nobody started is
     * abandoned and the GPS released; a recording carries on without the screen.
     */
    override fun onCleared() {
        when (RecordingSession.state.value) {
            is RecordingUiState.Locating -> RecordingService.stop(getApplication<Application>())
            is RecordingUiState.Finished, is RecordingUiState.TimedOut -> RecordingSession.dismiss()
            else -> Unit
        }
    }
}
