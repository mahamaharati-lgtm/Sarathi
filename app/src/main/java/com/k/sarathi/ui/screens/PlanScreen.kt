package com.k.sarathi.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.Db
import com.k.sarathi.data.Habit
import com.k.sarathi.data.Priority
import com.k.sarathi.data.Recur
import com.k.sarathi.data.TaskItem
import com.k.sarathi.domain.Streaks
import com.k.sarathi.domain.Tasks
import com.k.sarathi.domain.TimeParse
import com.k.sarathi.domain.iso
import com.k.sarathi.domain.toDate
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.components.EmptyState
import com.k.sarathi.ui.components.GhostButton
import com.k.sarathi.ui.components.Heatmap
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.Stat
import com.k.sarathi.ui.components.SwipeToDelete
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.rememberNow
import com.k.sarathi.ui.theme.Sar
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlanScreen() {
    val c = Sar.c
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Plan", onBack = null) {
            if (tab == 0) IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "New task", tint = c.textDim) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tasks", selected = tab == 0, leading = Icons.Rounded.Checklist, onClick = { tab = 0 })
            Pill("Habits", selected = tab == 1, leading = Icons.Rounded.LocalFireDepartment, onClick = { tab = 1 })
        }
        if (tab == 0) TasksPane() else HabitsScreen(standalone = false)
    }
    if (adding) TaskSheet(null) { adding = false }
}

@Composable
private fun TasksPane() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() })
    val zone = now.zone
    val today = now.toLocalDate()
    val haptic = LocalHapticFeedback.current
    val askNotif = rememberNotificationAsk()
    var quick by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<TaskItem?>(null) }
    var showDone by remember { mutableStateOf(false) }
    val parsed = remember(quick) { TimeParse.find(quick, now) }

    fun addQuick() {
        val text = quick.trim()
        if (text.isEmpty()) return
        val title = (parsed?.rest?.ifBlank { null } ?: text).replaceFirstChar { it.uppercase() }
        val t = e.repo.addTask(title, parsed?.at?.toInstant()?.toEpochMilli(), parsed?.hasTime == false, parsed?.hasTime == true)
        if (t.remind) askNotif()
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        quick = ""
    }

    val overdue = Tasks.sort(Tasks.overdue(db, now))
    val todays = Tasks.sort(Tasks.dueOn(db, today, zone).filterNot { t -> overdue.any { it.id == t.id } })
    val tomorrow = Tasks.sort(Tasks.dueOn(db, today.plusDays(1), zone))
    val later = Tasks.sort(Tasks.open(db).filter { t -> t.due?.toDate(zone)?.isAfter(today.plusDays(1)) == true })
    val someday = Tasks.sort(Tasks.someday(db))
    val done = db.tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)) {
        item {
            SarField(
                quick, { quick = it }, placeholder = "Add a task — “pay rent on the 1st”",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addQuick() }),
                trailing = { IconButton(onClick = ::addQuick, enabled = quick.isNotBlank()) { Icon(Icons.Rounded.Add, "Add", tint = if (quick.isBlank()) c.textFaint else c.accent) } },
            )
            parsed?.let { p ->
                Text("📅 " + whenLabel(p.at, !p.hasTime, db.settings.use24h) + (if (p.hasTime) " · reminder" else "") + " · “${p.rest}”",
                    style = Sar.t.small, color = c.accent, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }
        }
        if (Tasks.open(db).isEmpty()) item {
            EmptyState(Icons.Rounded.Checklist, "Nothing on your plate", "Add tasks above, or tell Sarathi “remind me to call amma at 6pm”.")
        }
        section("Overdue", overdue, c.bad, now, onEdit = { edit = it })
        section("Today", todays, c.accent, now, onEdit = { edit = it })
        section("Tomorrow", tomorrow, c.accent, now, onEdit = { edit = it })
        section("Upcoming", later, c.accent2, now, onEdit = { edit = it })
        section("Someday", someday, c.textDim, now, onEdit = { edit = it })
        if (done.isNotEmpty()) {
            item {
                SectionLabel("Done · ${done.size}", action = if (showDone) "Clear" else "Show", onAction = {
                    if (showDone) { val snapshot = e.repo.db.tasks.filter { it.done }; e.repo.clearCompleted(); e.bus.undo("Cleared ${snapshot.size} done tasks") { snapshot.forEach(e.repo::restoreTask) } }
                    else showDone = true
                })
            }
            if (showDone) items(done.take(30), key = { "d${it.id}" }) { t -> TaskRow(t, now, Sar.c.good, onEdit = { edit = it }) }
        }
    }
    edit?.let { t -> TaskSheet(t) { edit = null } }
}

