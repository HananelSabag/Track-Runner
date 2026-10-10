package com.hananel.fast.mobile.devicecheck

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hananel.fast.core.diagnostics.DeviceProbe
import com.hananel.fast.core.diagnostics.ReportSection
import com.hananel.fast.core.diagnostics.toLogText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val _sections = MutableStateFlow<List<ReportSection>?>(null)

    /** Null while the report is still being read. */
    val sections: StateFlow<List<ReportSection>?> = _sections.asStateFlow()

    init {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val report = withContext(Dispatchers.Default) {
                listOf(
                    DeviceProbe.device(context),
                    DeviceProbe.gnss(context),
                    DeviceProbe.motionSensors(context),
                    PhoneProbe.pairedDevices(context),
                )
            }
            Log.i(TAG, "\n" + report.toLogText())
            _sections.value = report
        }
    }

    private companion object {
        const val TAG = "FastProbe"
    }
}
