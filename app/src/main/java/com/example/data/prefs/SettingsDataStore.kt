package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val sttLocaleTag: String,
    val ttsLocale: Locale
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeLabel = "English",
        sttLocaleTag = "en-US",
        ttsLocale = Locale.US
    ),
    BENGALI(
        code = "bn",
        displayName = "Bengali",
        nativeLabel = "বাংলা",
        sttLocaleTag = "bn-BD",
        ttsLocale = Locale("bn", "BD")
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeLabel = "हिन्दी",
        sttLocaleTag = "hi-IN",
        ttsLocale = Locale("hi", "IN")
    );

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class HudTheme(val id: String, val label: String) {
    ARC_CYAN("arc_cyan", "Arc Cyan"),
    STARK_COBALT("stark_cobalt", "Stark Cobalt"),
    STEALTH_EMERALD("stealth_emerald", "Matrix Emerald");

    companion object {
        fun fromId(id: String?): HudTheme =
            entries.firstOrNull { it.id == id } ?: ARC_CYAN
    }
}

data class JarvisSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val hudTheme: HudTheme = HudTheme.ARC_CYAN,
    val autoSpeakResponses: Boolean = true,
    val speechRate: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val saveChatHistory: Boolean = true,
    val localOnlyPrivacyMode: Boolean = false,
    val shareTelemetryWithAi: Boolean = true,
    val highIntensityAnimations: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val HUD_THEME = stringPreferencesKey("hud_theme")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val SAVE_CHAT_HISTORY = booleanPreferencesKey("save_chat_history")
        val LOCAL_ONLY_MODE = booleanPreferencesKey("local_only_privacy_mode")
        val SHARE_TELEMETRY = booleanPreferencesKey("share_telemetry_with_ai")
        val HIGH_INTENSITY_ANIMATIONS = booleanPreferencesKey("high_intensity_animations")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        JarvisSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            hudTheme = HudTheme.fromId(prefs[Keys.HUD_THEME]),
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.0f,
            saveChatHistory = prefs[Keys.SAVE_CHAT_HISTORY] ?: true,
            localOnlyPrivacyMode = prefs[Keys.LOCAL_ONLY_MODE] ?: false,
            shareTelemetryWithAi = prefs[Keys.SHARE_TELEMETRY] ?: true,
            highIntensityAnimations = prefs[Keys.HIGH_INTENSITY_ANIMATIONS] ?: true
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.jarvisDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setHudTheme(theme: HudTheme) {
        context.jarvisDataStore.edit { it[Keys.HUD_THEME] = theme.id }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setVoicePitch(pitch: Float) {
        context.jarvisDataStore.edit { it[Keys.VOICE_PITCH] = pitch.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setSaveChatHistory(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SAVE_CHAT_HISTORY] = enabled }
    }

    suspend fun setLocalOnlyPrivacyMode(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.LOCAL_ONLY_MODE] = enabled }
    }

    suspend fun setShareTelemetryWithAi(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SHARE_TELEMETRY] = enabled }
    }

    suspend fun setHighIntensityAnimations(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.HIGH_INTENSITY_ANIMATIONS] = enabled }
    }
}
