package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.SoundglassNav
import com.example.ui.theme.SoundglassTheme
import com.example.ui.viewmodel.AnalyzerViewModel
import com.example.ui.viewmodel.GeneratorViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.MeterViewModel

class MainActivity : ComponentActivity() {

    private val app by lazy { application as SoundglassApp }

    private val meterViewModel: MeterViewModel by viewModels {
        MeterViewModel.Factory(
            captureManager = app.captureManager,
            playbackManager = app.playbackManager,
            sessionRepository = app.sessionRepository,
            preferencesManager = app.preferencesManager
        )
    }

    private val analyzerViewModel: AnalyzerViewModel by viewModels {
        AnalyzerViewModel.Factory(
            captureManager = app.captureManager,
            playbackManager = app.playbackManager,
            preferencesManager = app.preferencesManager
        )
    }

    private val generatorViewModel: GeneratorViewModel by viewModels {
        GeneratorViewModel.Factory(
            playbackManager = app.playbackManager,
            captureManager = app.captureManager
        )
    }

    private val historyViewModel: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(
            sessionRepository = app.sessionRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences by meterViewModel.preferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences?.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }

            SoundglassTheme(darkTheme = isDark) {
                SoundglassNav(
                    meterViewModel = meterViewModel,
                    analyzerViewModel = analyzerViewModel,
                    generatorViewModel = generatorViewModel,
                    historyViewModel = historyViewModel
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.captureManager.setForeground(true)
        app.playbackManager.setForeground(true)
    }

    override fun onStop() {
        app.captureManager.setForeground(false)
        app.playbackManager.setForeground(false)
        meterViewModel.cancelCalibration()
        super.onStop()
    }
}
