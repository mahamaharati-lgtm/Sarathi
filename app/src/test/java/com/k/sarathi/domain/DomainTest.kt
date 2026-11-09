package com.k.sarathi.domain

import com.k.sarathi.core.Store
import com.k.sarathi.data.Category
import com.k.sarathi.data.Clock
import com.k.sarathi.data.Db
import com.k.sarathi.data.Expense
import com.k.sarathi.data.Habit
import com.k.sarathi.data.Recur
import com.k.sarathi.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeParseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    // Sunday 8 Nov 2026, 19:05
    private val now = ZonedDateTime.of(2026, 11, 8, 19, 5, 0, 0, zone)

    private fun parse(s: String) = TimeParse.find(s, now)

    @Test fun tomorrowAtSixPm() {
        val m = parse("call amma tomorrow 6pm")!!
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, zone), m.at)
        assertTrue(m.hasTime)
        assertEquals("call amma", m.rest)
    }

    @Test fun relativeMinutes() {
        val m = parse("check oven in 20 min")!!
        assertEquals(now.plusMinutes(20).withSecond(0), m.at)
        assertEquals("check oven", m.rest)
    }

    @Test fun clockOnlyRollsToTomorrowWhenPast() {
        val m = parse("stretch at 7:30am")!!
        assertEquals(LocalDate.of(2026, 11, 9), m.at.toLocalDate())
        assertEquals(7, m.at.hour); assertEquals(30, m.at.minute)
    }

    @Test fun bareAtSixMeansEvening() {
        val m = parse("gym at 9")!!
        // 9 is outside 1..7 so it stays 09:00, which has passed today -> tomorrow
        assertEquals(9, m.at.hour)
        val e = parse("tea at 6")!!
        assertEquals(18, e.at.hour)
    }

    @Test fun weekdayAndNext() {
        assertEquals(LocalDate.of(2026, 11, 13), parse("pay rent on friday")!!.at.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 15), parse("call next sunday")!!.at.toLocalDate())
        assertFalse(parse("pay rent on friday")!!.hasTime)
    }

    @Test fun dayMonthAndOrdinal() {
        assertEquals(LocalDate.of(2026, 11, 15), parse("dentist 15 nov 10am")!!.at.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 5), parse("electricity bill on the 5th")!!.at.toLocalDate())
        assertEquals(LocalDate.of(2027, 1, 3), parse("trip jan 3")!!.at.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 25), parse("party 2026-12-25")!!.at.toLocalDate())
    }

    @Test fun tonightAndParts() {
        val m = parse("read tonight")!!
        assertEquals(21, m.at.hour); assertEquals(now.toLocalDate(), m.at.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 9), parse("walk tomorrow morning")!!.at.toLocalDate())
        assertEquals(9, parse("walk tomorrow morning")!!.at.hour)
    }

    @Test fun noTime() = assertNull(parse("buy milk"))
}

class CommandsTest {
    private val now = ZonedDateTime.of(2026, 11, 8, 19, 5, 0, 0, ZoneId.of("Asia/Kolkata"))
    private fun p(s: String, habits: List<String> = emptyList()) = Commands.parse(s, now, habits)

    @Test fun expenses() {
        val c = p("spent 200 on lunch") as Command.LogExpense
        assertEquals(200.0, c.amount, 0.0); assertEquals(Category.FOOD, c.category); assertEquals("Lunch", c.note)
        assertEquals(Category.TRANSPORT, (p("paid ₹350 for uber") as Command.LogExpense).category)
        assertEquals(1250.5, (p("spent 1,250.50 groceries") as Command.LogExpense).amount, 0.001)
        assertEquals(Category.GROCERIES, (p("spent 1,250.50 groceries") as Command.LogExpense).category)
        assertEquals(Category.BILLS, (p("electricity bill rs 1800") as Command.LogExpense).category)
    }

    @Test fun reminders() {
        val c = p("remind me to call amma at 6pm tomorrow") as Command.AddTask
        assertEquals("Call amma", c.title); assertTrue(c.remind); assertTrue(c.hasTime)
        assertEquals(18, c.due!!.hour)
        val t = p("add task renew passport on friday") as Command.AddTask
        assertEquals("Renew passport", t.title); assertFalse(t.remind)
        assertEquals("Buy milk", (p("todo: buy milk") as Command.AddTask).title)
    }

    @Test fun timersAlarmsFocus() {
        assertEquals(600, (p("set a timer for 10 minutes") as Command.SetTimer).seconds)
        assertEquals(300, (p("5 min timer") as Command.SetTimer).seconds)
        val a = p("wake me up at 6:30am") as Command.SetAlarm
        assertEquals(6, a.hour); assertEquals(30, a.minute)
        assertEquals(50, (p("focus 50") as Command.StartFocus).minutes)
        assertNull((p("start focus") as Command.StartFocus).minutes)
    }

