package com.k.sarathi.data

import com.k.sarathi.core.Store
import com.k.sarathi.domain.Recurrence
import com.k.sarathi.domain.iso
import com.k.sarathi.domain.toZoned
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

fun interface Clock {
    fun now(): ZonedDateTime
    fun millis(): Long = now().toInstant().toEpochMilli()
    fun zone(): ZoneId = now().zone
    fun today(): LocalDate = now().toLocalDate()

    companion object {
        val system = Clock { ZonedDateTime.now() }
    }
}

/**
 * All data operations, shared by the UI, the offline command router and the
 * Claude tools, so every path behaves identically. Side effects (alarms,
 * widgets) are reconciled by observers of the store, not called from here.
 */
class Repo(val store: Store, val clock: Clock = Clock.system) {
    val db get() = store.db

    // ---- tasks ----
    fun addTask(
        title: String,
        due: Long? = null,
        allDay: Boolean = false,
        remind: Boolean = due != null && !allDay,
        priority: Priority = Priority.NORMAL,
        recur: Recur = Recur.NONE,
        notes: String = "",
    ): TaskItem = store.mutate { db ->
        val t = TaskItem(db.nextId, title.trim(), clock.millis(), due, allDay, remind && due != null, priority, recur, notes = notes)
        db.copy(nextId = db.nextId + 1, tasks = db.tasks + t) to t
    }

    fun updateTask(t: TaskItem) = store.update { db -> db.copy(tasks = db.tasks.map { if (it.id == t.id) t else it }) }

    /** What completing a task changed, so it can be undone exactly. */
    data class Completion(val task: TaskItem, val spawnedId: Long?)

    /** Completes a task. A recurring task spawns its next occurrence. */
    fun complete(id: Long): Completion? = store.mutate { db ->
        val t = db.tasks.firstOrNull { it.id == id && !it.done } ?: return@mutate db to null
        val done = t.copy(done = true, doneAt = clock.millis())
        var tasks = db.tasks.map { if (it.id == id) done else it }
        var next = db.nextId
        var spawned: Long? = null
        val due = t.due
        if (t.recur != Recur.NONE && due != null) {
            val zone = clock.zone()
            val after = maxOf(due, clock.millis()).toZoned(zone)
            Recurrence.next(due.toZoned(zone), t.recur, after)?.let { n ->
                spawned = next
                tasks = tasks + t.copy(id = next++, due = n.toInstant().toEpochMilli(), done = false, doneAt = null, created = clock.millis())
            }
        }
        db.copy(nextId = next, tasks = tasks) to Completion(done, spawned)
    }

    fun undoComplete(c: Completion) = store.update { db ->
        db.copy(tasks = db.tasks.filterNot { it.id == c.spawnedId }.map { if (it.id == c.task.id) it.copy(done = false, doneAt = null) else it })
    }

    /** Completes an open task or reopens a done one. */
    fun toggleTask(id: Long): TaskItem? {
        val t = db.tasks.firstOrNull { it.id == id } ?: return null
        if (!t.done) return complete(id)?.task
        val reopened = t.copy(done = false, doneAt = null)
        store.update { db -> db.copy(tasks = db.tasks.map { if (it.id == id) reopened else it }) }
        return reopened
    }

    fun deleteTask(id: Long): TaskItem? = store.mutate { db ->
        val t = db.tasks.firstOrNull { it.id == id }
        db.copy(tasks = db.tasks.filterNot { it.id == id }) to t
    }

    fun restoreTask(t: TaskItem) = store.update { db -> db.copy(tasks = db.tasks.filterNot { it.id == t.id } + t) }

    fun snoozeTask(id: Long, minutes: Long) = store.update { db ->
        val at = clock.now().plusMinutes(minutes).toInstant().toEpochMilli()
        db.copy(tasks = db.tasks.map { if (it.id == id) it.copy(due = at, allDay = false, remind = true) else it })
    }

    fun clearCompleted() = store.update { db -> db.copy(tasks = db.tasks.filterNot { it.done }) }

    // ---- habits ----
    fun addHabit(name: String, emoji: String = "✦"): Habit = store.mutate { db ->
        val h = Habit(db.nextId, name.trim(), emoji.ifBlank { "✦" }, clock.millis())
        db.copy(nextId = db.nextId + 1, habits = db.habits + h) to h
    }

    fun updateHabit(h: Habit) = store.update { db -> db.copy(habits = db.habits.map { if (it.id == h.id) h else it }) }

    /** Toggles completion for [date]; returns true when it is now done. */
    fun toggleHabit(id: Long, date: LocalDate = clock.today()): Boolean = store.mutate { db ->
        var nowDone = false
        val habits = db.habits.map { h ->
            if (h.id != id) h else {
                val k = date.iso()
                nowDone = k !in h.done
                h.copy(done = if (nowDone) h.done + k else h.done - k)
            }
        }
        db.copy(habits = habits) to nowDone
    }

    fun markHabitDone(id: Long, date: LocalDate = clock.today()) = store.update { db ->
        db.copy(habits = db.habits.map { if (it.id == id) it.copy(done = it.done + date.iso()) else it })
    }

    /** Spends a freeze token to protect [date]. Returns false when none are left. */
    fun freezeHabit(id: Long, date: LocalDate): Boolean = store.mutate { db ->
        var ok = false
        val habits = db.habits.map { h ->
            if (h.id == id && h.freezes > 0 && date.iso() !in h.frozen && date.iso() !in h.done) {
                ok = true; h.copy(frozen = h.frozen + date.iso(), freezes = h.freezes - 1)
            } else h
        }
        db.copy(habits = habits) to ok
    }

