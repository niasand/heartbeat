package com.heartratemonitor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartratemonitor.R
import com.heartratemonitor.service.BleHeartRateService
import com.heartratemonitor.service.TimerCountdownService
import com.heartratemonitor.ui.theme.HeartRateMonitorTheme
import com.heartratemonitor.viewmodel.HeartRateViewModel
import com.heartratemonitor.ui.screens.HeartRateScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主Activity
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ViewModel at Activity level so service bindings survive recomposition
    private val viewModel: HeartRateViewModel by viewModels()

    // BLE service binding
    private var bleService: BleHeartRateService? = null
    private var bleBindRequested = false
    private var isBleBound = false
    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bleService = (binder as BleHeartRateService.LocalBinder).getService()
            isBleBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bleService = null
            isBleBound = false
        }
    }

    // Timer service binding
    private var timerService: TimerCountdownService? = null
    private var timerBindRequested = false
    private var isTimerBound = false
    private val timerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            timerService = (binder as TimerCountdownService.LocalBinder).getService()
            isTimerBound = true
            viewModel.bindTimerService(timerService!!)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            timerService = null
            isTimerBound = false
            viewModel.unbindTimerService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartRateMonitorApp(viewModel)
        }
    }

    override fun onStart() {
        super.onStart()

        // Bind BLE service
        if (!bleBindRequested) {
            val intent = Intent(this, BleHeartRateService::class.java)
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
            bleBindRequested = true
        }

        // Bind Timer service
        if (!timerBindRequested) {
            val intent = Intent(this, TimerCountdownService::class.java)
            bindService(intent, timerServiceConnection, Context.BIND_AUTO_CREATE)
            timerBindRequested = true
        }
    }

    override fun onStop() {
        super.onStop()

        // Unbind BLE service
        if (bleBindRequested) {
            try {
                unbindService(bleServiceConnection)
            } catch (_: IllegalArgumentException) {}
            bleBindRequested = false
            isBleBound = false
            bleService = null
        }

        // Unbind Timer service
        if (timerBindRequested) {
            try {
                unbindService(timerServiceConnection)
            } catch (_: IllegalArgumentException) {}
            timerBindRequested = false
            isTimerBound = false
            timerService = null
            viewModel.unbindTimerService()
        }
    }
}

@Composable
fun HeartRateMonitorApp(viewModel: HeartRateViewModel) {
    val themeColor by viewModel.themeColor.collectAsState()

    HeartRateMonitorTheme(themeColor = themeColor) {
        HeartRateScreen(viewModel = viewModel)
    }
}
