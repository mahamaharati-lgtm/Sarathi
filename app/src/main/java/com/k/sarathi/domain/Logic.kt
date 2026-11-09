package com.k.sarathi.domain

import com.k.sarathi.data.Budget
import com.k.sarathi.data.Category
import com.k.sarathi.data.Db
import com.k.sarathi.data.Expense
import com.k.sarathi.data.Habit
import com.k.sarathi.data.Recur
import com.k.sarathi.data.TaskItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun Long.toZoned(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(this).atZone(zone)
fun Long.toDate(zone: ZoneId): LocalDate = toZoned(zone).toLocalDate()
fun LocalDate.iso(): String = toString()

object Recurrence {
    /** Next occurrence strictly after [from] for a task due at [due]. */
    fun next(due: ZonedDateTime, recur: Recur, from: ZonedDateTime = due): ZonedDateTime? {
        if (recur == Recur.NONE) return null
        var t = due
        var guard = 0
        do {
            t = when (recur) {
                Recur.DAILY -> t.plusDays(1)
                Recur.WEEKDAYS -> {
                    var n = t.plusDays(1)
                    while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
                    n
                }
                Recur.WEEKLY -> t.plusWeeks(1)
                Recur.MONTHLY -> t.plusMonths(1)
                Recur.YEARLY -> t.plusYears(1)
                Recur.NONE -> return null
            }
        } while (!t.isAfter(from) && ++guard < 5000)
        return t
    }

    fun label(r: Recur) = when (r) {
        Recur.NONE -> "Once"
        Recur.DAILY -> "Daily"
        Recur.WEEKDAYS -> "Weekdays"
        Recur.WEEKLY -> "Weekly"
        Recur.MONTHLY -> "Monthly"
        Recur.YEARLY -> "Yearly"
    }
}

object Streaks {
    private fun counts(h: Habit, d: LocalDate) = d.iso() in h.done || d.iso() in h.frozen

    /** Current streak; today counts if done, otherwise it is still "in play". */
    fun current(h: Habit, today: LocalDate): Int {
        var d = if (counts(h, today)) today else today.minusDays(1)
        var n = 0
        while (counts(h, d)) { n++; d = d.minusDays(1) }
        return n
    }

    fun best(h: Habit): Int {
        val days = (h.done + h.frozen).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    /** Completion ratio over the last [days] days (frozen days excluded). */
    fun rate(h: Habit, today: LocalDate, days: Int = 30): Float {
        var done = 0; var counted = 0
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            if (d.iso() in h.frozen) continue
            counted++
            if (d.iso() in h.done) done++
        }
        return if (counted == 0) 0f else done.toFloat() / counted
    }

    /** A streak is at risk when yesterday was done but today is not yet. */
    fun atRisk(h: Habit, today: LocalDate) = !counts(h, today) && current(h, today) > 0
}

data class MonthMoney(
    val month: YearMonth,
    val spent: Double,
    val budget: Double,
    val byCategory: List<Pair<Category, Double>>,
    val dailyAverage: Double,
    val projected: Double,
    /** Fraction of the month elapsed (0..1). */
    val elapsed: Float,
    val lastMonthSameDay: Double,
) {
    val left get() = budget - spent
    val usedFraction get() = if (budget <= 0) 0f else (spent / budget).toFloat()
    /** Spending faster than the calendar is moving. */
    val overPace get() = budget > 0 && usedFraction > elapsed + 0.05f
}

object Money {
    fun inMonth(e: List<Expense>, ym: YearMonth, zone: ZoneId) =
        e.filter { YearMonth.from(it.at.toZoned(zone)) == ym }

    fun month(db: Db, now: ZonedDateTime): MonthMoney {
        val ym = YearMonth.from(now)
        val list = inMonth(db.expenses, ym, now.zone)
        val spent = list.sumOf { it.amount }
        val day = now.dayOfMonth
        val len = ym.lengthOfMonth()
        val byCat = list.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }
        val last = inMonth(db.expenses, ym.minusMonths(1), now.zone)
            .filter { it.at.toZoned(now.zone).dayOfMonth <= day }.sumOf { it.amount }
        return MonthMoney(
            month = ym, spent = spent, budget = db.budget.monthly, byCategory = byCat,
            dailyAverage = spent / day, projected = spent / day * len,
            elapsed = day.toFloat() / len, lastMonthSameDay = last,
        )
    }

    fun categoryBudget(b: Budget, c: Category) = b.perCategory[c] ?: 0.0

    fun spentBetween(e: List<Expense>, from: Long, to: Long) = e.filter { it.at in from until to }.sumOf { it.amount }

    fun insights(m: MonthMoney, currency: String): List<String> {
        val out = mutableListOf<String>()
        if (m.spent == 0.0) return listOf("Nothing logged this month yet. Try “spent 120 on coffee”.")
        m.byCategory.firstOrNull()?.let { (c, v) ->
            out += "${c.emoji} ${c.label} is your biggest category: ${fmt(v, currency)} (${(v / m.spent * 100).roundToInt()}%)."
        }
        if (m.lastMonthSameDay > 0) {
            val pct = ((m.spent - m.lastMonthSameDay) / m.lastMonthSameDay * 100).roundToInt()
            out += when {
                pct > 5 -> "Up $pct% versus the same point last month."
                pct < -5 -> "Down ${-pct}% versus the same point last month. Nice."
                else -> "About the same as this point last month."
            }
        }
        if (m.budget > 0) {
            out += if (m.overPace) "At this pace you'll spend ${fmt(m.projected, currency)} — over your ${fmt(m.budget, currency)} budget."
            else "On pace: projected ${fmt(m.projected, currency)} of ${fmt(m.budget, currency)}."
        } else out += "Set a monthly budget to see your pace."
        return out
    }

    fun fmt(v: Double, currency: String = "₹"): String {
        val whole = v.roundToInt()
        return currency + indian(whole.toLong())
    }

    /** Indian digit grouping: 12,34,567. */
    fun indian(n: Long): String {
        val neg = n < 0
        val s = kotlin.math.abs(n).toString()
        if (s.length <= 3) return (if (neg) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return (if (neg) "-" else "") + parts.joinToString(",") + "," + last3
    }

    fun csv(e: List<Expense>, zone: ZoneId): String {
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("date,amount,category,note\n")
        e.sortedBy { it.at }.forEach {
            val note = it.note.replace("\"", "\"\"")
            sb.append(it.at.toZoned(zone).format(f)).append(',').append(it.amount).append(',')
                .append(it.category.label).append(",\"").append(note).append("\"\n")
        }
        return sb.toString()
    }
}

object Tasks {
    fun open(db: Db) = db.tasks.filter { !it.done }
    fun overdue(db: Db, now: ZonedDateTime) = open(db).filter { t ->
        val d = t.due ?: return@filter false
        if (t.allDay) d.toDate(now.zone).isBefore(now.toLocalDate()) else d < now.toInstant().toEpochMilli()
    }
    fun dueOn(db: Db, date: LocalDate, zone: ZoneId) = open(db).filter { it.due?.toDate(zone) == date }
    fun upcoming(db: Db, now: ZonedDateTime) = open(db).filter { (it.due?.toDate(now.zone) ?: return@filter false).isAfter(now.toLocalDate()) }
    fun someday(db: Db) = open(db).filter { it.due == null }
    fun doneOn(db: Db, date: LocalDate, zone: ZoneId) = db.tasks.filter { it.done && it.doneAt?.toDate(zone) == date }
    fun sort(list: List<TaskItem>) = list.sortedWith(compareBy<TaskItem>({ it.due ?: Long.MAX_VALUE }, { it.priority.ordinal }, { it.created }))

    /** Fuzzy title lookup used by voice/AI ("mark the rent task done"). */
    fun findByTitle(db: Db, q: String): TaskItem? {
        val n = q.trim().lowercase()
        val open = open(db)
        return open.firstOrNull { it.title.lowercase() == n }
            ?: open.firstOrNull { it.title.lowercase().contains(n) }
            ?: open.maxByOrNull { overlap(it.title.lowercase(), n) }?.takeIf { overlap(it.title.lowercase(), n) > 0.5 }
    }

    private fun overlap(a: String, b: String): Double {
        val wa = a.split(Regex("\\W+")).filter { it.length > 1 }.toSet()
        val wb = b.split(Regex("\\W+")).filter { it.length > 1 }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return (wa intersect wb).size.toDouble() / wb.size
    }
}

data class WeekReview(
    val from: LocalDate,
    val to: LocalDate,
    val tasksDone: Int,
    val tasksOpen: Int,
    val habitRate: Float,
    val bestHabit: Pair<String, Int>?,
    val focusMinutes: Int,
    val focusSessions: Int,
    val spent: Double,
    val topCategory: Pair<Category, Double>?,
    val journalDays: Int,
    val shlokasRead: Int,
)

object Review {
    fun week(db: Db, today: LocalDate, zone: ZoneId): WeekReview {
        val from = today.minusDays(6)
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val habits = db.habits.filter { !it.archived }
        val rates = habits.map { h -> (0..6).count { from.plusDays(it.toLong()).iso() in h.done } / 7f }
        val best = habits.map { it.name to Streaks.current(it, today) }.maxByOrNull { it.second }?.takeIf { it.second > 0 }
        val exp = db.expenses.filter { it.at in start until end }
        val top = exp.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amount } }.maxByOrNull { it.value }?.toPair()
        val focus = db.focusLog.filter { it.start in start until end && it.completed }
        return WeekReview(
            from = from, to = today,
            tasksDone = db.tasks.count { it.done && (it.doneAt ?: 0) in start until end },
            tasksOpen = db.tasks.count { !it.done },
            habitRate = if (rates.isEmpty()) 0f else rates.average().toFloat(),
            bestHabit = best,
            focusMinutes = focus.sumOf { it.minutes }, focusSessions = focus.size,
            spent = exp.sumOf { it.amount }, topCategory = top,
            journalDays = db.journal.map { it.date }.filter { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> !d.isBefore(from) && !d.isAfter(today) } == true }.toSet().size,
            shlokasRead = db.gita.read.size,
        )
    }

    fun text(r: WeekReview, currency: String): String {
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        return buildString {
            appendLine("Sarathi weekly review · ${r.from.format(f)} – ${r.to.format(f)}")
            appendLine("✓ ${r.tasksDone} tasks done (${r.tasksOpen} open)")
            appendLine("◎ Habits ${(r.habitRate * 100).roundToInt()}% complete" + (r.bestHabit?.let { " · best streak: ${it.first} ${it.second}d" } ?: ""))
            appendLine("⏱ ${r.focusMinutes} focus minutes in ${r.focusSessions} sessions")
            appendLine("₹ Spent ${Money.fmt(r.spent, currency)}" + (r.topCategory?.let { " · mostly ${it.first.label}" } ?: ""))
            appendLine("✎ Journaled ${r.journalDays} of 7 days")
        }.trim()
    }
}

