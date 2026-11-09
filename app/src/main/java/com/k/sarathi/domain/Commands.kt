package com.k.sarathi.domain

import com.k.sarathi.data.Category
import java.time.ZonedDateTime

/** An instruction understood on-device, without any network call. */
sealed interface Command {
    data class LogExpense(val amount: Double, val category: Category, val note: String) : Command
    data class AddTask(val title: String, val due: ZonedDateTime?, val hasTime: Boolean, val remind: Boolean) : Command
    data class SetTimer(val seconds: Int, val label: String) : Command
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : Command
    data class StartFocus(val minutes: Int?, val label: String) : Command
    data class LogHabit(val name: String) : Command
    data class AddNote(val body: String) : Command
    data class AddJournal(val text: String) : Command
    data class Calculate(val expression: String, val result: Double) : Command
    data class Open(val screen: Screen) : Command
    data class Torch(val on: Boolean) : Command
}

/** Navigable screens, with the words people use for them. */
enum class Screen(val route: String, val title: String, val words: List<String>) {
    HUD("hud", "HUD", listOf("hud", "home", "dashboard")),
    SARATHI("sarathi", "Sarathi", listOf("sarathi", "chat", "assistant", "ai")),
    PLAN("plan", "Plan", listOf("plan", "tasks", "todo", "todos", "reminders", "agenda")),
    HABITS("habits", "Habits", listOf("habits", "habit", "streaks", "goals")),
    MONEY("money", "Money", listOf("money", "expenses", "expense", "budget", "spending", "finance")),
    NOTES("notes", "Notes", listOf("notes", "note")),
    JOURNAL("journal", "Journal", listOf("journal", "diary")),
    GITA("gita", "Gita", listOf("gita", "shloka", "shlokas", "bhagavad gita", "verses")),
    FOCUS("focus", "Focus", listOf("focus", "pomodoro", "deep work")),
    REVIEW("review", "Weekly review", listOf("review", "weekly review", "week")),
    WEATHER("weather", "Weather", listOf("weather", "forecast")),
    SETTINGS("settings", "Settings", listOf("settings", "systems", "preferences")),
    MORE("more", "More", listOf("more", "modules", "menu"));

    companion object {
        fun byRoute(r: String) = entries.firstOrNull { it.route == r }
        fun match(word: String): Screen? {
            val w = word.trim().lowercase()
            return entries.firstOrNull { s -> s.words.any { it == w } }
        }
    }
}

