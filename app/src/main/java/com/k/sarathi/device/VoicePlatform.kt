package com.k.sarathi.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.k.sarathi.BuildConfig
import com.k.sarathi.data.Settings as AppSettings
import com.k.sarathi.ui.Platform
import com.k.sarathi.ui.Voice
import com.k.sarathi.ui.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

/**
 * Speech in and out. In hands-free mode, Sarathi listens again after every
 * spoken reply, until the user says "thank you", "stop" or goes quiet.
 */
class AndroidVoice(private val ctx: Context, private val settings: () -> AppSettings) : Voice {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(VoiceState())
    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var callback: ((String) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("en", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { _state.update { it.copy(speaking = true) } }
                    override fun onError(id: String?) { _state.update { it.copy(speaking = false) } }
                    override fun onDone(id: String?) {
                        _state.update { it.copy(speaking = false) }
                        if (_state.value.conversation) callback?.let { cb -> main.postDelayed({ listen(cb) }, 350) }
                    }
                })
                pendingSpeech?.let { speak(it) }; pendingSpeech = null
            }
        }
    }

    override fun listen(onFinal: (String) -> Unit) {
        main.post {
            callback = onFinal
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _state.update { it.copy(error = "Microphone permission is needed for voice.", listening = false) }
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                _state.update { it.copy(error = "No speech recognition service on this phone.") }
                return@post
            }
            stopSpeaking()
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(listener)
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings().sttLang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            }
            _state.update { it.copy(listening = true, partial = "", error = null, level = 0f) }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) } }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _state.update { it.copy(level = 0f) } }
        override fun onError(error: Int) {
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            _state.update {
                it.copy(listening = false, level = 0f, conversation = if (quiet) false else it.conversation,
                    error = if (quiet) null else "Couldn't hear that (error $error). Tap the mic to try again.")
            }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            _state.update { it.copy(listening = false, partial = "", level = 0f) }
            if (text.isEmpty()) return
            if (_state.value.conversation && STOP.any { text.lowercase().trimEnd('.', '!').endsWith(it) }) {
                _state.update { it.copy(conversation = false) }
                speak("You're welcome.")
                return
            }
            callback?.invoke(text)
        }
        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { p -> _state.update { it.copy(partial = p) } }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun stopListening() {
        main.post { recognizer?.stopListening(); recognizer?.cancel(); _state.update { it.copy(listening = false, level = 0f, conversation = false) } }
    }

    override fun speak(text: String) {
        main.post {
            ensureTts()
            if (!ttsReady) { pendingSpeech = text; return@post }
            val clean = text.replace(Regex("[*`#_]"), "").take(1200)
            tts?.setSpeechRate(settings().ttsRate)
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "sarathi-${System.currentTimeMillis()}")
        }
    }

    override fun stopSpeaking() { tts?.stop(); _state.update { it.copy(speaking = false) } }

    override fun setConversation(on: Boolean) = _state.update { it.copy(conversation = on) }

    companion object {
        private val STOP = listOf("thank you", "thanks", "stop", "that's all", "that is all", "bye", "goodbye", "dhanyavaad", "dhanyavad")
    }
}

class AndroidPlatform(private val ctx: Context) : Platform {
    private fun start(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    override fun share(text: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, subject)
        start(Intent.createChooser(send, "Share"))
    }

    override fun shareFile(name: String, mime: String, content: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name).apply { writeText(content) }
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(send, "Save or share $name").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    override fun openAppSettings() {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }

    override fun appVersion() = BuildConfig.VERSION_NAME
}
