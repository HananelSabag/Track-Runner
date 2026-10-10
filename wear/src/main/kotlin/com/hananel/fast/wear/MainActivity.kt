package com.hananel.fast.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import com.hananel.fast.wear.devicecheck.DeviceCheckRoute
import com.hananel.fast.wear.theme.FastTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FastTheme {
                AppScaffold {
                    DeviceCheckRoute()
                }
            }
        }
    }
}
