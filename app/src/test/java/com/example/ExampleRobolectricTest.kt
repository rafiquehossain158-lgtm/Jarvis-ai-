package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.JarvisRepository
import com.example.data.db.ChatMessageEntity
import com.example.data.db.JarvisDatabase
import com.example.data.prefs.AppLanguage
import com.example.data.prefs.JarvisSettings
import com.example.device.DeviceTelemetryManager
import com.example.i18n.StringsCatalog
import com.example.ui.screens.CoreChatScreen
import com.example.ui.theme.JarvisAiTheme
import com.example.viewmodel.AiUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase
    private lateinit var repository: JarvisRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JarvisRepository(
            chatMessageDao = db.chatMessageDao(),
            noteDao = db.noteDao(),
            reminderDao = db.reminderDao(),
            alarmDao = db.alarmDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis AI", appName)
    }

    @Test
    fun `room repository persists chat notes reminders and alarms locally`() = runBlocking {
        repository.addChatMessage(ChatMessageEntity(role = "user", content = "Hello Jarvis"))
        repository.addNote("Mission Log", "Calibrate Mark 85")
        repository.addReminder("Check telemetry", System.currentTimeMillis() + 60_000L)
        repository.addAlarm(7, 30, "Morning Brief")

        assertEquals(1, repository.chatMessages.first().size)
        assertEquals(1, repository.notes.first().size)
        assertEquals(1, repository.reminders.first().size)
        assertEquals(1, repository.alarms.first().size)

        repository.purgeAllData()
        assertTrue(repository.chatMessages.first().isEmpty())
        assertTrue(repository.notes.first().isEmpty())
        assertTrue(repository.reminders.first().isEmpty())
        assertTrue(repository.alarms.first().isEmpty())
    }

    @Test
    fun `multilingual strings catalog supports english bengali and hindi`() {
        val en = StringsCatalog.get(AppLanguage.ENGLISH)
        val bn = StringsCatalog.get(AppLanguage.BENGALI)
        val hi = StringsCatalog.get(AppLanguage.HINDI)

        assertEquals("CORE ONLINE", en.coreOnline)
        assertEquals("কোর সক্রিয়", bn.coreOnline)
        assertEquals("कोर ऑनलाइन", hi.coreOnline)
    }

    @Test
    fun `device telemetry manager captures valid battery and model info`() {
        val telemetry = DeviceTelemetryManager(context).captureCurrentTelemetry()
        assertTrue(telemetry.batteryPercent in 0..100)
        assertTrue(telemetry.deviceModel.isNotBlank())
    }

    @Test
    fun `core chat screen renders input field and mic button`() {
        composeTestRule.setContent {
            JarvisAiTheme {
                CoreChatScreen(
                    strings = StringsCatalog.get(AppLanguage.ENGLISH),
                    settings = JarvisSettings(),
                    messages = emptyList(),
                    aiUiState = AiUiState.Idle,
                    isListening = false,
                    isSpeaking = false,
                    rmsLevel = 0f,
                    partialTranscript = "",
                    voiceError = null,
                    onSendMessage = {},
                    onMicClick = {},
                    onStopSpeaking = {},
                    onSpeakMessage = {},
                    onClearChat = {},
                    onDismissVoiceError = {},
                    onDismissAiError = {},
                    onCopiedNotice = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_message_list").assertIsDisplayed()
    }
}
