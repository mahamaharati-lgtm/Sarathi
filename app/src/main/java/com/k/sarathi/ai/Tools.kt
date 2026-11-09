package com.k.sarathi.ai

import com.k.sarathi.data.Category
import com.k.sarathi.data.Priority
import com.k.sarathi.data.Recur
import com.k.sarathi.data.Repo
import com.k.sarathi.data.ToolCard
import com.k.sarathi.data.WeatherCache
import com.k.sarathi.data.Content
import com.k.sarathi.domain.Calc
import com.k.sarathi.domain.Command
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.Review
import com.k.sarathi.domain.Screen
import com.k.sarathi.domain.Streaks
import com.k.sarathi.domain.Tasks
import com.k.sarathi.domain.TimeParse
import com.k.sarathi.domain.WeatherCodes
import com.k.sarathi.domain.toZoned
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

data class CalEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String)

/** Phone capabilities the tools need; the real implementation lives in `device`. */
interface Device {
    fun setAlarm(hour: Int, minute: Int, label: String, days: List<DayOfWeek>): String
    fun setTimer(seconds: Int, label: String): String
    fun torch(on: Boolean): String
    suspend fun weather(): WeatherCache?
    /** Null when calendar permission has not been granted. */
    fun events(date: LocalDate): List<CalEvent>?
    fun createEvent(title: String, start: ZonedDateTime, end: ZonedDateTime, location: String): String
    fun open(route: String)
    fun startFocus(minutes: Int, label: String): String
    fun startBreak(minutes: Int): String
    fun stopFocus(completed: Boolean)
}

data class ToolOutcome(val content: String, val isError: Boolean = false, val card: ToolCard? = null)

private class P(val name: String, val type: String, val desc: String, val enum: List<String>? = null, val items: String? = null)

/** Tool definitions. The order and text are fixed so the prompt prefix (and cache) stay stable. */
object ToolSpecs {
    private fun tool(name: String, desc: String, required: List<String>, vararg props: P): JsonObject = buildJsonObject {
        put("name", name)
        put("description", desc)
        put("eager_input_streaming", true)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                for (p in props) putJsonObject(p.name) {
                    put("type", p.type)
                    put("description", p.desc)
                    p.enum?.let { e -> putJsonArray("enum") { e.forEach { add(JsonPrimitive(it)) } } }
                    p.items?.let { putJsonObject("items") { put("type", it) } }
                }
            }
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private val CATS = Category.entries.map { it.name.lowercase() }
    private val RECUR = Recur.entries.map { it.name.lowercase() }
    private val SCREENS = Screen.entries.map { it.route }
    private const val WHEN = "Local date-time as ISO 2026-11-09T18:00, or a date 2026-11-09 for all-day"

