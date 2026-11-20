package com.example.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiStreamEvent
import com.example.ai.CommandParser
import com.example.ai.FirebaseJarvisAiRepository
import com.example.ai.JarvisAction
import com.example.ai.JarvisAiRepository
import com.example.data.JarvisRepository
import com.example.data.db.AlarmEntity
import com.example.data.db.ChatMessageEntity
import com.example.data.db.JarvisDatabase
import com.example.data.db.NoteEntity
import com.example.data.db.ReminderEntity
import com.example.data.prefs.AppLanguage
import com.example.data.prefs.HudTheme
import com.example.data.prefs.JarvisSettings
import com.example.data.prefs.SettingsRepository
import com.example.device.AppLauncherManager
import com.example.device.DeviceTelemetry
import com.example.device.DeviceTelemetryManager
import com.example.device.InstalledAppInfo
import com.example.device.LaunchResult
import com.example.device.ReminderScheduler
import com.example.device.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "JarvisViewModel"

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Thinking : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

data class PermissionStatus(
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = true
)

class JarvisViewModel(
    private val appContext: Context,
    private val repository: JarvisRepository,
    private val settingsRepository: SettingsRepository,
    private val aiRepository: JarvisAiRepository,
    private val telemetryManager: DeviceTelemetryManager,
    private val appLauncher: AppLauncherManager,
    private val reminderScheduler: ReminderScheduler,
    private val voiceEngine: VoiceEngine
) : ViewModel() {

    val settings: StateFlow<JarvisSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JarvisSettings())

    private val _ephemeralMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = combine(
        repository.chatMessages,
        _ephemeralMessages,
        settings
    ) { dbMessages, ephemeral, currentSettings ->
        if (currentSettings.saveChatHistory) dbMessages else ephemeral
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alarms: StateFlow<List<AlarmEntity>> = repository.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<DeviceTelemetry> = telemetryManager.observeTelemetry()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            telemetryManager.captureCurrentTelemetry()
        )

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiUiState: StateFlow<AiUiState> = _aiUiState.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val rmsLevel: StateFlow<Float> = voiceEngine.rmsLevel
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val voiceError: StateFlow<String?> = voiceEngine.voiceError

    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var activeGenerationJob: Job? = null

    init {
        refreshPermissions()
        refreshInstalledApps()
        viewModelScope.launch {
            settings.collect { s ->
                voiceEngine.updateSettings(s.language, s.speechRate, s.voicePitch)
            }
        }
    }

    fun refreshPermissions() {
        val micGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _permissionStatus.value = PermissionStatus(
            microphoneGranted = micGranted,
            notificationsGranted = notifGranted
        )
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                appLauncher.loadLaunchableApps()
            }
            _installedApps.value = apps
        }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val currentSettings = settings.first()
            val lang = currentSettings.language

            appendChatMessage(
                ChatMessageEntity(
                    role = "user",
                    content = prompt,
                    languageCode = lang.code
                ),
                persist = currentSettings.saveChatHistory
            )

            // Instant local device command path when matched or in Local-Only Privacy Mode
            val directAction = CommandParser.detectDirectLocalAction(prompt)
            if (directAction != null) {
                val execution = executeAction(directAction, lang)
                val reply = execution.replyMessage
                appendChatMessage(
                    ChatMessageEntity(
                        role = "jarvis",
                        content = reply,
                        languageCode = lang.code,
                        actionBadge = execution.badge
                    ),
                    persist = currentSettings.saveChatHistory
                )
                _aiUiState.value = AiUiState.Idle
                if (currentSettings.autoSpeakResponses) {
                    voiceEngine.speak(reply)
                }
                return@launch
            }

            if (currentSettings.localOnlyPrivacyMode) {
                val offlineReply = when (lang) {
                    AppLanguage.BENGALI -> "লোকাল প্রাইভেসি শিল্ড সক্রিয় আছে। অফলাইনে আমি নোট, রিমাইন্ডার, অ্যালার্ম, ব্যাটারি তথ্য, ক্যামেরা, সেটিংস, ম্যাপস বা যেকোনো অ্যাপ খুলতে পারি।"
                    AppLanguage.HINDI -> "लोकल प्राइवेसी शील्ड सक्रिय है। ऑफलाइन मोड में मैं नोट्स, रिमाइंडर, अलार्म, बैटरी स्थिति, कैमरा, सेटिंग्स, मैप्स और ऐप्स खोल सकता हूँ।"
                    AppLanguage.ENGLISH -> "Local-Only Privacy Shield is active. Offline commands ready: take a note, set a reminder or alarm, check battery/device status, or open Camera, Settings, Maps, or installed apps."
                }
                appendChatMessage(
                    ChatMessageEntity(
                        role = "jarvis",
                        content = offlineReply,
                        languageCode = lang.code,
                        actionBadge = "LOCAL SHIELD"
                    ),
                    persist = currentSettings.saveChatHistory
                )
                _aiUiState.value = AiUiState.Idle
                if (currentSettings.autoSpeakResponses) {
                    voiceEngine.speak(offlineReply)
                }
                return@launch
            }

            // Cloud Gemini AI path via Firebase AI Logic
            _aiUiState.value = AiUiState.Thinking
            try {
                val historySnapshot = chatMessages.value.dropLast(1)
                val telemetrySnapshot = if (currentSettings.shareTelemetryWithAi) telemetry.value else null
                val appNames = _installedApps.value.map { it.label }

                var completedText = ""
                var parsedAction: JarvisAction? = null

                aiRepository.streamReply(
                    userPrompt = prompt,
                    history = historySnapshot,
                    language = lang,
                    telemetry = telemetrySnapshot,
                    installedAppNames = appNames
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            _aiUiState.value = AiUiState.Streaming(event.accumulatedText)
                        }
                        is AiStreamEvent.Completed -> {
                            completedText = event.finalOutput.cleanText
                            parsedAction = event.finalOutput.action
                        }
                    }
                }

                var actionBadge: String? = null
                if (parsedAction != null) {
                    val exec = executeAction(parsedAction!!, lang)
                    actionBadge = exec.badge
                    if (completedText.isBlank()) {
                        completedText = exec.replyMessage
                    }
                }

                if (completedText.isBlank()) {
                    throw IllegalStateException("Empty response from Gemini.")
                }

                appendChatMessage(
                    ChatMessageEntity(
                        role = "jarvis",
                        content = completedText,
                        languageCode = lang.code,
                        actionBadge = actionBadge
                    ),
                    persist = currentSettings.saveChatHistory
                )
                _aiUiState.value = AiUiState.Idle
                if (currentSettings.autoSpeakResponses) {
                    voiceEngine.speak(completedText)
                }
            } catch (e: CancellationException) {
                _aiUiState.value = AiUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                val errMsg = e.localizedMessage ?: "AI link error. Please verify network connectivity."
                _aiUiState.value = AiUiState.Error(errMsg)
                appendChatMessage(
                    ChatMessageEntity(
                        role = "jarvis",
                        content = errMsg,
                        languageCode = lang.code,
                        isError = true
                    ),
                    persist = currentSettings.saveChatHistory
                )
            }
        }
    }

    fun dismissAiError() {
        if (_aiUiState.value is AiUiState.Error) {
            _aiUiState.value = AiUiState.Idle
        }
    }

    fun toggleVoiceListening() {
        if (isListening.value) {
            voiceEngine.stopListening()
        } else {
            refreshPermissions()
            if (!_permissionStatus.value.microphoneGranted) {
                _events.tryEmit("Microphone permission is required for voice commands.")
                return
            }
            voiceEngine.startListening(settings.value.language) { transcript ->
                sendMessage(transcript)
            }
        }
    }

    fun speakText(text: String) {
        voiceEngine.speak(text)
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun clearVoiceError() {
        voiceEngine.clearError()
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            _ephemeralMessages.value = emptyList()
            repository.clearChatHistory()
            _events.tryEmit("Chat feed cleared.")
        }
    }

    // Notes, Reminders, Alarms CRUD
    fun createNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            val finalTitle = title.ifBlank { content.take(32) }
            repository.addNote(finalTitle, content.ifBlank { finalTitle })
            _events.tryEmit("Note saved.")
        }
    }

    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch { repository.togglePinNote(note) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
    }

    fun createReminder(title: String, delayMinutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
            val id = repository.addReminder(title, triggerAt)
            reminderScheduler.scheduleReminder(id, title, triggerAt)
            _events.tryEmit("Reminder scheduled in $delayMinutes min.")
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.toggleReminderComplete(reminder)
            if (!reminder.isCompleted) {
                reminderScheduler.cancelReminder(reminder.id)
            }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            reminderScheduler.cancelReminder(id)
            repository.deleteReminder(id)
        }
    }

    fun createAlarm(hour: Int, minute: Int, label: String, syncSystemClock: Boolean) {
        viewModelScope.launch {
            val cleanLabel = label.ifBlank { "Jarvis Alarm" }
            repository.addAlarm(hour, minute, cleanLabel)
            if (syncSystemClock) {
                val launched = reminderScheduler.launchSystemAlarmIntent(hour, minute, cleanLabel)
                if (!launched) {
                    _events.tryEmit("Alarm saved locally (system clock app unavailable).")
                    return@launch
                }
            }
            _events.tryEmit("Alarm configured for ${CommandParser.formatAlarmTime(hour, minute)}.")
        }
    }

    fun toggleAlarm(alarm: AlarmEntity) {
        viewModelScope.launch { repository.toggleAlarm(alarm) }
    }

    fun deleteAlarm(id: Long) {
        viewModelScope.launch { repository.deleteAlarm(id) }
    }

    // App & Hardware Intent Launchers
    fun launchInstalledApp(app: InstalledAppInfo) {
        handleLaunchResult(appLauncher.launchAppByPackage(app.packageName, app.label))
    }

    fun openCamera() = handleLaunchResult(appLauncher.openCamera())
    fun openSettings() = handleLaunchResult(appLauncher.openSettings())
    fun openWifiSettings() = handleLaunchResult(appLauncher.openWifiSettings())
    fun openAppPermissionsSettings() = handleLaunchResult(appLauncher.openAppPermissionsSettings())
    fun openMaps(query: String = "") = handleLaunchResult(appLauncher.openMaps(query))

    private fun handleLaunchResult(result: LaunchResult) {
        when (result) {
            is LaunchResult.Launched -> _events.tryEmit("Launching ${result.targetName}...")
            is LaunchResult.NotFound -> _events.tryEmit("App not found: ${result.query}")
            is LaunchResult.Failed -> _events.tryEmit(result.reason)
        }
    }

    // Settings updates
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setHudTheme(theme: HudTheme) {
        viewModelScope.launch { settingsRepository.setHudTheme(theme) }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSpeak(enabled) }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { settingsRepository.setSpeechRate(rate) }
    }

    fun setVoicePitch(pitch: Float) {
        viewModelScope.launch { settingsRepository.setVoicePitch(pitch) }
    }

    fun setSaveChatHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSaveChatHistory(enabled) }
    }

    fun setLocalOnlyPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLocalOnlyPrivacyMode(enabled) }
    }

    fun setShareTelemetryWithAi(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShareTelemetryWithAi(enabled) }
    }

    fun setHighIntensityAnimations(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHighIntensityAnimations(enabled) }
    }

    fun purgeAllLocalData() {
        viewModelScope.launch {
            _ephemeralMessages.value = emptyList()
            repository.purgeAllData()
            _events.tryEmit("All local records purged.")
        }
    }

    fun testVoice() {
        val sample = when (settings.value.language) {
            AppLanguage.ENGLISH -> "J.A.R.V.I.S. acoustic systems calibrated and standing by."
            AppLanguage.BENGALI -> "জার্ভিস ভয়েস সিস্টেম সক্রিয় এবং প্রস্তুত।"
            AppLanguage.HINDI -> "जार्विस वॉयस सिस्टम तैयार है।"
        }
        voiceEngine.speak(sample)
    }

    private suspend fun appendChatMessage(message: ChatMessageEntity, persist: Boolean) {
        if (persist) {
            repository.addChatMessage(message)
        } else {
            _ephemeralMessages.value = _ephemeralMessages.value + message.copy(id = System.nanoTime())
        }
    }

    private data class ActionExecution(val badge: String, val replyMessage: String)

    private suspend fun executeAction(action: JarvisAction, language: AppLanguage): ActionExecution {
        return when (action) {
            is JarvisAction.CreateNote -> {
                repository.addNote(action.title, action.content)
                ActionExecution(
                    badge = "NOTE SAVED",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "নোট সংরক্ষণ করা হয়েছে: ${action.title}"
                        AppLanguage.HINDI -> "नोट सहेजा गया: ${action.title}"
                        AppLanguage.ENGLISH -> "Note recorded: \"${action.title}\"."
                    }
                )
            }
            is JarvisAction.CreateReminder -> {
                val triggerAt = System.currentTimeMillis() + action.delayMinutes.coerceAtLeast(1) * 60_000L
                val id = repository.addReminder(action.title, triggerAt)
                reminderScheduler.scheduleReminder(id, action.title, triggerAt)
                ActionExecution(
                    badge = "REMINDER • ${action.delayMinutes}m",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "${action.delayMinutes} মিনিট পরের জন্য রিমাইন্ডার সেট করা হয়েছে: ${action.title}"
                        AppLanguage.HINDI -> "${action.delayMinutes} मिनट बाद के लिए रिमाइंडर सेट किया गया: ${action.title}"
                        AppLanguage.ENGLISH -> "Reminder set for ${action.delayMinutes} minutes from now: \"${action.title}\"."
                    }
                )
            }
            is JarvisAction.SetAlarm -> {
                repository.addAlarm(action.hour, action.minute, action.label)
                reminderScheduler.launchSystemAlarmIntent(action.hour, action.minute, action.label)
                val timeStr = CommandParser.formatAlarmTime(action.hour, action.minute)
                ActionExecution(
                    badge = "ALARM • $timeStr",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "$timeStr-এ অ্যালার্ম সেট করা হয়েছে।"
                        AppLanguage.HINDI -> "$timeStr के लिए अलार्म सेट कर दिया गया है।"
                        AppLanguage.ENGLISH -> "Alarm configured for $timeStr."
                    }
                )
            }
            JarvisAction.OpenCamera -> {
                val res = appLauncher.openCamera()
                ActionExecution(
                    badge = "CAMERA",
                    replyMessage = when (res) {
                        is LaunchResult.Launched -> when (language) {
                            AppLanguage.BENGALI -> "ক্যামেরা খোলা হচ্ছে।"
                            AppLanguage.HINDI -> "कैमरा खोल रहा हूँ।"
                            AppLanguage.ENGLISH -> "Opening device camera."
                        }
                        else -> "Unable to launch camera on this device."
                    }
                )
            }
            JarvisAction.OpenSettings -> {
                appLauncher.openSettings()
                ActionExecution(
                    badge = "SETTINGS",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "ডিভাইস সেটিংস খোলা হচ্ছে।"
                        AppLanguage.HINDI -> "डिवाइस सेटिंग्स खोल रहा हूँ।"
                        AppLanguage.ENGLISH -> "Opening Android system settings."
                    }
                )
            }
            is JarvisAction.OpenMaps -> {
                appLauncher.openMaps(action.query)
                val target = if (action.query.isNotBlank()) " (${action.query})" else ""
                ActionExecution(
                    badge = "MAPS$target",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "গুগল ম্যাপস খোলা হচ্ছে$target।"
                        AppLanguage.HINDI -> "गूगल मैप्स खोल रहा हूँ$target।"
                        AppLanguage.ENGLISH -> "Launching Google Maps$target."
                    }
                )
            }
            is JarvisAction.LaunchApp -> {
                val res = appLauncher.launchAppByQuery(action.appName)
                when (res) {
                    is LaunchResult.Launched -> ActionExecution(
                        badge = "APP • ${res.targetName.uppercase()}",
                        replyMessage = when (language) {
                            AppLanguage.BENGALI -> "${res.targetName} চালু করা হচ্ছে।"
                            AppLanguage.HINDI -> "${res.targetName} खोल रहा हूँ।"
                            AppLanguage.ENGLISH -> "Launching ${res.targetName}."
                        }
                    )
                    is LaunchResult.NotFound -> ActionExecution(
                        badge = "APP NOT FOUND",
                        replyMessage = when (language) {
                            AppLanguage.BENGALI -> "ডিভাইসে '${action.appName}' অ্যাপটি খুঁজে পাওয়া যায়নি।"
                            AppLanguage.HINDI -> "डिवाइस पर '${action.appName}' ऐप नहीं मिला।"
                            AppLanguage.ENGLISH -> "I could not locate an installed app matching \"${action.appName}\"."
                        }
                    )
                    is LaunchResult.Failed -> ActionExecution("LAUNCH FAILED", res.reason)
                }
            }
            JarvisAction.BatteryStatus -> {
                val t = telemetryManager.captureCurrentTelemetry()
                ActionExecution(
                    badge = "BATTERY • ${t.batteryPercent}%",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "ব্যাটারি চার্জ ${t.batteryPercent}% (${if (t.isCharging) "চার্জ হচ্ছে" else "ব্যবহৃত হচ্ছে"}), তাপমাত্রা ${String.format("%.1f", t.batteryTempCelsius)}°C।"
                        AppLanguage.HINDI -> "बैटरी स्तर ${t.batteryPercent}% है (${if (t.isCharging) "चार्जिंग" else "डिस्चार्जिंग"}), तापमान ${String.format("%.1f", t.batteryTempCelsius)}°C।"
                        AppLanguage.ENGLISH -> "Power cell is at ${t.batteryPercent}% (${if (t.isCharging) "Charging" else "Discharging"}), ${String.format("%.1f", t.batteryTempCelsius)}°C, health: ${t.batteryHealthLabel}."
                    }
                )
            }
            JarvisAction.DeviceStatus -> {
                val t = telemetryManager.captureCurrentTelemetry()
                ActionExecution(
                    badge = "TELEMETRY",
                    replyMessage = when (language) {
                        AppLanguage.BENGALI -> "ডিভাইস: ${t.deviceModel}, ${t.androidVersion}, ব্যাটারি ${t.batteryPercent}%, র‍্যাম ${t.ramUsedMb}/${t.ramTotalMb} MB, নেটওয়ার্ক ${t.networkStatus}।"
                        AppLanguage.HINDI -> "डिवाइस: ${t.deviceModel}, ${t.androidVersion}, बैटरी ${t.batteryPercent}%, रैम ${t.ramUsedMb}/${t.ramTotalMb} MB, नेटवर्क ${t.networkStatus}।"
                        AppLanguage.ENGLISH -> "System Diagnostics — ${t.deviceModel} running ${t.androidVersion}. Battery: ${t.batteryPercent}%. RAM: ${t.ramUsedMb}/${t.ramTotalMb} MB. Storage free: ${String.format("%.1f", t.storageFreeGb)} GB. Uplink: ${t.networkStatus}."
                    }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}

class JarvisViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val db = JarvisDatabase.getInstance(appContext)
        val repository = JarvisRepository(
            chatMessageDao = db.chatMessageDao(),
            noteDao = db.noteDao(),
            reminderDao = db.reminderDao(),
            alarmDao = db.alarmDao()
        )
        return JarvisViewModel(
            appContext = appContext,
            repository = repository,
            settingsRepository = SettingsRepository(appContext),
            aiRepository = FirebaseJarvisAiRepository(),
            telemetryManager = DeviceTelemetryManager(appContext),
            appLauncher = AppLauncherManager(appContext),
            reminderScheduler = ReminderScheduler(appContext),
            voiceEngine = VoiceEngine(appContext)
        ) as T
    }
}
