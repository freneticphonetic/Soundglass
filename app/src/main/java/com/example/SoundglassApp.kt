package com.example

import android.app.Application
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.data.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.SessionRepository

class SoundglassApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var captureManager: AudioCaptureManager
        private set

    lateinit var playbackManager: AudioPlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        sessionRepository = SessionRepository(database.sessionDao())
        preferencesManager = PreferencesManager(this)
        captureManager = AudioCaptureManager(this)
        playbackManager = AudioPlaybackManager(this)
    }
}