    val all: JsonArray = buildJsonArray {
        add(tool("create_task", "Add a task or reminder. With a due time and remind=true the phone rings at that time.", listOf("title"),
            P("title", "string", "Short task title"),
            P("due", "string", WHEN),
            P("remind", "boolean", "Ring at the due time (default true when due has a time)"),
            P("priority", "string", "Priority", listOf("high", "normal", "low")),
            P("recur", "string", "Repeat rule", RECUR),
            P("notes", "string", "Extra details")))
        add(tool("list_tasks", "List open tasks with ids.", emptyList(),
            P("filter", "string", "Which tasks (default today, which includes overdue)", listOf("today", "overdue", "upcoming", "someday", "all"))))
        add(tool("complete_task", "Mark a task done by id, or by a fuzzy title match.", emptyList(),
            P("task_id", "integer", "Task id from list_tasks"), P("title", "string", "Task title (fuzzy)")))
        add(tool("reschedule_task", "Move a task to a new due time.", listOf("due"),
            P("task_id", "integer", "Task id"), P("title", "string", "Task title (fuzzy)"), P("due", "string", WHEN)))
        add(tool("log_expense", "Record money spent.", listOf("amount"),
            P("amount", "number", "Amount in the user's currency"),
            P("category", "string", "Category", CATS),
            P("note", "string", "What it was for"),
            P("date", "string", "ISO date if not today")))
        add(tool("delete_last_expense", "Remove the most recently logged expense (undoable in the app).", emptyList()))
        add(tool("expense_summary", "Spending totals, top categories and budget pace.", emptyList(),
            P("period", "string", "Period (default month)", listOf("today", "week", "month", "last_month"))))
        add(tool("set_budget", "Set the monthly budget, overall or for one category.", listOf("amount"),
            P("amount", "number", "Monthly amount; 0 clears it"), P("category", "string", "Category (omit for overall)", CATS)))
        add(tool("create_habit", "Start tracking a new daily habit.", listOf("name"),
            P("name", "string", "Habit name"), P("emoji", "string", "One emoji")))
        add(tool("log_habit", "Mark a habit done for today (or a date).", listOf("name"),
            P("name", "string", "Habit name (fuzzy)"), P("date", "string", "ISO date if not today")))
        add(tool("habit_status", "All habits with today's status and streaks.", emptyList()))
        add(tool("create_note", "Save a note.", listOf("body"),
            P("title", "string", "Optional title"), P("body", "string", "Note text")))
        add(tool("search_notes", "Search notes and journal entries.", listOf("query"), P("query", "string", "Words to find")))
        add(tool("append_journal", "Add a journal entry for today.", listOf("text"),
            P("text", "string", "Entry text"), P("mood", "integer", "Mood 1 (low) to 5 (great)")))
        add(tool("set_alarm", "Create an alarm in the phone's Clock app.", listOf("hour", "minute"),
            P("hour", "integer", "Hour 0-23"), P("minute", "integer", "Minute 0-59"), P("label", "string", "Alarm label"),
            P("days", "array", "Repeat days like [\"mon\",\"wed\"]; omit for one-time", items = "string")))
        add(tool("set_timer", "Start a countdown timer in the Clock app.", listOf("seconds"),
            P("seconds", "integer", "Timer length in seconds"), P("label", "string", "Timer label")))
        add(tool("start_focus", "Start a focus (pomodoro) session in Sarathi.", emptyList(),
            P("minutes", "integer", "Length in minutes (default from settings)"), P("label", "string", "What to focus on")))
        add(tool("get_weather", "Current weather and today's forecast for the user's city.", emptyList()))
        add(tool("list_events", "Calendar events for a day.", emptyList(), P("date", "string", "ISO date (default today)")))
        add(tool("create_event", "Open the calendar to add an event (the user confirms it there).", listOf("title", "start"),
            P("title", "string", "Event title"), P("start", "string", "ISO local date-time"),
            P("end", "string", "ISO local date-time (default start + 1h)"), P("location", "string", "Location")))
        add(tool("get_shloka", "A Bhagavad Gita shloka: by reference (e.g. 2.47), or today's.", emptyList(),
            P("ref", "string", "Chapter.verse like 2.47"), P("theme", "string", "A word to search meanings for")))
        add(tool("get_quote", "Today's motivational quote.", emptyList()))
        add(tool("remember_fact", "Remember a durable fact about the user for future chats.", listOf("fact"),
            P("fact", "string", "The fact, in third person")))
        add(tool("forget_fact", "Forget a remembered fact.", listOf("text"), P("text", "string", "Words from the fact")))
        add(tool("open_screen", "Open a screen in the app.", listOf("screen"), P("screen", "string", "Screen", SCREENS)))
        add(tool("flashlight", "Turn the torch on or off.", listOf("on"), P("on", "boolean", "true for on")))
        add(tool("weekly_review", "Stats for the last 7 days: tasks, habits, focus, spending, journal.", emptyList()))
        add(tool("calculate", "Evaluate arithmetic, including percentages like '15% of 2000'.", listOf("expression"),
            P("expression", "string", "Expression")))
        add(tool("days_until", "Days from today until a date.", listOf("date"), P("date", "string", "ISO date")))
    }

    private val byName: Map<String, JsonObject> = all.associate { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject }

