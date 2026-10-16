package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.dao.SessionDao
import com.example.data.entity.SavedSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveSession() = runBlocking {
        val session = SavedSession(
            title = "Studio Acoustic Check",
            notes = "Near monitor speaker",
            timestampMillis = 1700000000000L,
            durationMillis = 15000L,
            sampleRate = 48000,
            captureSource = "UNPROCESSED",
            inputRoute = "Built-in Microphone",
            timeWeighting = "FAST",
            frequencyWeighting = "A",
            isCalibrated = true,
            calibrationOffsetDb = 40.0f,
            minLevelDb = 42.1f,
            maxLevelDb = 78.4f,
            leqDb = 65.2f,
            peakDbFS = -6.1f,
            clippingOccurred = false,
            unit = "dB SPL",
            timeSeriesJson = "[]"
        )

        val id = dao.insertSession(session)
        assertTrue(id > 0)

        val list = dao.getAllSessions().first()
        assertEquals(1, list.size)
        assertEquals("Studio Acoustic Check", list[0].title)
        assertEquals(65.2f, list[0].leqDb, 0.01f)

        // Update
        val updated = list[0].copy(title = "Studio Check Updated")
        dao.updateSession(updated)

        val retrieved = dao.getSessionById(id).first()
        assertNotNull(retrieved)
        assertEquals("Studio Check Updated", retrieved!!.title)

        // Delete
        dao.deleteSessionById(id)
        val afterDelete = dao.getAllSessions().first()
        assertTrue(afterDelete.isEmpty())
    }
}
