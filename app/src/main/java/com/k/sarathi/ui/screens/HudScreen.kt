package com.k.sarathi.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.sarathi.ai.CalEvent
import com.k.sarathi.data.FocusPhase
import com.k.sarathi.data.Habit
import com.k.sarathi.data.Priority
import com.k.sarathi.data.Recur
import com.k.sarathi.data.TaskItem
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.Streaks
import com.k.sarathi.domain.Tasks
import com.k.sarathi.domain.WeatherCodes
import com.k.sarathi.domain.iso
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.RingGauge
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.hudBackdrop
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.components.tapAndHold
import com.k.sarathi.ui.env
import com.k.sarathi.ui.rememberNow
import com.k.sarathi.ui.theme.Sar
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private sealed interface TItem {
    val sortKey: Long
    data class TaskRow(val t: TaskItem, val overdue: Boolean) : TItem { override val sortKey = if (overdue) Long.MIN_VALUE + (t.due ?: 0) else (t.due ?: Long.MAX_VALUE) }
    data class EventRow(val ev: CalEvent) : TItem { override val sortKey = if (ev.allDay) Long.MIN_VALUE / 2 else ev.start }
    data class FocusRow(val endsAt: Long, val label: String, val breakPhase: Boolean) : TItem { override val sortKey = Long.MIN_VALUE / 4 }
}

