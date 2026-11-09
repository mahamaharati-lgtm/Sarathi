package com.k.sarathi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.k.sarathi.data.Settings
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RouteInfo(val route: String, val label: String, val icon: ImageVector)

object Routes {
    val modules = listOf(
        RouteInfo("plan", "Plan", Icons.Rounded.Checklist),
        RouteInfo("habits", "Habits", Icons.Rounded.LocalFireDepartment),
        RouteInfo("money", "Money", Icons.Rounded.AccountBalanceWallet),
        RouteInfo("gita", "Gita", Icons.AutoMirrored.Rounded.MenuBook),
        RouteInfo("notes", "Notes", Icons.Rounded.EditNote),
        RouteInfo("journal", "Journal", Icons.Rounded.AutoStories),
        RouteInfo("focus", "Focus", Icons.Rounded.Timer),
        RouteInfo("review", "Review", Icons.Rounded.Insights),
        RouteInfo("weather", "Weather", Icons.Rounded.WbSunny),
        RouteInfo("chats", "Chats", Icons.Rounded.Forum),
    )
    /** Modules that can sit in the bottom bar. */
    val pinnable = listOf("plan", "habits", "money", "gita", "notes", "journal", "focus")
    val hud = RouteInfo("hud", "HUD", Icons.Rounded.Dashboard)
    val more = RouteInfo("more", "More", Icons.Rounded.GridView)
    val settings = RouteInfo("settings", "Settings", Icons.Rounded.Settings)
    val sarathi = RouteInfo("sarathi", "Sarathi", Icons.Rounded.AutoAwesome)
    fun info(route: String) = (modules + hud + more + settings + sarathi).firstOrNull { it.route == route }
}

/** Current time, refreshed at each minute boundary (or every [periodMs]). */
@Composable
fun rememberNow(clock: () -> ZonedDateTime, periodMs: Long = 0): ZonedDateTime {
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(periodMs) {
        while (true) {
            val wait = if (periodMs > 0) periodMs else 60_000L - (System.currentTimeMillis() % 60_000L) + 50
            delay(wait)
            now = clock()
        }
    }
    return now
}

object Fmt {
    fun time(z: ZonedDateTime, s: Settings): String =
        z.format(DateTimeFormatter.ofPattern(if (s.use24h) "HH:mm" else "h:mm a", Locale.ENGLISH))
    fun clock(z: ZonedDateTime, s: Settings): String =
        z.format(DateTimeFormatter.ofPattern(if (s.use24h) "HH:mm" else "h:mm", Locale.ENGLISH))
    fun ampm(z: ZonedDateTime, s: Settings): String = if (s.use24h) "" else z.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH))
    fun day(z: ZonedDateTime): String = z.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    fun shortDay(z: ZonedDateTime): String = z.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
    fun greeting(z: ZonedDateTime) = when (z.hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up"
    }
}

/** Friendly description of what a tool is doing, for the chat status line. */
fun toolVerb(name: String?): String = when (name) {
    null -> "Thinking"
    "create_task" -> "Adding a task"
    "list_tasks" -> "Checking your tasks"
    "complete_task" -> "Completing a task"
    "reschedule_task" -> "Rescheduling"
    "log_expense" -> "Logging the expense"
    "delete_last_expense" -> "Removing the expense"
    "expense_summary" -> "Reading your spending"
    "set_budget" -> "Setting the budget"
    "create_habit", "log_habit", "habit_status" -> "Updating habits"
    "create_note", "search_notes" -> "Working with notes"
    "append_journal" -> "Writing to your journal"
    "set_alarm" -> "Setting an alarm"
    "set_timer" -> "Starting a timer"
    "start_focus" -> "Starting focus"
    "get_weather" -> "Checking the weather"
    "list_events", "create_event" -> "Checking your calendar"
    "get_shloka" -> "Finding a shloka"
    "remember_fact", "forget_fact" -> "Updating memory"
    "weekly_review" -> "Reviewing your week"
    else -> "Working"
}
