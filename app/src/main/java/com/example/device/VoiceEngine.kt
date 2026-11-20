package com.example.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.prefs.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "JarvisVoiceEngine"

class VoiceEngine(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var currentLanguage: AppLanguage = AppLanguage.ENGLISH
    private var speechRate: Float = 1.0f
    private var voicePitch: Float = 1.0f

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    applyTtsConfig()
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                        }
                    })
                } else {
                    Log.w(TAG, "TextToSpeech initialization returned status=$status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    fun updateSettings(language: AppLanguage, rate: Float, pitch: Float) {
        currentLanguage = language
        speechRate = rate
        voicePitch = pitch
        applyTtsConfig()
    }

    private fun applyTtsConfig() {
        val tts = textToSpeech ?: return
        if (!isTtsReady) return
        val res = tts.setLanguage(currentLanguage.ttsLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if device lacks specific voice pack
            tts.setLanguage(AppLanguage.ENGLISH.ttsLocale)
        }
        tts.setSpeechRate(speechRate)
        tts.setPitch(voicePitch)
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val tts = textToSpeech ?: return
        if (!isTtsReady) {
            _voiceError.value = "Text-to-Speech engine is initializing."
            return
        }
        applyTtsConfig()
        val utteranceId = UUID.randomUUID().toString()
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        runCatching { textToSpeech?.stop() }
        _isSpeaking.value = false
    }

    fun startListening(
        language: AppLanguage,
        onFinalResult: (String) -> Unit
    ) {
        _voiceError.value = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceError.value = "Speech recognition service is not available on this device."
            return
        }

        stopSpeaking()
        stopListening()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _partialTranscript.value = ""
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    // SpeechRecognizer typically reports roughly -2f..10f dB
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    _partialTranscript.value = ""
                    _voiceError.value = mapSpeechError(error)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    if (best.isNotEmpty()) {
                        onFinalResult(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.sttLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.sttLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _isListening.value = true
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _isListening.value = false
            _voiceError.value = e.localizedMessage ?: "Microphone capture failed."
        }
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    fun clearError() {
        _voiceError.value = null
    }

    fun release() {
        stopListening()
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
        isTtsReady = false
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network unavailable for speech recognition."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech server unavailable."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition error ($code)."
    }
}
