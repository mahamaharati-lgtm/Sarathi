package com.k.sarathi.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Result of finding a time phrase inside free text. */
data class TimeMatch(
    val at: ZonedDateTime,
    /** False when only a date was given ("tomorrow", "on friday"). */
    val hasTime: Boolean,
    /** The input with the time phrase removed and tidied. */
    val rest: String,
)

/**
 * Finds English time expressions such as "tomorrow 6pm", "in 20 min",
 * "at 7:30", "next monday", "on the 5th", "15 nov", "tonight".
 * Pure function of the input and [now], so it is fully unit-tested.
 */
object TimeParse {
    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )
    private val PARTS = mapOf(
        "morning" to LocalTime.of(9, 0), "afternoon" to LocalTime.of(14, 0),
        "evening" to LocalTime.of(18, 0), "night" to LocalTime.of(21, 0),
        "noon" to LocalTime.NOON, "midnight" to LocalTime.MIDNIGHT,
    )

    private const val WD = "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun"
    private const val MO = "january|february|march|april|may|june|july|august|september|sept|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec"

    private val reRelative = Regex("""\b(?:in|after)\s+(\d+|an?|half an?)\s*(minutes?|mins?|m|hours?|hrs?|h|days?|weeks?)\b""")
    private val reClock = Regex("""\b(?:at\s+|by\s+|@\s*)?(\d{1,2})(?::|\.)(\d{2})\s*(am|pm|a\.m\.|p\.m\.)?\b|\b(?:at\s+|by\s+|@\s*)?(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)\b|\bat\s+(\d{1,2})\b(?!\s*(?:%|rs|₹|minutes?|mins?|hours?|days?|th|st|nd|rd))""")
    private val reIso = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val reSlash = Regex("""\b(?:on\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val reDayMonth = Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MO)\b""")
    private val reMonthDay = Regex("""\b(?:on\s+)?($MO)\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val reOrdinal = Regex("""\b(?:on\s+)(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\b""")
    private val reWeekday = Regex("""\b(?:on\s+|this\s+|next\s+|coming\s+)?($WD)\b""")
    private val reDayWord = Regex("""\b(day after tomorrow|tomorrow|tmrw|tmr|today|tonight|this (?:morning|afternoon|evening))\b""")
    private val rePart = Regex("""\b(?:in the\s+|at\s+)?(morning|afternoon|evening|night|noon|midnight)\b""")
    private val reNextUnit = Regex("""\bnext\s+(week|month)\b""")

    fun find(input: String, now: ZonedDateTime): TimeMatch? {
        var text = " " + input.trim() + " "
        val lower = { text.lowercase() }
        var date: LocalDate? = null
        var time: LocalTime? = null
        var relative: ZonedDateTime? = null

        fun cut(r: IntRange) {
            text = text.substring(0, r.first) + " " + text.substring(r.last + 1)
        }

        reRelative.find(lower())?.let { m ->
            val qty = m.groupValues[1].let {
                when {
                    it.startsWith("half") -> -1
                    it == "a" || it == "an" -> 1
                    else -> it.toInt()
                }
            }
            val unit = m.groupValues[2]
            relative = when {
                qty == -1 -> now.plusMinutes(30)
                unit.startsWith("m") -> now.plusMinutes(qty.toLong())
                unit.startsWith("h") -> now.plusHours(qty.toLong())
                unit.startsWith("d") -> now.plusDays(qty.toLong())
                else -> now.plusWeeks(qty.toLong())
            }
            cut(m.range)
        }
        if (relative != null) {
            val at = relative!!.truncatedTo(ChronoUnit.MINUTES)
            return TimeMatch(at, hasTime = true, rest = tidy(text))
        }

        reDayWord.find(lower())?.let { m ->
            val w = m.groupValues[1]
            date = when (w) {
                "day after tomorrow" -> now.toLocalDate().plusDays(2)
                "tomorrow", "tmrw", "tmr" -> now.toLocalDate().plusDays(1)
                else -> now.toLocalDate()
            }
            when (w) {
                "tonight" -> time = LocalTime.of(21, 0)
                "this morning" -> time = LocalTime.of(9, 0)
                "this afternoon" -> time = LocalTime.of(14, 0)
                "this evening" -> time = LocalTime.of(18, 0)
            }
            cut(m.range)
        }

        if (date == null) reIso.find(lower())?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { date = it; cut(m.range) }
        }
        if (date == null) reDayMonth.find(lower())?.let { m ->
            monthDay(now, MONTHS.getValue(m.groupValues[2]), m.groupValues[1].toInt())?.let { date = it; cut(m.range) }
        }
        if (date == null) reMonthDay.find(lower())?.let { m ->
            monthDay(now, MONTHS.getValue(m.groupValues[1]), m.groupValues[2].toInt())?.let { date = it; cut(m.range) }
        }
        if (date == null) reSlash.find(lower())?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val parsed = if (y != null) runCatching { LocalDate.of(y, mo, d) }.getOrNull() else monthDay(now, mo, d)
            parsed?.let { date = it; cut(m.range) }
        }
        if (date == null) reOrdinal.find(lower())?.let { m ->
            val d = m.groupValues[1].toInt()
            val today = now.toLocalDate()
            var cand = runCatching { today.withDayOfMonth(d) }.getOrNull()
            if (cand == null || cand.isBefore(today)) cand = runCatching { today.plusMonths(1).withDayOfMonth(d) }.getOrNull()
            cand?.let { date = it; cut(m.range) }
        }
        if (date == null) reNextUnit.find(lower())?.let { m ->
            date = if (m.groupValues[1] == "week") now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            else now.toLocalDate().plusMonths(1).withDayOfMonth(1)
            cut(m.range)
        }
        if (date == null) reWeekday.find(lower())?.let { m ->
            val whole = m.value.trim()
            val dow = WEEKDAYS.getValue(m.groupValues[1])
            val today = now.toLocalDate()
            var d = today.with(TemporalAdjusters.nextOrSame(dow))
            if (whole.startsWith("next") && d == today) d = d.plusWeeks(1)
            date = d
            cut(m.range)
        }

        reClock.find(lower())?.let { m ->
            val g = m.groupValues
            val (h, min, ap) = when {
                g[1].isNotEmpty() -> Triple(g[1].toInt(), g[2].toInt(), g[3])
                g[4].isNotEmpty() -> Triple(g[4].toInt(), 0, g[5])
                else -> Triple(g[6].toInt(), 0, "")
            }
            var hour = h
            val apNorm = ap.replace(".", "")
            if (apNorm == "pm" && hour < 12) hour += 12
            if (apNorm == "am" && hour == 12) hour = 0
            if (apNorm.isEmpty() && g[1].isEmpty() && hour in 1..7) hour += 12 // "at 6" usually means 6 pm
            if (hour in 0..23 && min in 0..59) {
                time = LocalTime.of(hour, min)
                cut(m.range)
            }
        }
        if (time == null) rePart.find(lower())?.let { m ->
            time = PARTS.getValue(m.groupValues[1])
            cut(m.range)
        }

        if (date == null && time == null) return null
        val hasTime = time != null
        var d = date ?: now.toLocalDate()
        val t = time ?: LocalTime.of(9, 0)
        var at = ZonedDateTime.of(d, t, now.zone)
        if (date == null && at.isBefore(now)) {
            d = d.plusDays(1)
            at = ZonedDateTime.of(d, t, now.zone)
        }
        return TimeMatch(at, hasTime, tidy(text))
    }

    private fun monthDay(now: ZonedDateTime, month: Int, day: Int): LocalDate? {
        val today = now.toLocalDate()
        val cand = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (cand.isBefore(today)) runCatching { cand.plusYears(1) }.getOrNull() else cand
    }

    /** Removes dangling connector words left behind after cutting a phrase. */
    fun tidy(s: String): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        val dangling = Regex("""^(?:on|at|by|for|in|the)\b\s*|\s*\b(?:on|at|by|for|in|the)$""", RegexOption.IGNORE_CASE)
        repeat(3) { t = t.replace(dangling, "").trim() }
        return t.trim(' ', ',', '-', '.', ':')
    }
}
