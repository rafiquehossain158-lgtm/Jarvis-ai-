package com.example.ai

import android.util.Log
import com.example.data.db.ChatMessageEntity
import com.example.data.prefs.AppLanguage
import com.example.device.DeviceTelemetry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "JarvisAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Completed(val finalOutput: ParsedModelOutput) : AiStreamEvent
}

interface JarvisAiRepository {
    fun streamReply(
        userPrompt: String,
        history: List<ChatMessageEntity>,
        language: AppLanguage,
        telemetry: DeviceTelemetry?,
        installedAppNames: List<String>
    ): Flow<AiStreamEvent>
}

class FirebaseJarvisAiRepository : JarvisAiRepository {

    private fun buildModel(
        language: AppLanguage,
        telemetry: DeviceTelemetry?,
        installedAppNames: List<String>
    ) = Firebase.ai.generativeModel(
        modelName = MODEL_NAME,
        systemInstruction = content {
            text(buildSystemInstruction(language, telemetry, installedAppNames))
        }
    )

    override fun streamReply(
        userPrompt: String,
        history: List<ChatMessageEntity>,
        language: AppLanguage,
        telemetry: DeviceTelemetry?,
        installedAppNames: List<String>
    ): Flow<AiStreamEvent> = flow {
        val model = buildModel(language, telemetry, installedAppNames)
        val chatHistory: List<Content> = history
            .filter { !it.isError && it.content.isNotBlank() }
            .takeLast(12)
            .map { msg ->
                val role = if (msg.role == "user") "user" else "model"
                content(role) { text(msg.content) }
            }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        val display = CommandParser.parseGeminiActionDirective(accumulated.toString()).cleanText
                        emit(AiStreamEvent.Chunk(display))
                    }
                }
            }
            val raw = accumulated.toString().trim()
            if (raw.isEmpty()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
            val parsed = CommandParser.parseGeminiActionDirective(raw)
            emit(AiStreamEvent.Completed(parsed))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini request timed out after 60s", e)
            throw IllegalStateException("Request timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic inference failed", e)
            throw e
        }
    }

    private fun buildSystemInstruction(
        language: AppLanguage,
        telemetry: DeviceTelemetry?,
        installedAppNames: List<String>
    ): String {
        val targetLanguageName = when (language) {
            AppLanguage.ENGLISH -> "English"
            AppLanguage.BENGALI -> "Bengali (বাংলা)"
            AppLanguage.HINDI -> "Hindi (हिन्दी)"
        }
        val telemetryBlock = telemetry?.let { "Live device telemetry: ${it.toPromptSummary()}." }
            ?: "Device telemetry sharing is disabled by user privacy controls."
        val appsSample = if (installedAppNames.isNotEmpty()) {
            "Installed apps on device include: ${installedAppNames.take(30).joinToString(", ")}."
        } else {
            ""
        }

        return """
            You are J.A.R.V.I.S., a futuristic, intelligent, polite, and concise AI assistant running natively on Android.
            Always respond primarily in $targetLanguageName unless the user explicitly writes in English, Bengali, or Hindi asking for a specific language.
            Keep replies crisp, helpful, and natural for both reading and Text-to-Speech vocalization (avoid excessive markdown tables or ASCII art).
            $telemetryBlock
            $appsSample
            If the user asks to perform an Android device action, append EXACTLY ONE action tag at the very end of your response using one of these formats:
            - [ACTION:NOTE|short title|full note body]
            - [ACTION:REMINDER|reminder title|delay in integer minutes]
            - [ACTION:ALARM|hour 0-23|minute 0-59|alarm label]
            - [ACTION:CAMERA]
            - [ACTION:SETTINGS]
            - [ACTION:MAPS|search query or empty]
            - [ACTION:APP|app name]
            - [ACTION:BATTERY]
            - [ACTION:DEVICE]
            Only emit an [ACTION:...] tag when the user clearly requested creating a note/reminder/alarm, checking device/battery info, or opening an app/camera/settings/maps.
        """.trimIndent()
    }
}