@Composable
fun HudScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() })
    val s = db.settings
    val today = now.toLocalDate()
    val zone = now.zone
    val haptic = LocalHapticFeedback.current
    var editTask by remember { mutableStateOf<TaskItem?>(null) }
    var addTask by remember { mutableStateOf(false) }
    var addExpense by remember { mutableStateOf(false) }
    var calGranted by remember { mutableStateOf(e.platform.hasPermission(Manifest.permission.READ_CALENDAR)) }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calGranted = it }
    val events = remember(today, calGranted, db.tasks.size) { if (calGranted) e.device.events(today).orEmpty() else emptyList() }
    LaunchedEffect(s.city) {
        val w = db.weather
        if (w == null || w.city != s.city || e.repo.clock.millis() - w.at > 30 * 60_000) runCatching { e.device.weather() }
    }

    val overdue = Tasks.overdue(db, now)
    val dueToday = Tasks.dueOn(db, today, zone).filterNot { t -> overdue.any { it.id == t.id } }
    val doneToday = Tasks.doneOn(db, today, zone)
    val habits = db.habits.filter { !it.archived }
    val habitsDone = habits.count { today.iso() in it.done }
    val focusToday = db.focusLog.filter { it.start.toZoned(zone).toLocalDate() == today }.sumOf { it.minutes }
    val focusGoal = (s.focusMin * 4).coerceAtLeast(30)
    val money = Money.month(db, now)
    val nextUp = (dueToday.filter { !it.allDay && (it.due ?: 0) > now.toInstant().toEpochMilli() }.minByOrNull { it.due!! })

    val items = buildList<TItem> {
        overdue.forEach { add(TItem.TaskRow(it, true)) }
        dueToday.forEach { add(TItem.TaskRow(it, false)) }
        events.forEach { add(TItem.EventRow(it)) }
        if (db.focus.phase != FocusPhase.IDLE) add(TItem.FocusRow(db.focus.endsAt, db.focus.label, db.focus.phase == FocusPhase.BREAK))
    }.sortedBy { it.sortKey }

    val shloka = e.content.shlokaOfDay(today)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        Modifier.fillMaxSize().hudBackdrop(c.line.copy(alpha = if (c.isDark) 0.35f else 0.5f), c.glow.copy(alpha = 0.18f)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = 120.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.greeting(now)}, ${s.userName}", style = Sar.t.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.day(now), style = Sar.t.small, color = c.textDim)
                }
                db.weather?.let { w ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.surfaceHi).border(1.dp, c.line, RoundedCornerShape(50))
                            .clickable { e.nav.go("weather") }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(WeatherCodes.emoji(w.code, now.hour !in 6..18), style = Sar.t.body)
                        Spacer(Modifier.width(6.dp))
                        Text("${w.tempC.roundToInt()}°", style = Sar.t.num, color = c.text)
                    }
                }
                IconButton(onClick = { e.bus.paletteOpen = true }) { Icon(Icons.Rounded.Search, "Search and commands", tint = c.textDim) }
                IconButton(onClick = { e.nav.go("settings") }) { Icon(Icons.Rounded.Settings, "Settings", tint = c.textDim) }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.clock(now, s), style = Sar.t.clock, color = c.text)
                    Text(Fmt.ampm(now, s), style = Sar.t.label.copy(fontSize = sp(16f)), color = c.accent, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                }
                val next = nextUp?.let { "Next: ${it.title} · ${Fmt.time(it.due!!.toZoned(zone), s)}" }
                    ?: if (overdue.isNotEmpty()) "${overdue.size} overdue — clear one now?" else "Clear road ahead."
                Text(next, style = Sar.t.small, color = if (nextUp == null && overdue.isNotEmpty()) c.warn else c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(50))
                    .clickable { e.bus.paletteOpen = true }.heightIn(min = 52.dp).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ask Sarathi or type a command…", style = Sar.t.body, color = c.textFaint, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.accent).clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        e.bus.listenOnOpen = true; e.nav.go("sarathi")
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Mic, "Speak to Sarathi", tint = c.onAccent) }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val taskTotal = doneToday.size + dueToday.size + overdue.size
                Ring("Tasks", if (taskTotal == 0) 0f else doneToday.size.toFloat() / taskTotal, "${doneToday.size}/$taskTotal", c.accent) { e.nav.go("plan") }
                Ring("Habits", if (habits.isEmpty()) 0f else habitsDone.toFloat() / habits.size, "$habitsDone/${habits.size}", c.good) { e.nav.go("habits") }
                Ring("Focus", focusToday.toFloat() / focusGoal, "${focusToday}m", c.accent2) { e.nav.go("focus") }
                Ring(
                    "Budget", if (money.budget > 0) money.usedFraction else 0f,
                    if (money.budget > 0) "${(money.usedFraction * 100).roundToInt()}%" else "Set",
                    when { money.budget <= 0 -> c.textDim; money.usedFraction > 1f -> c.bad; money.overPace -> c.warn; else -> c.good },
                ) { e.nav.go("money") }
            }
        }

        item { SectionLabel("Today", action = "Add", onAction = { addTask = true }) }
        item {
            HudCard(padding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                if (items.isEmpty()) {
                    Text(
                        if (doneToday.isNotEmpty()) "All clear — ${doneToday.size} done today. Rest your horses. 🐎" else "Nothing scheduled. Add a task or ask Sarathi to plan your day.",
                        style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(14.dp),
                    )
                }
                items.forEach { it ->
                    when (it) {
                        is TItem.TaskRow -> TimelineTask(it.t, it.overdue, now, onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            e.repo.complete(it.t.id)?.let { done -> e.bus.undo("Done: ${it.t.title}") { e.repo.undoComplete(done) } }
                        }, onOpen = { editTask = it.t }, onSnooze = { e.repo.snoozeTask(it.t.id, 60); e.bus.snack("Snoozed 1 hour") })
                        is TItem.EventRow -> TimelineRow(
                            time = if (it.ev.allDay) "ALL DAY" else Fmt.time(it.ev.start.toZoned(zone), s), timeColor = c.accent2,
                            title = it.ev.title, meta = it.ev.location.ifBlank { "Calendar" }, icon = Icons.Rounded.Event,
                        )
                        is TItem.FocusRow -> TimelineRow(
                            time = "NOW", timeColor = c.good,
                            title = if (it.breakPhase) "Break" else "Focus" + (if (it.label.isNotBlank()) " · ${it.label}" else ""),
                            meta = "Ends ${Fmt.time(it.endsAt.toZoned(zone), s)}", icon = Icons.Rounded.Timer, onClick = { e.nav.go("focus") },
                        )
                    }
                }
                if (!calGranted) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { runCatching { calLauncher.launch(Manifest.permission.READ_CALENDAR) } }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Show calendar events here", style = Sar.t.small, color = c.accent)
                    }
                }
            }
        }

        if (habits.isNotEmpty()) item {
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                habits.forEach { h -> HabitChip(h, today.iso() in h.done, Streaks.current(h, today)) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (e.repo.toggleHabit(h.id)) e.bus.snack("${h.emoji} ${h.name} ✓ · ${Streaks.current(e.repo.db.habits.first { it.id == h.id }, today)}-day streak")
                } }
            }
        }

        item { SectionLabel("Shloka of the day · ${shloka.ref}", action = "Gita", onAction = { e.nav.go("gita") }) }
        item {
            HudCard(onClick = { e.repo.markRead(shloka.ref); e.nav.go("shloka/${shloka.ref}") }, accent = c.accent) {
                Text(shloka.sa.lines().take(2).joinToString("\n"), style = Sar.t.body.copy(fontFamily = FontFamily.Default, fontSize = sp(17f), fontWeight = FontWeight.Medium, lineHeight = sp(27f)), color = c.text)
                if (s.showIast) Text(shloka.iast.lines().take(2).joinToString("\n"), style = Sar.t.small.copy(fontStyle = FontStyle.Italic), color = c.textDim, modifier = Modifier.padding(top = 6.dp))
                Text(shloka.en, style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("REFLECT", style = Sar.t.label, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        e.bus.journalPrompt = "${shloka.ref}|${e.content.promptOfDay(today)}"; e.nav.go("journal")
                    }.padding(6.dp))
                    Text("LISTEN", style = Sar.t.label, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { e.voice.speak(shloka.en) }.padding(6.dp))
                }
            }
        }

        item { SectionLabel("Quick") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Quick("Focus", Icons.Rounded.Timer, Modifier.weight(1f)) { e.nav.go("focus") }
                Quick("Expense", Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f)) { addExpense = true }
                Quick("Task", Icons.Rounded.AddTask, Modifier.weight(1f)) { addTask = true }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Quick("Journal", Icons.Rounded.AutoStories, Modifier.weight(1f)) { e.nav.go("journal") }
                Quick("Gita", Icons.AutoMirrored.Rounded.MenuBook, Modifier.weight(1f)) { e.nav.go("gita") }
                Quick("Review", Icons.Rounded.Insights, Modifier.weight(1f)) { e.nav.go("review") }
            }
        }

        item {
            Text("“${e.content.quoteOfDay(today)}”", style = Sar.t.small.copy(fontStyle = FontStyle.Italic), color = c.textDim, modifier = Modifier.padding(top = 22.dp, start = 8.dp, end = 8.dp))
        }
    }

    if (addTask) TaskSheet(null) { addTask = false }
    editTask?.let { t -> TaskSheet(t) { editTask = null } }
    if (addExpense) ExpenseSheet(null) { addExpense = false }
}