    @Test fun mathScreensHabits() {
        assertEquals(300.0, (p("15% of 2000") as Command.Calculate).result, 1e-9)
        assertEquals(14.0, (p("2+3*4") as Command.Calculate).result, 1e-9)
        assertEquals(Screen.MONEY, (p("open expenses") as Command.Open).screen)
        assertEquals(Screen.GITA, (p("gita") as Command.Open).screen)
        assertEquals("Meditation", (p("done meditation", listOf("Meditation", "Walk")) as Command.LogHabit).name)
        assertNull(p("done something unknown", listOf("Meditation")))
    }

    @Test fun leavesConversationForClaude() {
        assertNull(p("what should I focus on this week?"))
        assertNull(p("how are you"))
        assertNull(p("2026"))
    }
}

class CalcTest {
    @Test fun basics() {
        assertEquals(7.0, Calc.eval("1 + 2 * 3"), 1e-9)
        assertEquals(9.0, Calc.eval("(1 + 2) * 3"), 1e-9)
        assertEquals(1024.0, Calc.eval("2^10"), 1e-9)
        assertEquals(-4.0, Calc.eval("-2 * 2"), 1e-9)
        assertEquals(1800.0, Calc.eval("2000 - 10%"), 1e-9)
        assertEquals(1700.0, Calc.eval("15% off 2000"), 1e-9)
        assertEquals("1,234", Calc.format(1234.0))
        assertEquals("0.333333", Calc.format(1.0 / 3))
    }
}

class LogicTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun recurrence() {
        val d = ZonedDateTime.of(2026, 11, 6, 9, 0, 0, 0, zone) // Friday
        assertEquals(LocalDate.of(2026, 11, 9), Recurrence.next(d, Recur.WEEKDAYS)!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 6), Recurrence.next(d, Recur.MONTHLY)!!.toLocalDate())
        val far = ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 20), Recurrence.next(d, Recur.DAILY, far.minusMinutes(1))!!.toLocalDate())
    }

    @Test fun streaksWithFreeze() {
        val h = Habit(1, "Walk", created = 0, done = setOf("2026-11-05", "2026-11-06", "2026-11-08"), frozen = setOf("2026-11-07"))
        assertEquals(4, Streaks.current(h, today))
        assertEquals(4, Streaks.best(h))
        val notToday = h.copy(done = h.done - "2026-11-08")
        assertEquals(3, Streaks.current(notToday, today))
        assertTrue(Streaks.atRisk(notToday, today))
    }

    @Test fun indianGrouping() {
        assertEquals("1,23,45,678", Money.indian(12345678))
        assertEquals("999", Money.indian(999))
        assertEquals("₹12,000", Money.fmt(12000.0))
    }

    @Test fun monthPace() {
        val now = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, zone)
        val at = now.toInstant().toEpochMilli()
        val db = Db(expenses = listOf(Expense(1, 9000.0, Category.FOOD, at = at)), budget = com.k.sarathi.data.Budget(monthly = 20000.0))
        val m = Money.month(db, now)
        assertEquals(9000.0, m.spent, 0.0)
        assertTrue(m.overPace) // 45% used after a third of the month
        assertEquals(27000.0, m.projected, 0.001)
    }

    @Test fun cost() {
        val usd = Models.cost("claude-opus-5-5", 1_000_000, 100_000, 0, 0)
        assertEquals(6.0, usd, 1e-9)
    }
}

class RepoTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var now = ZonedDateTime.of(2026, 11, 8, 19, 0, 0, 0, zone)
    private val repo = Repo(Store(null, CoroutineScope(Dispatchers.Unconfined)), Clock { now })

    @Test fun recurringTaskSpawnsNext() {
        val due = now.minusHours(1).toInstant().toEpochMilli()
        val t = repo.addTask("Water plants", due = due, recur = Recur.DAILY)
        repo.toggleTask(t.id)
        val open = repo.db.tasks.filter { !it.done }
        assertEquals(1, open.size)
        assertEquals(LocalDate.of(2026, 11, 9), open.single().due!!.toDate(zone))
        assertTrue(repo.db.tasks.first { it.id == t.id }.done)
    }

    @Test fun deleteAndUndo() {
        val e = repo.logExpense(120.0, Category.FOOD, "Coffee")
        val removed = repo.deleteExpense(e.id)
        assertTrue(repo.db.expenses.isEmpty())
        repo.restoreExpense(removed!!)
        assertEquals(listOf(e), repo.db.expenses)
    }

    @Test fun habitFreezeConsumesToken() {
        val h = repo.addHabit("Read")
        assertTrue(repo.freezeHabit(h.id, LocalDate.of(2026, 11, 7)))
        assertEquals(1, repo.db.habits.single().freezes)
        assertFalse(repo.freezeHabit(h.id, LocalDate.of(2026, 11, 7)))
        assertNotNull(repo.db.habits.single().frozen.firstOrNull())
    }

    @Test fun fuzzyTaskTitle() {
        repo.addTask("Pay electricity bill")
        repo.addTask("Call amma")
        assertEquals("Pay electricity bill", Tasks.findByTitle(repo.db, "electricity")!!.title)
        assertEquals("Call amma", Tasks.findByTitle(repo.db, "call amma")!!.title)
    }
}
