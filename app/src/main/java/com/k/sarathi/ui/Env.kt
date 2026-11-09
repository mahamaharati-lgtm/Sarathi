package com.k.sarathi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.k.sarathi.ai.Brain
import com.k.sarathi.ai.Device
import com.k.sarathi.data.Content
import com.k.sarathi.data.Repo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

data class VoiceState(
    val listening: Boolean = false,
    val partial: String = "",
    val level: Float = 0f,
    val speaking: Boolean = false,
    val error: String? = null,
    /** Hands-free mode: listen again after each spoken reply. */
    val conversation: Boolean = false,
)

interface Voice {
    val state: StateFlow<VoiceState>
    fun listen(onFinal: (String) -> Unit)
    fun stopListening()
    fun speak(text: String)
    fun stopSpeaking()
    fun setConversation(on: Boolean)
}

/** Where the Claude API key lives (Android Keystore-encrypted on device). */
interface KeyVault {
    fun get(): String?
    fun set(value: String?)
}

/** Platform intents the UI needs, kept behind an interface for tests. */
interface Platform {
    fun share(text: String, subject: String = "Sarathi")
    fun shareFile(name: String, mime: String, content: String)
    fun hasPermission(permission: String): Boolean
    fun openAppSettings()
    fun appVersion(): String
}

data class Snack(val message: String, val action: String? = null, val onAction: (() -> Unit)? = null)

class UiBus {
    private val _snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 8)
    val snacks: SharedFlow<Snack> = _snacks.asSharedFlow()
    fun snack(message: String, action: String? = null, onAction: (() -> Unit)? = null) { _snacks.tryEmit(Snack(message, action, onAction)) }
    /** Shows "<message> · Undo" and runs [undo] if tapped. */
    fun undo(message: String, undo: () -> Unit) = snack(message, "Undo", undo)

    var paletteOpen by mutableStateOf(false)
    /** Text pre-filled into the chat input (e.g. shared from another app). */
    var chatDraft by mutableStateOf("")
    var listenOnOpen by mutableStateOf(false)
    var addExpenseOnOpen by mutableStateOf(false)
    var journalPrompt by mutableStateOf<String?>(null)
    /** Text shared from another app, waiting for the user to choose what to do. */
    var sharedText by mutableStateOf<String?>(null)
}

/** Tabs (bottom bar roots) and a stack of pushed screens on top of them. */
class Nav(private val tabs: () -> List<String>) {
    var tab by mutableStateOf("hud")
        private set
    val stack = mutableStateListOf<String>()
    val current: String get() = stack.lastOrNull() ?: tab

    fun isTab(route: String) = route == "hud" || route == "more" || route == "sarathi" || route in tabs()

    fun go(route: String) {
        if (isTab(route)) { tab = route; stack.clear() }
        else if (stack.lastOrNull() != route) stack.add(route)
    }

    fun back(): Boolean {
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab != "hud") { tab = "hud"; return true }
        return false
    }
}

class AppEnv(
    val repo: Repo,
    val content: Content,
    val brain: Brain,
    val device: Device,
    val voice: Voice,
    val keys: KeyVault,
    val platform: Platform,
    val bus: UiBus = UiBus(),
) {
    val nav = Nav { repo.db.settings.tabs }
    val weatherRefresh = MutableStateFlow(0)
}

val LocalEnv = staticCompositionLocalOf<AppEnv> { error("AppEnv not provided") }

val env: AppEnv @Composable get() = LocalEnv.current