@Composable
private fun Ring(label: String, progress: Float, value: String, color: Color, onClick: () -> Unit) {
    val c = Sar.c
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RingGauge(progress, size = 70.dp, stroke = 6.dp, color = color) {
            Text(value, style = Sar.t.num.copy(fontSize = sp(14f), fontWeight = FontWeight.Bold), color = c.text, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(11f)), color = c.textDim)
    }
}

@Composable
private fun TimelineTask(t: TaskItem, overdue: Boolean, now: ZonedDateTime, onToggle: () -> Unit, onOpen: () -> Unit, onSnooze: () -> Unit) {
    val c = Sar.c
    val s = env.repo.db.settings
    val zone = now.zone
    val time = when {
        overdue -> "OVERDUE"
        t.allDay || t.due == null -> "TODAY"
        else -> Fmt.time(t.due.toZoned(zone), s)
    }
    val meta = buildList {
        if (overdue && t.due != null) add(whenLabel(t.due.toZoned(zone), t.allDay, s.use24h))
        if (t.remind) add("Reminder")
        if (t.recur != Recur.NONE) add(t.recur.name.lowercase())
        if (t.priority == Priority.HIGH) add("High")
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tapAndHold(onClick = onOpen, onLongClick = onSnooze).heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = Sar.t.num.copy(fontSize = sp(12f)), color = if (overdue) c.bad else c.accent, modifier = Modifier.width(72.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(t.title, style = Sar.t.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.remind) Icon(Icons.Rounded.Notifications, null, tint = c.textFaint, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                if (t.recur != Recur.NONE) Icon(Icons.Rounded.Repeat, null, tint = c.textFaint, modifier = Modifier.size(12.dp).padding(end = 2.dp))
                Text(meta, style = Sar.t.small, color = c.textDim, maxLines = 1)
            }
        }
        CheckCircle(false, if (t.priority == Priority.HIGH) c.bad else c.accent, onToggle, "Complete ${t.title}")
    }
}

@Composable
fun CheckCircle(checked: Boolean, color: Color, onClick: () -> Unit, label: String) {
    val c = Sar.c
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (checked) color else Color.Transparent).border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun TimelineRow(time: String, timeColor: Color, title: String, meta: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    val c = Sar.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = Sar.t.num.copy(fontSize = sp(12f)), color = timeColor, modifier = Modifier.width(72.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(title, style = Sar.t.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = Sar.t.small, color = c.textDim, maxLines = 1)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = timeColor, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun HabitChip(h: Habit, done: Boolean, streak: Int, onClick: () -> Unit) {
    val c = Sar.c
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (done) c.good.copy(alpha = 0.16f) else c.surface)
            .border(1.dp, if (done) c.good.copy(alpha = 0.6f) else c.line, RoundedCornerShape(50))
            .clickable(role = Role.Checkbox, onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(h.emoji, style = Sar.t.small)
        Spacer(Modifier.width(6.dp))
        Text(h.name, style = Sar.t.small.copy(fontWeight = FontWeight.Medium), color = if (done) c.text else c.textDim)
        if (streak > 0) Text("  🔥$streak", style = Sar.t.small, color = c.warn)
        if (done) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, "Done", tint = c.good, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun Quick(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Sar.c
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(11f)), color = c.text)
    }
}