    fun deleteHabit(id: Long): Habit? = store.mutate { db ->
        db.copy(habits = db.habits.filterNot { it.id == id }) to db.habits.firstOrNull { it.id == id }
    }

    fun restoreHabit(h: Habit) = store.update { db -> db.copy(habits = db.habits.filterNot { it.id == h.id } + h) }

    // ---- money ----
    fun logExpense(amount: Double, category: Category, note: String = "", at: Long = clock.millis()): Expense = store.mutate { db ->
        val e = Expense(db.nextId, amount, category, note.trim(), at)
        db.copy(nextId = db.nextId + 1, expenses = db.expenses + e) to e
    }

    fun updateExpense(e: Expense) = store.update { db -> db.copy(expenses = db.expenses.map { if (it.id == e.id) e else it }) }

    fun deleteExpense(id: Long): Expense? = store.mutate { db ->
        db.copy(expenses = db.expenses.filterNot { it.id == id }) to db.expenses.firstOrNull { it.id == id }
    }

    fun restoreExpense(e: Expense) = store.update { db -> db.copy(expenses = db.expenses.filterNot { it.id == e.id } + e) }

    fun setMonthlyBudget(v: Double) = store.update { db -> db.copy(budget = db.budget.copy(monthly = v.coerceAtLeast(0.0))) }

    fun setCategoryBudget(c: Category, v: Double) = store.update { db ->
        val m = db.budget.perCategory.toMutableMap()
        if (v <= 0) m.remove(c) else m[c] = v
        db.copy(budget = db.budget.copy(perCategory = m))
    }

    // ---- notes & journal ----
    fun addNote(title: String, body: String): Note = store.mutate { db ->
        val now = clock.millis()
        val n = Note(db.nextId, title.trim(), body.trim(), created = now, updated = now)
        db.copy(nextId = db.nextId + 1, notes = db.notes + n) to n
    }

    fun updateNote(n: Note) = store.update { db ->
        db.copy(notes = db.notes.map { if (it.id == n.id) n.copy(updated = clock.millis()) else it })
    }

    fun deleteNote(id: Long): Note? = store.mutate { db ->
        db.copy(notes = db.notes.filterNot { it.id == id }) to db.notes.firstOrNull { it.id == id }
    }

    fun restoreNote(n: Note) = store.update { db -> db.copy(notes = db.notes.filterNot { it.id == n.id } + n) }

    fun addJournal(text: String, mood: Int = 3, shlokaRef: String? = null, date: LocalDate = clock.today()): JournalEntry = store.mutate { db ->
        val j = JournalEntry(db.nextId, date.iso(), text.trim(), mood.coerceIn(1, 5), shlokaRef, clock.millis())
        db.copy(nextId = db.nextId + 1, journal = db.journal + j) to j
    }

    fun updateJournal(j: JournalEntry) = store.update { db -> db.copy(journal = db.journal.map { if (it.id == j.id) j else it }) }

    fun deleteJournal(id: Long): JournalEntry? = store.mutate { db ->
        db.copy(journal = db.journal.filterNot { it.id == id }) to db.journal.firstOrNull { it.id == id }
    }

    fun restoreJournal(j: JournalEntry) = store.update { db -> db.copy(journal = db.journal.filterNot { it.id == j.id } + j) }

    // ---- focus ----
    fun startFocus(minutes: Int, label: String, phase: FocusPhase = FocusPhase.FOCUS) = store.update { db ->
        val now = clock.millis()
        db.copy(focus = FocusState(phase, label.trim(), now, now + minutes * 60_000L, minutes))
    }

    /** Ends the current phase; a focus phase is logged (completed or not). */
    fun endFocus(completed: Boolean) = store.update { db ->
        val f = db.focus
        if (f.phase == FocusPhase.IDLE) return@update db
        var log = db.focusLog
        var next = db.nextId
        if (f.phase == FocusPhase.FOCUS) {
            val mins = if (completed) f.minutes else ((clock.millis() - f.startedAt) / 60_000L).toInt()
            if (mins > 0) log = log + FocusSession(next++, f.label.ifBlank { "Focus" }, f.startedAt, mins, completed)
        }
        db.copy(nextId = next, focusLog = log, focus = FocusState())
    }

    // ---- memory ----
    fun remember(text: String): MemoryFact = store.mutate { db ->
        val m = MemoryFact(db.nextId, text.trim(), clock.millis())
        db.copy(nextId = db.nextId + 1, memory = db.memory + m) to m
    }

    fun forget(id: Long) = store.update { db -> db.copy(memory = db.memory.filterNot { it.id == id }) }

    // ---- gita ----
    fun markRead(ref: String) = store.update { db -> db.copy(gita = db.gita.copy(read = db.gita.read + ref, last = ref)) }
    fun toggleBookmark(ref: String) = store.update { db ->
        val b = db.gita.bookmarks
        db.copy(gita = db.gita.copy(bookmarks = if (ref in b) b - ref else b + ref))
    }

    // ---- settings / misc ----
    fun settings(fn: (Settings) -> Settings) = store.update { db -> db.copy(settings = fn(db.settings)) }

    fun addUsage(u: UsageEntry) = store.update { db -> db.copy(usage = (db.usage + u).takeLast(5000)) }

    fun setWeather(w: WeatherCache) = store.update { db -> db.copy(weather = w) }

    // ---- conversations ----
    fun upsertConversation(c: Conversation) = store.update { db ->
        val others = db.conversations.filterNot { it.id == c.id }
        db.copy(conversations = (others + c).sortedByDescending { it.updated }.take(40))
    }

    fun newConversationId(): Long = store.mutate { db -> db.copy(nextId = db.nextId + 1) to db.nextId }

    fun deleteConversation(id: Long) = store.update { db -> db.copy(conversations = db.conversations.filterNot { it.id == id }) }
}