object Commands {
    private val reExpense = Regex(
        """^(?:i\s+)?(?:spent|spend|paid|pay|expense|log|logged|bought)\s*(?:₹|rs\.?|inr)?\s*([\d,]+(?:\.\d{1,2})?)\s*(?:₹|rs\.?|rupees|inr|/-)?\s*(?:(?:on|for|at|in|to)\s+)?(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val reExpenseSuffix = Regex(
        """^(.{2,40}?)\s+(?:₹|rs\.?\s*)([\d,]+(?:\.\d{1,2})?)$|^(?:₹|rs\.?\s*)([\d,]+(?:\.\d{1,2})?)\s+(.{2,40})$""",
        RegexOption.IGNORE_CASE,
    )
    private val reTask = Regex("""^(?:add\s+)?(?:a\s+)?(?:task|todo|to-do|to do)\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reAddTo = Regex("""^add\s+(.+?)\s+to\s+(?:my\s+)?(?:tasks|task list|todo|to-do list|list)$""", RegexOption.IGNORE_CASE)
    private val reRemind = Regex("""^(?:please\s+)?(?:remind me|reminder|set (?:a )?reminder)\s*(?:to|that|about|for)?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reTimer = Regex("""^(?:set\s+)?(?:a\s+)?(?:timer\s+(?:for\s+)?(\d+)\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?)|(\d+)\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?)\s+timer)(?:\s+(?:for|called)\s+(.+))?$""", RegexOption.IGNORE_CASE)
    private val reAlarm = Regex("""^(?:set\s+)?(?:an?\s+)?(?:alarm|wake me(?: up)?)\s*(?:for|at)?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reFocus = Regex("""^(?:start\s+)?(?:a\s+)?(?:focus|pomodoro|deep work)(?:\s+(?:session|mode))?(?:\s+(?:for\s+)?(\d+)\s*(?:m|min|mins|minutes?)?)?(?:\s+(?:on|for)\s+(.+))?$""", RegexOption.IGNORE_CASE)
    private val reHabit = Regex("""^(?:done|did|completed|finished|log habit|habit)\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reNote = Regex("""^(?:note|save note|new note|jot)\s*[:\-]\s*(.+)$|^(?:note|jot down)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val reJournal = Regex("""^(?:journal|dear diary|diary)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reOpen = Regex("""^(?:open|go to|show(?: me)?|launch)\s+(?:my\s+|the\s+)?(.+)$""", RegexOption.IGNORE_CASE)
    private val reTorch = Regex("""^(?:turn\s+)?(on|off)?\s*(?:the\s+)?(?:torch|flashlight|flash light)\s*(on|off)?$""", RegexOption.IGNORE_CASE)

    private val categoryWords: List<Pair<Category, List<String>>> = listOf(
        Category.FOOD to listOf("lunch", "dinner", "breakfast", "coffee", "tea", "chai", "snack", "swiggy", "zomato", "food", "restaurant", "biryani", "dosa", "pizza", "meal", "cafe", "juice"),
        Category.GROCERIES to listOf("grocery", "groceries", "vegetables", "veggies", "milk", "fruits", "bigbasket", "blinkit", "zepto", "dmart", "instamart", "kirana", "rice"),
        Category.TRANSPORT to listOf("uber", "ola", "auto", "rapido", "metro", "bus", "petrol", "diesel", "fuel", "cab", "taxi", "train", "parking", "toll", "flight"),
        Category.SHOPPING to listOf("amazon", "flipkart", "myntra", "clothes", "shoes", "shirt", "shopping", "gift", "electronics", "ajio"),
        Category.BILLS to listOf("rent", "electricity", "bill", "recharge", "wifi", "internet", "broadband", "emi", "gas", "water", "dth", "insurance", "maintenance"),
        Category.HEALTH to listOf("medicine", "medicines", "doctor", "pharmacy", "gym", "hospital", "clinic", "tablets", "health", "yoga"),
        Category.FUN to listOf("movie", "movies", "netflix", "spotify", "prime", "hotstar", "game", "games", "party", "concert", "outing", "trip"),
        Category.EDUCATION to listOf("book", "books", "course", "udemy", "fees", "tuition", "class", "exam", "kindle"),
        Category.INVEST to listOf("sip", "mutual fund", "stock", "stocks", "invest", "investment", "gold", "ppf", "nps", "fd"),
    )

    fun categoryFor(note: String): Category {
        val n = note.lowercase()
        for ((cat, words) in categoryWords) {
            if (words.any { w -> Regex("""\b${Regex.escape(w)}\b""").containsMatchIn(n) }) return cat
        }
        Category.entries.firstOrNull { n.contains(it.label.lowercase()) }?.let { return it }
        return Category.OTHER
    }

    /**
     * Returns a command when [raw] is unambiguous enough to run instantly;
     * otherwise null and the text goes to Claude (or gets a help reply).
     */
    fun parse(raw: String, now: ZonedDateTime, habitNames: List<String> = emptyList()): Command? {
        val text = raw.trim().trimEnd('.', '!').replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return null

        Screen.match(text)?.let { return Command.Open(it) }
        reOpen.matchEntire(text)?.let { m -> Screen.match(m.groupValues[1])?.let { return Command.Open(it) } }

        if (Calc.looksLikeMath(text)) {
            runCatching { Calc.eval(text) }.getOrNull()?.let { return Command.Calculate(text, it) }
        }

        reExpense.matchEntire(text)?.let { m ->
            val amount = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) {
                val note = m.groupValues[2].trim().trimEnd('.')
                return Command.LogExpense(amount, categoryFor(note), note.replaceFirstChar { it.uppercase() })
            }
        }
        reExpenseSuffix.matchEntire(text)?.let { m ->
            val note = (m.groupValues[1].ifEmpty { m.groupValues[4] }).trim()
            val amount = (m.groupValues[2].ifEmpty { m.groupValues[3] }).replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) return Command.LogExpense(amount, categoryFor(note), note.replaceFirstChar { it.uppercase() })
        }

        reTimer.matchEntire(text)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt()
            val unit = (m.groupValues[2].ifEmpty { m.groupValues[4] }).lowercase()
            val secs = when {
                unit.startsWith("h") -> n * 3600
                unit.startsWith("s") -> n
                else -> n * 60
            }
            if (secs in 1..86_400) return Command.SetTimer(secs, m.groupValues[5].trim())
        }

        reAlarm.matchEntire(text)?.let { m ->
            val tm = TimeParse.find(m.groupValues[1], now)
            if (tm != null && tm.hasTime) return Command.SetAlarm(tm.at.hour, tm.at.minute, tm.rest)
        }

        reFocus.matchEntire(text)?.let { m ->
            return Command.StartFocus(m.groupValues[1].toIntOrNull()?.coerceIn(1, 240), m.groupValues[2].trim())
        }

        reRemind.matchEntire(text)?.let { m ->
            val body = m.groupValues[1]
            val tm = TimeParse.find(body, now)
            val title = (tm?.rest ?: body).removePrefix("to ").trim().replaceFirstChar { it.uppercase() }
            if (title.isNotBlank()) return Command.AddTask(title, tm?.at, tm?.hasTime ?: false, remind = tm != null)
        }
        (reTask.matchEntire(text) ?: reAddTo.matchEntire(text))?.let { m ->
            val body = m.groupValues[1]
            val tm = TimeParse.find(body, now)
            val title = (tm?.rest ?: body).trim().replaceFirstChar { it.uppercase() }
            if (title.isNotBlank()) return Command.AddTask(title, tm?.at, tm?.hasTime ?: false, remind = tm?.hasTime == true)
        }

        reHabit.matchEntire(text)?.let { m ->
            val name = m.groupValues[1].trim().lowercase()
            habitNames.firstOrNull { it.lowercase() == name || it.lowercase().startsWith(name) || name.startsWith(it.lowercase()) }
                ?.let { return Command.LogHabit(it) }
        }

        reNote.matchEntire(text)?.let { m -> return Command.AddNote(m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()) }
        reJournal.matchEntire(text)?.let { m -> return Command.AddJournal(m.groupValues[1].trim()) }

        reTorch.matchEntire(text)?.let { m ->
            val state = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase()
            return Command.Torch(state != "off")
        }
        return null
    }
}