private fun LazyListScope.section(title: String, list: List<TaskItem>, color: Color, now: ZonedDateTime, onEdit: (TaskItem) -> Unit) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionLabel("$title · ${list.size}") }
    items(list, key = { it.id }) { t -> TaskRow(t, now, color, onEdit) }
}

@Composable
private fun TaskRow(t: TaskItem, now: ZonedDateTime, color: Color, onEdit: (TaskItem) -> Unit) {
    val e = env
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    val s = e.repo.db.settings
    SwipeToDelete(onDelete = {
        e.repo.deleteTask(t.id)?.let { x -> e.bus.undo("Deleted “${x.title}”") { e.repo.restoreTask(x) } }
    }, modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
                .clickable { onEdit(t) }.heightIn(min = 60.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(t.done, if (t.priority == Priority.HIGH && !t.done) c.bad else color, {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                if (t.done) e.repo.toggleTask(t.id)
                else e.repo.complete(t.id)?.let { done -> e.bus.undo("Done: ${t.title}") { e.repo.undoComplete(done) } }
            }, if (t.done) "Reopen ${t.title}" else "Complete ${t.title}")
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    t.title, style = Sar.t.bodyStrong.copy(textDecoration = if (t.done) TextDecoration.LineThrough else null),
                    color = if (t.done) c.textDim else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    t.due?.let { add(whenLabel(it.toZoned(now.zone), t.allDay, s.use24h)) }
                    if (t.recur != Recur.NONE) add(t.recur.name.lowercase())
                    if (t.notes.isNotBlank()) add(t.notes.lineSequence().first().take(40))
                }
                if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.remind && !t.done) Icon(Icons.Rounded.Notifications, "Reminder", tint = c.textFaint, modifier = Modifier.size(13.dp).padding(end = 3.dp))
                    if (t.recur != Recur.NONE) Icon(Icons.Rounded.Repeat, "Repeats", tint = c.textFaint, modifier = Modifier.size(13.dp).padding(end = 3.dp))
                    Text(meta.joinToString(" · "), style = Sar.t.small, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (t.priority == Priority.HIGH && !t.done) Box(Modifier.size(8.dp).clip(CircleShape).background(c.bad))
        }
    }
}

@Composable
fun HabitsScreen(standalone: Boolean) {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() })
    val today = now.toLocalDate()
    var newName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🧘") }
    var detail by remember { mutableStateOf<Long?>(null) }
    val habits = db.habits.filter { !it.archived }
    Column(Modifier.fillMaxSize()) {
        if (standalone) ScreenHeader("Habits", onBack = { e.nav.back() }, subtitle = "${habits.size} active")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SarField(
                    newName, { newName = it }, placeholder = "New habit — meditate, walk, read…",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank()) { e.repo.addHabit(newName.trim().replaceFirstChar { it.uppercase() }, emoji); newName = "" } }),
                    trailing = {
                        IconButton(onClick = { e.repo.addHabit(newName.trim().replaceFirstChar { it.uppercase() }, emoji); newName = "" }, enabled = newName.isNotBlank()) {
                            Icon(Icons.Rounded.Add, "Add habit", tint = if (newName.isBlank()) c.textFaint else c.accent)
                        }
                    },
                )
                if (newName.isNotBlank()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🧘", "🚶", "📖", "💧", "🏋️", "🛏", "🥗", "✍️", "🙏", "📵", "🎵", "✦").forEach { em -> Pill(em, selected = emoji == em, onClick = { emoji = em }) }
                }
            }
            if (habits.isEmpty()) item { EmptyState(Icons.Rounded.LocalFireDepartment, "Build a streak", "Small daily habits compound. Add one above — and streak freezes protect you on rough days.") }
            items(habits, key = { it.id }) { h -> HabitCard(h, today, onOpen = { detail = h.id }) }
        }
    }
    detail?.let { id -> db.habits.firstOrNull { it.id == id }?.let { HabitSheet(it, today) { detail = null } } }
}