    /** Strict check of streamed tool input against its schema. Returns an error message or null. */
    fun validate(name: String, input: JsonObject?): String? {
        if (input == null) return "Input was not valid JSON."
        val spec = byName[name] ?: return "Unknown tool '$name'."
        val schema = spec["input_schema"]!!.jsonObject
        val props = schema["properties"]!!.jsonObject
        for (r in schema["required"]!!.jsonArray) {
            val k = r.jsonPrimitive.content
            if (input[k] == null) return "Missing required field '$k'."
        }
        for ((k, v) in input) {
            val p = props[k]?.jsonObject ?: continue
            val type = p["type"]!!.jsonPrimitive.content
            val ok = when (type) {
                "string" -> v is JsonPrimitive && v.isString
                "integer" -> v is JsonPrimitive && !v.isString && (v.longOrNull != null || v.doubleOrNull?.let { it % 1.0 == 0.0 } == true)
                "number" -> v is JsonPrimitive && !v.isString && v.doubleOrNull != null
                "boolean" -> v is JsonPrimitive && !v.isString && v.booleanOrNull != null
                "array" -> v is JsonArray
                else -> true
            }
            if (!ok) return "Field '$k' must be a $type."
            p["enum"]?.jsonArray?.let { e ->
                if (e.none { it.jsonPrimitive.content == v.jsonPrimitive.content }) return "Field '$k' must be one of ${e.joinToString { it.jsonPrimitive.content }}."
            }
        }
        return null
    }
}

/** Runs tools (for Claude) and offline commands (for the router) against the same repo. */
class ToolExecutor(private val repo: Repo, private val content: Content, private val device: Device) {
    private val zone get() = repo.clock.zone()
    private val now get() = repo.clock.now()
    private val cur get() = repo.db.settings.currency
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val timeFmt get() = DateTimeFormatter.ofPattern(if (repo.db.settings.use24h) "HH:mm" else "h:mm a", Locale.ENGLISH)

