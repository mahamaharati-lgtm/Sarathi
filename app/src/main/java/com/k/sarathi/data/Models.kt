package com.k.sarathi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Everything Sarathi stores lives in one [Db] value. It is small (a personal
 * app), serialised to a single JSON file, and replaced atomically on every
 * change, which keeps backup/restore trivial: the backup *is* this file.
 */
@Serializable
data class Db(
    val schema: Int = 1,
    val nextId: Long = 1,
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val budget: Budget = Budget(),
    val notes: List<Note> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val focusLog: List<FocusSession> = emptyList(),
    val focus: FocusState = FocusState(),
    val memory: List<MemoryFact> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val gita: GitaProgress = GitaProgress(),
    val usage: List<UsageEntry> = emptyList(),
    val weather: WeatherCache? = null,
    val settings: Settings = Settings(),
)

enum class Priority { HIGH, NORMAL, LOW }

enum class Recur { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }

/** A task. When [due] is set and [remind] is true it also rings as a reminder. */
@Serializable
data class TaskItem(
    val id: Long,
    val title: String,
    val created: Long,
    val due: Long? = null,
    val allDay: Boolean = false,
    val remind: Boolean = false,
    val priority: Priority = Priority.NORMAL,
    val recur: Recur = Recur.NONE,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val notes: String = "",
)

@Serializable
data class Habit(
    val id: Long,
    val name: String,
    val emoji: String = "✦",
    val created: Long,
    /** ISO dates (yyyy-MM-dd) on which the habit was done. */
    val done: Set<String> = emptySet(),
    /** ISO dates protected by a streak freeze. */
    val frozen: Set<String> = emptySet(),
    val freezes: Int = 2,
    val archived: Boolean = false,
)

enum class Category(val label: String, val emoji: String) {
    FOOD("Food", "🍛"),
    GROCERIES("Groceries", "🛒"),
    TRANSPORT("Transport", "🛺"),
    SHOPPING("Shopping", "🛍"),
    BILLS("Bills", "🧾"),
    HEALTH("Health", "💊"),
    FUN("Fun", "🎬"),
    EDUCATION("Learning", "📚"),
    INVEST("Invest", "📈"),
    OTHER("Other", "◆"),
}

@Serializable
data class Expense(
    val id: Long,
    val amount: Double,
    val category: Category,
    val note: String = "",
    val at: Long,
)

@Serializable
data class Budget(
    val monthly: Double = 0.0,
    val perCategory: Map<Category, Double> = emptyMap(),
)

@Serializable
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val created: Long,
    val updated: Long,
)

@Serializable
data class JournalEntry(
    val id: Long,
    val date: String,
    val text: String,
    val mood: Int = 3,
    val shlokaRef: String? = null,
    val created: Long,
)

@Serializable
data class FocusSession(
    val id: Long,
    val label: String,
    val start: Long,
    val minutes: Int,
    val completed: Boolean,
)

enum class FocusPhase { IDLE, FOCUS, BREAK }

@Serializable
data class FocusState(
    val phase: FocusPhase = FocusPhase.IDLE,
    val label: String = "",
    val startedAt: Long = 0,
    val endsAt: Long = 0,
    val minutes: Int = 0,
)

@Serializable
data class MemoryFact(val id: Long, val text: String, val created: Long)

/**
 * One chat with Claude. [system] and [tools] are frozen when the conversation
 * starts and re-sent byte-for-byte on every turn: the API binds thinking
 * blocks (and the prompt cache) to an unchanged prefix, so a conversation must
 * only ever be appended to.
 */
@Serializable
data class Conversation(
    val id: Long,
    val title: String,
    val created: Long,
    val updated: Long,
    val system: String,
    val tools: JsonElement,
    val messages: List<ChatMsg> = emptyList(),
)

@Serializable
data class ChatMsg(
    val role: String,
    /** Exactly what is (re)sent to the API for this message. */
    val content: JsonElement,
    /** Text shown in the chat UI ("" for tool-result plumbing). */
    val display: String = "",
    val cards: List<ToolCard> = emptyList(),
    val hidden: Boolean = false,
    /** Excluded from the API history, e.g. a prompt that was declined. */
    val excluded: Boolean = false,
    val ts: Long = 0,
    val model: String? = null,
)

@Serializable
data class ToolCard(val tool: String, val title: String, val detail: String = "", val ok: Boolean = true)

@Serializable
data class GitaProgress(
    val read: Set<String> = emptySet(),
    val bookmarks: Set<String> = emptySet(),
    val last: String? = null,
)

@Serializable
data class UsageEntry(
    val at: Long,
    val model: String,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val usd: Double,
)

@Serializable
data class WeatherCache(
    val at: Long,
    val city: String,
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val humidity: Int,
    val windKmh: Double,
    val maxC: Double,
    val minC: Double,
    val rainPct: Int,
    val tomorrowMaxC: Double? = null,
    val tomorrowCode: Int? = null,
)

enum class ThemeChoice(val label: String) {
    AUTO("Auto"), ARC("Arc"), SAFFRON("Saffron"), EMBER("Ember"), NEBULA("Nebula"), DAY("Day")
}

enum class Effort(val api: String, val label: String, val blurb: String) {
    LOW("low", "Quick", "Fast, short answers — best for phone commands"),
    MEDIUM("medium", "Balanced", "More thinking for planning and advice"),
    HIGH("high", "Deep", "Thorough reasoning; slower and costlier"),
}

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val userName: String = "K",
    val city: String = "Bengaluru",
    val lat: Double = 12.9716,
    val lon: Double = 77.5946,
    val theme: ThemeChoice = ThemeChoice.AUTO,
    val dayThemeInDaylight: Boolean = false,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val sounds: Boolean = true,
    val use24h: Boolean = false,
    val currency: String = "₹",
    val model: String = "claude-opus-5-5",
    val effort: Effort = Effort.LOW,
    val monthlyCapUsd: Double = 0.0,
    val speakReplies: Boolean = false,
    val ttsRate: Float = 1.0f,
    val sttLang: String = "en-IN",
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val briefing: Boolean = true,
    val briefingTime: String = "07:00",
    val showIast: Boolean = true,
    /** The two slots beside the Sarathi orb; HUD is always first and More last. */
    val tabs: List<String> = listOf("plan", "money"),
)