@Composable
private fun HabitCard(h: Habit, today: LocalDate, onOpen: () -> Unit) {
    val e = env
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    val streak = Streaks.current(h, today)
    val atRisk = Streaks.atRisk(h, today)
    HudCard(onClick = onOpen, accent = if (atRisk) c.warn else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.emoji, style = Sar.t.title)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = Sar.t.bodyStrong, color = c.text)
                Text(
                    if (atRisk) "Streak at risk — still time today" else "Best ${Streaks.best(h)}d · ${(Streaks.rate(h, today) * 100).roundToInt()}% last 30 days",
                    style = Sar.t.small, color = if (atRisk) c.warn else c.textDim,
                )
            }
            Text("🔥 $streak", style = Sar.t.num.copy(fontWeight = FontWeight.Bold), color = if (streak > 0) c.warn else c.textFaint)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 6 downTo 0) {
                val d = today.minusDays(i.toLong())
                val done = d.iso() in h.done
                val frozen = d.iso() in h.frozen
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = Sar.t.label.copy(fontSize = sp(11f)), color = if (i == 0) c.accent else c.textFaint)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(when { done -> c.good; frozen -> c.accent2.copy(alpha = 0.3f); else -> c.surface })
                            .border(1.dp, if (i == 0 && !done) c.accent else c.line, CircleShape)
                            .clickable(role = Role.Checkbox) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                e.repo.toggleHabit(h.id, d)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            done -> Text("✓", style = Sar.t.bodyStrong, color = c.onAccent)
                            frozen -> Icon(Icons.Rounded.AcUnit, "Frozen", tint = c.accent2, modifier = Modifier.size(16.dp))
                            else -> Text("${d.dayOfMonth}", style = Sar.t.small, color = c.textDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitSheet(h: Habit, today: LocalDate, onDismiss: () -> Unit) {
    val e = env
    val c = Sar.c
    var name by remember { mutableStateOf(h.name) }
    val yesterday = today.minusDays(1)
    SarSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.emoji, style = Sar.t.headline)
            Spacer(Modifier.width(10.dp))
            SarField(name, { name = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${Streaks.current(h, today)}d", "Current", color = c.warn)
            Stat("${Streaks.best(h)}d", "Best")
            Stat("${(Streaks.rate(h, today) * 100).roundToInt()}%", "30 days")
            Stat("${h.freezes}", "Freezes", color = c.accent2)
        }
        Text("LAST 12 WEEKS", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(bottom = 8.dp))
        Heatmap(today, 12, color = c.good) { d -> if (d.iso() in h.done) 1f else if (d.iso() in h.frozen) 0.25f else 0f }
        Spacer(Modifier.height(16.dp))
        if (yesterday.iso() !in h.done && yesterday.iso() !in h.frozen && Streaks.current(h, yesterday.minusDays(1)) > 0) {
            GhostButton("Use a freeze for yesterday (${h.freezes} left)", Modifier.fillMaxWidth(), icon = Icons.Rounded.AcUnit, color = c.accent2) {
                if (e.repo.freezeHabit(h.id, yesterday)) e.bus.snack("Streak protected ❄") else e.bus.snack("No freezes left")
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Delete", icon = Icons.Rounded.Delete, color = c.bad) {
                e.repo.deleteHabit(h.id)?.let { x -> e.bus.undo("Deleted ${x.name}") { e.repo.restoreHabit(x) } }
                onDismiss()
            }
            GhostButton("Archive", icon = Icons.Rounded.Archive, color = c.textDim) { e.repo.updateHabit(h.copy(archived = true)); onDismiss() }
            com.k.sarathi.ui.components.PrimaryButton("Save", Modifier.weight(1f), enabled = name.isNotBlank()) { e.repo.updateHabit(h.copy(name = name.trim())); onDismiss() }
        }
        Spacer(Modifier.height(8.dp))
    }
}
