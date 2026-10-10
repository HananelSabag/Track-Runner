package com.hananel.fast.wear.devicecheck

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
            val report = withContext(Dispatchers.Default) { WatchProbe.report(context) }
            // A watch has no easy copy/paste, so the report is also readable with `adb logcat -s FastProbe`.
            Log.i(TAG, "\n" + report.toLogText())
            _sections.value = report
        }
    }

    private companion object {
        const val TAG = "FastProbe"
    }
}