/** Claude per-million-token prices (USD) for the cost meter. Estimates only. */
data class Price(val input: Double, val output: Double, val cacheRead: Double, val cacheWrite: Double)

data class ModelInfo(val id: String, val name: String, val blurb: String, val price: Price, val fallbacks: Boolean)

object Models {
    val all = listOf(
        ModelInfo("claude-opus-5-5", "Opus 5.5", "Most capable everyday model", Price(4.0, 20.0, 0.20, 5.0), fallbacks = true),
        ModelInfo("claude-sonnet-5-5", "Sonnet 5.5", "Fast and capable · half the price", Price(2.0, 10.0, 0.20, 2.50), fallbacks = true),
        ModelInfo("claude-haiku-5-5", "Haiku 5.5", "Fastest and cheapest", Price(0.10, 0.50, 0.01, 0.125), fallbacks = false),
        ModelInfo("claude-fable-5-1", "Fable 5.1", "Anthropic's most capable · premium", Price(10.0, 50.0, 0.25, 12.50), fallbacks = true),
    )
    const val DEFAULT = "claude-opus-5-5"
    const val CHEAP = "claude-haiku-5-5"

    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun cost(model: String, input: Long, output: Long, cacheRead: Long, cacheWrite: Long): Double {
        val p = get(model).price
        return (input * p.input + output * p.output + cacheRead * p.cacheRead + cacheWrite * p.cacheWrite) / 1_000_000.0
    }

    fun monthSpend(db: Db, now: ZonedDateTime): Double {
        val ym = YearMonth.from(now)
        return db.usage.filter { YearMonth.from(it.at.toZoned(now.zone)) == ym }.sumOf { it.usd }
    }
}