    fun whenText(at: Long, allDay: Boolean): String {
        val z = at.toZoned(zone)
        val day = when (z.toLocalDate()) {
            now.toLocalDate() -> "Today"
            now.toLocalDate().plusDays(1) -> "Tomorrow"
            else -> z.format(dayFmt)
        }
        return if (allDay) day else "$day ${z.format(timeFmt)}"
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.let { it.longOrNull ?: it.doubleOrNull?.roundToInt()?.toLong() }
    private fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.b(k: String) = this[k]?.jsonPrimitive?.booleanOrNull

    /** Parses ISO date / date-time, falling back to natural phrases. Returns (millis, allDay). */
    fun parseWhen(s: String): Pair<Long, Boolean>? {
        val t = s.trim()
        runCatching { LocalDateTime.parse(t.replace(' ', 'T').let { if (it.length == 16) "$it:00" else it }) }.getOrNull()?.let {
            return it.atZone(zone).toInstant().toEpochMilli() to false
        }
        runCatching { ZonedDateTime.parse(t) }.getOrNull()?.let { return it.toInstant().toEpochMilli() to false }
        runCatching { LocalDate.parse(t) }.getOrNull()?.let {
            return it.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() to true
        }
        TimeParse.find(t, now)?.let { return it.at.toInstant().toEpochMilli() to !it.hasTime }
        return null
    }

    private fun category(s: String?): Category? = s?.let { v -> Category.entries.firstOrNull { it.name.equals(v, true) || it.label.equals(v, true) } }

    suspend fun run(name: String, input: JsonObject): ToolOutcome = try {
        dispatch(name, input)
    } catch (e: Exception) {
        ToolOutcome("Failed: ${e.message ?: e.javaClass.simpleName}", isError = true, card = ToolCard(name, "Couldn't complete that", e.message ?: "", ok = false))
    }

    private suspend fun dispatch(name: String, a: JsonObject): ToolOutcome = when (name) {
        "create_task" -> {
            val w = a.s("due")?.let { parseWhen(it) ?: return ToolOutcome("Couldn't read due '${a.s("due")}'. Use ISO like 2026-11-09T18:00.", true) }
            val remind = a.b("remind") ?: (w != null && !w.second)
            val pr = when (a.s("priority")) { "high" -> Priority.HIGH; "low" -> Priority.LOW; else -> Priority.NORMAL }
            val recur = a.s("recur")?.let { r -> Recur.entries.firstOrNull { it.name.equals(r, true) } } ?: Recur.NONE
            addTask(a.s("title")!!, w?.first, w?.second ?: false, remind, pr, recur, a.s("notes") ?: "")
        }
        "list_tasks" -> {
            val db = repo.db
            val f = a.s("filter") ?: "today"
            val list = when (f) {
                "overdue" -> Tasks.overdue(db, now)
                "upcoming" -> Tasks.upcoming(db, now)
                "someday" -> Tasks.someday(db)
                "all" -> Tasks.open(db)
                else -> (Tasks.overdue(db, now) + Tasks.dueOn(db, now.toLocalDate(), zone)).distinctBy { it.id }
            }
            val lines = Tasks.sort(list).take(40).joinToString("\n") { t ->
                "#${t.id} ${t.title}" + (t.due?.let { " · due ${whenText(it, t.allDay)}" } ?: "") +
                    (if (t.priority == Priority.HIGH) " · high" else "") + (if (t.recur != Recur.NONE) " · ${t.recur.name.lowercase()}" else "")
            }
            ToolOutcome(if (lines.isEmpty()) "No $f tasks." else lines)
        }
        "complete_task" -> {
            val t = a.i("task_id")?.let { id -> repo.db.tasks.firstOrNull { it.id == id && !it.done } }
                ?: a.s("title")?.let { Tasks.findByTitle(repo.db, it) }
                ?: return ToolOutcome("No matching open task. Call list_tasks to get ids.", true)
            repo.toggleTask(t.id)
            ToolOutcome("Completed #${t.id} “${t.title}”.", card = ToolCard("complete_task", "Done: ${t.title}"))
        }
        "reschedule_task" -> {
            val t = a.i("task_id")?.let { id -> repo.db.tasks.firstOrNull { it.id == id } }
                ?: a.s("title")?.let { Tasks.findByTitle(repo.db, it) }
                ?: return ToolOutcome("No matching task.", true)
            val w = parseWhen(a.s("due")!!) ?: return ToolOutcome("Couldn't read the due time.", true)
            repo.updateTask(t.copy(due = w.first, allDay = w.second, remind = !w.second || t.remind))
            ToolOutcome("Moved “${t.title}” to ${whenText(w.first, w.second)}.", card = ToolCard("reschedule_task", t.title, "→ ${whenText(w.first, w.second)}"))
        }
        "log_expense" -> {
            val amount = a.d("amount")!!
            if (amount <= 0) return ToolOutcome("Amount must be positive.", true)
            val note = a.s("note") ?: ""
            val cat = category(a.s("category")) ?: com.k.sarathi.domain.Commands.categoryFor(note)
            val at = a.s("date")?.let { parseWhen(it)?.first } ?: repo.clock.millis()
            logExpense(amount, cat, note, at)
        }
        "delete_last_expense" -> {
            val last = repo.db.expenses.maxByOrNull { it.id } ?: return ToolOutcome("There are no expenses.", true)
            repo.deleteExpense(last.id)
            ToolOutcome("Removed ${Money.fmt(last.amount, cur)} ${last.category.label} “${last.note}”.",
                card = ToolCard("delete_last_expense", "Removed ${Money.fmt(last.amount, cur)}", last.note))
        }
        "expense_summary" -> ToolOutcome(summary(a.s("period") ?: "month"))
        "set_budget" -> {
            val amt = a.d("amount")!!
            val c = category(a.s("category"))
            if (c == null) repo.setMonthlyBudget(amt) else repo.setCategoryBudget(c, amt)
            val what = c?.label ?: "Monthly"
            ToolOutcome("$what budget set to ${Money.fmt(amt, cur)}.", card = ToolCard("set_budget", "$what budget", Money.fmt(amt, cur)))
        }
        "create_habit" -> {
            val h = repo.addHabit(a.s("name")!!, a.s("emoji") ?: "✦")
            ToolOutcome("Tracking habit “${h.name}”.", card = ToolCard("create_habit", "New habit: ${h.emoji} ${h.name}"))
        }
        "log_habit" -> {
            val date = a.s("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: now.toLocalDate()
            logHabit(a.s("name")!!, date)
        }
        "habit_status" -> {
            val today = now.toLocalDate()
            val hs = repo.db.habits.filter { !it.archived }
            ToolOutcome(if (hs.isEmpty()) "No habits yet." else hs.joinToString("\n") { h ->
                "${h.name}: ${if (today.toString() in h.done) "done today" else "not yet today"} · streak ${Streaks.current(h, today)}d · best ${Streaks.best(h)}d · ${h.freezes} freezes"
            })
        }
        "create_note" -> {
            val body = a.s("body")!!
            val n = repo.addNote(a.s("title") ?: body.lineSequence().first().take(48), body)
            ToolOutcome("Saved note #${n.id}.", card = ToolCard("create_note", "Note saved", n.title))
        }
        "search_notes" -> {
            val q = a.s("query")!!.lowercase()
            val notes = repo.db.notes.filter { it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
                .take(8).map { "Note #${it.id} “${it.title}”: ${it.body.take(300)}" }
            val journal = repo.db.journal.filter { it.text.lowercase().contains(q) }.take(5).map { "Journal ${it.date}: ${it.text.take(300)}" }
            ToolOutcome((notes + journal).joinToString("\n").ifEmpty { "Nothing found for “$q”." })
        }
        "append_journal" -> {
            val j = repo.addJournal(a.s("text")!!, (a.i("mood") ?: 3).toInt())
            ToolOutcome("Journal entry saved for ${j.date}.", card = ToolCard("append_journal", "Journal entry saved", j.text.take(60)))
        }
        "set_alarm" -> {
            val h = a.i("hour")!!.toInt(); val m = a.i("minute")!!.toInt()
            if (h !in 0..23 || m !in 0..59) return ToolOutcome("Hour must be 0-23 and minute 0-59.", true)
            val days = (a["days"] as? JsonArray)?.mapNotNull { d -> dow(d.jsonPrimitive.content) } ?: emptyList()
            val msg = device.setAlarm(h, m, a.s("label") ?: "", days)
            ToolOutcome(msg, card = ToolCard("set_alarm", "Alarm %02d:%02d".format(h, m), a.s("label") ?: ""))
        }
        "set_timer" -> {
            val secs = a.i("seconds")!!.toInt()
            if (secs !in 1..86_400) return ToolOutcome("Seconds must be 1-86400.", true)
            ToolOutcome(device.setTimer(secs, a.s("label") ?: ""), card = ToolCard("set_timer", "Timer ${duration(secs)}", a.s("label") ?: ""))
        }
        "start_focus" -> {
            val mins = (a.i("minutes")?.toInt() ?: repo.db.settings.focusMin).coerceIn(1, 240)
            ToolOutcome(device.startFocus(mins, a.s("label") ?: ""), card = ToolCard("start_focus", "Focus $mins min", a.s("label") ?: ""))
        }
        "get_weather" -> {
            val w = device.weather() ?: return ToolOutcome("Weather is unavailable right now.", true)
            ToolOutcome(weatherText(w))
        }
        "list_events" -> {
            val date = a.s("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: now.toLocalDate()
            val ev = device.events(date) ?: return ToolOutcome("Calendar access isn't granted. The user can allow it from the HUD.", true)
            ToolOutcome(if (ev.isEmpty()) "No events on $date." else ev.joinToString("\n") {
                (if (it.allDay) "All day" else it.start.toZoned(zone).format(timeFmt)) + " ${it.title}" + (if (it.location.isNotBlank()) " @ ${it.location}" else "")
            })
        }
        "create_event" -> {
            val s = parseWhen(a.s("start")!!) ?: return ToolOutcome("Couldn't read start time.", true)
            val e = a.s("end")?.let { parseWhen(it)?.first } ?: (s.first + 3_600_000)
            val msg = device.createEvent(a.s("title")!!, s.first.toZoned(zone), e.toZoned(zone), a.s("location") ?: "")
            ToolOutcome(msg, card = ToolCard("create_event", a.s("title")!!, whenText(s.first, s.second)))
        }
        "get_shloka" -> {
            val sh = a.s("ref")?.let { content.byRef(it) }
                ?: a.s("theme")?.let { content.search(it).firstOrNull() }
                ?: content.shlokaOfDay(now.toLocalDate())
            ToolOutcome("Gita ${sh.ref} (${sh.speaker}):\n${sh.sa}\n${sh.iast}\nMeaning: ${sh.en}")
        }
        "get_quote" -> ToolOutcome(content.quoteOfDay(now.toLocalDate()))
        "remember_fact" -> {
            val m = repo.remember(a.s("fact")!!)
            ToolOutcome("Remembered.", card = ToolCard("remember_fact", "Remembered", m.text))
        }
        "forget_fact" -> {
            val q = a.s("text")!!.lowercase()
            val hit = repo.db.memory.firstOrNull { it.text.lowercase().contains(q) } ?: return ToolOutcome("No remembered fact matches.", true)
            repo.forget(hit.id)
            ToolOutcome("Forgot: ${hit.text}", card = ToolCard("forget_fact", "Forgotten", hit.text))
        }
        "open_screen" -> {
            val s = Screen.byRoute(a.s("screen")!!) ?: return ToolOutcome("Unknown screen.", true)
            device.open(s.route)
            ToolOutcome("Opened ${s.title}.", card = ToolCard("open_screen", "Opened ${s.title}"))
        }
        "flashlight" -> {
            val on = a.b("on")!!
            ToolOutcome(device.torch(on), card = ToolCard("flashlight", if (on) "Torch on" else "Torch off"))
        }
        "weekly_review" -> ToolOutcome(Review.text(Review.week(repo.db, now.toLocalDate(), zone), cur))
        "calculate" -> {
            val expr = a.s("expression")!!
            val v = runCatching { Calc.eval(expr) }.getOrElse { return ToolOutcome("Can't evaluate: ${it.message}", true) }
            ToolOutcome("$expr = ${Calc.format(v)}")
        }
        "days_until" -> {
            val d = runCatching { LocalDate.parse(a.s("date")!!) }.getOrNull() ?: return ToolOutcome("Use an ISO date.", true)
            ToolOutcome("${ChronoUnit.DAYS.between(now.toLocalDate(), d)} days until $d.")
        }
        else -> ToolOutcome("Unknown tool '$name'.", true)
    }

    // ---- shared actions (also used by offline commands) ----

    fun addTask(title: String, due: Long?, allDay: Boolean, remind: Boolean, priority: Priority = Priority.NORMAL, recur: Recur = Recur.NONE, notes: String = ""): ToolOutcome {
        val t = repo.addTask(title, due, allDay, remind && due != null, priority, recur, notes)
        val whenStr = due?.let { whenText(it, allDay) }
        val detail = listOfNotNull(whenStr, if (t.remind) "reminder on" else null, if (recur != Recur.NONE) recur.name.lowercase() else null).joinToString(" · ")
        return ToolOutcome("Added task #${t.id} “${t.title}”" + (if (detail.isNotEmpty()) " ($detail)" else "") + ".",
            card = ToolCard("create_task", t.title, detail.ifEmpty { "No due date" }))
    }

    fun logExpense(amount: Double, cat: Category, note: String, at: Long = repo.clock.millis()): ToolOutcome {
        val e = repo.logExpense(amount, cat, note, at)
        val m = Money.month(repo.db, now)
        val pace = if (m.budget > 0) " · ${Money.fmt(m.left, cur)} left this month" else ""
        return ToolOutcome("Logged ${Money.fmt(amount, cur)} ${cat.label}" + (if (note.isNotBlank()) " “$note”" else "") + ". Month so far ${Money.fmt(m.spent, cur)}$pace.",
            card = ToolCard("log_expense", "${cat.emoji} ${Money.fmt(e.amount, cur)} · ${cat.label}", (note.ifBlank { "" } + pace).trim(' ', '·')))
    }

    fun logHabit(name: String, date: LocalDate): ToolOutcome {
        val n = name.trim().lowercase()
        val h = repo.db.habits.firstOrNull { it.name.lowercase() == n }
            ?: repo.db.habits.firstOrNull { it.name.lowercase().contains(n) || n.contains(it.name.lowercase()) }
            ?: return ToolOutcome("No habit called “$name”. Use create_habit first.", true)
        repo.markHabitDone(h.id, date)
        val updated = repo.db.habits.first { it.id == h.id }
        val streak = Streaks.current(updated, now.toLocalDate())
        return ToolOutcome("Logged ${h.name}. Streak $streak days.", card = ToolCard("log_habit", "${h.emoji} ${h.name} ✓", "$streak-day streak"))
    }

    fun summary(period: String): String {
        val db = repo.db
        val today = now.toLocalDate()
        val (from, to, label) = when (period) {
            "today" -> Triple(today, today, "Today")
            "week" -> Triple(today.minusDays(6), today, "Last 7 days")
            "last_month" -> { val f = today.withDayOfMonth(1).minusMonths(1); Triple(f, f.withDayOfMonth(f.lengthOfMonth()), "Last month") }
            else -> Triple(today.withDayOfMonth(1), today, "This month")
        }
        val s = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val e = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = db.expenses.filter { it.at in s until e }
        val total = list.sumOf { it.amount }
        val cats = list.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }.toList().sortedByDescending { it.second }
        return buildString {
            append("$label: ${Money.fmt(total, cur)} across ${list.size} expenses.")
            if (cats.isNotEmpty()) append(" By category: " + cats.joinToString { "${it.first.label} ${Money.fmt(it.second, cur)}" } + ".")
            if (period == "month") Money.insights(Money.month(db, now), cur).forEach { append(" ").append(it) }
        }
    }

    fun weatherText(w: WeatherCache): String =
        "${w.city}: ${w.tempC.roundToInt()}°C, ${WeatherCodes.label(w.code)} (feels ${w.feelsC.roundToInt()}°). " +
            "Today ${w.minC.roundToInt()}–${w.maxC.roundToInt()}°C, rain chance ${w.rainPct}%, humidity ${w.humidity}%, wind ${w.windKmh.roundToInt()} km/h." +
            (w.tomorrowMaxC?.let { " Tomorrow up to ${it.roundToInt()}°C, ${WeatherCodes.label(w.tomorrowCode ?: 0)}." } ?: "")

    /** Runs an offline [Command]. Returns null for commands the UI handles itself (open screen). */
    suspend fun runCommand(c: Command): ToolOutcome? = when (c) {
        is Command.LogExpense -> logExpense(c.amount, c.category, c.note)
        is Command.AddTask -> addTask(c.title, c.due?.toInstant()?.toEpochMilli(), c.due != null && !c.hasTime, c.remind)
        is Command.SetTimer -> run("set_timer", buildJsonObject { put("seconds", c.seconds); put("label", c.label) })
        is Command.SetAlarm -> run("set_alarm", buildJsonObject { put("hour", c.hour); put("minute", c.minute); put("label", c.label) })
        is Command.StartFocus -> run("start_focus", buildJsonObject { c.minutes?.let { put("minutes", it) }; put("label", c.label) })
        is Command.LogHabit -> logHabit(c.name, now.toLocalDate())
        is Command.AddNote -> run("create_note", buildJsonObject { put("body", c.body) })
        is Command.AddJournal -> run("append_journal", buildJsonObject { put("text", c.text) })
        is Command.Calculate -> ToolOutcome("${c.expression.trim()} = ${Calc.format(c.result)}", card = ToolCard("calculate", Calc.format(c.result), c.expression.trim()))
        is Command.Torch -> run("flashlight", buildJsonObject { put("on", c.on) })
        is Command.Open -> { device.open(c.screen.route); null }
    }

    private fun dow(s: String): DayOfWeek? = when (s.trim().lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY; "tue" -> DayOfWeek.TUESDAY; "wed" -> DayOfWeek.WEDNESDAY; "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY; "sat" -> DayOfWeek.SATURDAY; "sun" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun duration(secs: Int) = when {
        secs % 3600 == 0 -> "${secs / 3600} h"
        secs >= 60 && secs % 60 == 0 -> "${secs / 60} min"
        else -> "$secs s"
    }
}

fun JsonElement.objOrNull(): JsonObject? = this as? JsonObject
