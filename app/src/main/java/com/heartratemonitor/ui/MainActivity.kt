package com.heartratemonitor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartratemonitor.R
import com.heartratemonitor.service.BleHeartRateService
import com.heartratemonitor.ui.theme.HeartRateMonitorTheme
import com.heartratemonitor.viewmodel.HeartRateViewModel
import com.heartratemonitor.ui.screens.HeartRateScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var service: BleHeartRateService? = null
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as BleHeartRateService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartRateMonitorApp()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, BleHeartRateService::class.java)
        isBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Service was already destroyed by the system
            }
            isBound = false
            service = null
        }
    }
}

@Composable
fun HeartRateMonitorApp(viewModel: HeartRateViewModel = viewModel()) {
    val themeColor by viewModel.themeColor.collectAsState()

    HeartRateMonitorTheme(themeColor = themeColor) {
        HeartRateScreen(viewModel)
    }
}
