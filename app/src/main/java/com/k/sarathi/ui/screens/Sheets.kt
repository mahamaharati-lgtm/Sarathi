package com.k.sarathi.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.Category
import com.k.sarathi.data.Expense
import com.k.sarathi.data.Priority
import com.k.sarathi.data.Recur
import com.k.sarathi.data.TaskItem
import com.k.sarathi.domain.Commands
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.Recurrence
import com.k.sarathi.domain.TimeParse
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.components.GhostButton
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.PrimaryButton
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.theme.Sar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Composable
fun SarSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Sar.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.text,
        dragHandle = { Spacer(Modifier.padding(top = 10.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/** Asks for the notification permission the first time a reminder is saved (Android 13+). */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val e = env
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) e.bus.snack("Reminders will be silent without notification permission", "Settings") { e.platform.openAppSettings() }
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !e.platform.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
}

/** Add or edit a task. Typing "call amma tomorrow 6pm" fills the date and time. */
@Composable
fun TaskSheet(existing: TaskItem?, onDismiss: () -> Unit) {
    val e = env
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    val zone = e.repo.clock.zone()
    val s = e.repo.db.settings
    val askNotif = rememberNotificationAsk()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var due by remember { mutableStateOf(existing?.due?.toZoned(zone)) }
    var allDay by remember { mutableStateOf(existing?.allDay ?: false) }
    var remind by remember { mutableStateOf(existing?.remind ?: false) }
    var manualTime by remember { mutableStateOf(existing?.due != null) }
    var priority by remember { mutableStateOf(existing?.priority ?: Priority.NORMAL) }
    var recur by remember { mutableStateOf(existing?.recur ?: Recur.NONE) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }

    val parsed = remember(title) { if (manualTime) null else TimeParse.find(title, e.repo.clock.now()) }
    val effDue = parsed?.at ?: due
    val effAllDay = if (parsed != null) !parsed.hasTime else allDay
    val effRemind = if (parsed != null && !manualTime) parsed.hasTime || remind else remind

    fun save() {
        val t = (parsed?.rest?.takeIf { it.isNotBlank() } ?: title).trim()
        if (t.isEmpty()) return
        val dueMs = effDue?.toInstant()?.toEpochMilli()
        val rem = effRemind && dueMs != null
        if (existing == null) {
            e.repo.addTask(t.replaceFirstChar { it.uppercase() }, dueMs, effAllDay, rem, priority, recur, notes)
            e.bus.snack("Task added")
        } else {
            e.repo.updateTask(existing.copy(title = t, due = dueMs, allDay = effAllDay, remind = rem, priority = priority, recur = recur, notes = notes))
        }
        if (rem) askNotif()
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onDismiss()
    }

    SarSheet(onDismiss) {
        Text(if (existing == null) "New task" else "Edit task", style = Sar.t.title, color = c.text)
        Spacer(Modifier.height(12.dp))
        SarField(title, { title = it }, Modifier.focusRequester(focus), placeholder = "e.g. Call amma tomorrow 6pm", singleLine = false)
        if (parsed != null) {
            Text(
                "📅 " + whenLabel(parsed.at, !parsed.hasTime, s.use24h) + (if (parsed.hasTime) " · reminder" else ""),
                style = Sar.t.small, color = c.accent, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val today = e.repo.clock.today()
            fun setDay(d: LocalDate) {
                manualTime = true
                val t = due?.toLocalTime() ?: LocalTime.of(9, 0)
                due = ZonedDateTime.of(d, t, zone); allDay = due?.toLocalTime() == LocalTime.of(9, 0) && !remind
            }
            Pill("Today", selected = effDue?.toLocalDate() == today, onClick = { setDay(today) })
            Pill("Tomorrow", selected = effDue?.toLocalDate() == today.plusDays(1), onClick = { setDay(today.plusDays(1)) })
            Pill("Weekend", onClick = { setDay(today.plusDays(((6 - today.dayOfWeek.value + 7) % 7).toLong().let { if (it == 0L) 7 else it })) })
            Pill(effDue?.let { Fmt.shortDay(it) } ?: "Pick date", leading = Icons.Rounded.CalendarMonth, selected = effDue != null && manualTime, onClick = { pickDate = true })
            Pill(if (effDue != null && !effAllDay) Fmt.time(effDue, s) else "Time", leading = Icons.Rounded.Schedule, selected = effDue != null && !effAllDay, onClick = { pickTime = true })
            if (effDue != null) Pill("No date", leading = Icons.Rounded.EventBusy, onClick = { manualTime = true; due = null; remind = false; title = parsed?.rest ?: title })
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remind me", style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
            IconButton(onClick = { manualTime = true; if (parsed != null) { due = parsed.at; allDay = !parsed.hasTime; title = parsed.rest }; remind = !effRemind }, enabled = effDue != null) {
                Icon(if (effRemind && effDue != null) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, if (effRemind) "Reminder on" else "Reminder off",
                    tint = if (effRemind && effDue != null) c.accent else c.textFaint)
            }
        }
        Text("PRIORITY", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("High", selected = priority == Priority.HIGH, color = c.bad, onClick = { priority = Priority.HIGH })
            Pill("Normal", selected = priority == Priority.NORMAL, onClick = { priority = Priority.NORMAL })
            Pill("Low", selected = priority == Priority.LOW, color = c.textDim, onClick = { priority = Priority.LOW })
        }
        Text("REPEAT", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Recur.entries.forEach { r -> Pill(Recurrence.label(r), selected = recur == r, leading = if (r == Recur.NONE) null else Icons.Rounded.Repeat, onClick = { recur = r }) }
        }
        Spacer(Modifier.height(14.dp))
        SarField(notes, { notes = it }, placeholder = "Notes (optional)", singleLine = false, minLines = 2)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) GhostButton("Delete", icon = Icons.Rounded.Delete, color = c.bad, onClick = {
                e.repo.deleteTask(existing.id)?.let { t -> e.bus.undo("Deleted “${t.title}”") { e.repo.restoreTask(t) } }
                onDismiss()
            })
            PrimaryButton(if (existing == null) "Add task" else "Save", Modifier.weight(1f), enabled = title.isNotBlank(), onClick = ::save)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (effDue ?: e.repo.clock.now()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        manualTime = true
                        if (parsed != null) title = parsed.rest
                        val t = effDue?.toLocalTime() ?: LocalTime.of(9, 0)
                        due = ZonedDateTime.of(d, t, zone)
                        allDay = effAllDay || effDue == null
                    }
                    pickDate = false
                }) { Text("OK", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.textDim) } },
            colors = DatePickerDefaults.colors(containerColor = c.surface),
        ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = c.surface, selectedDayContainerColor = c.accent, todayDateBorderColor = c.accent)) }
    }
    if (pickTime) {
        val base = effDue ?: e.repo.clock.now().plusHours(1).withMinute(0)
        val st = rememberTimePickerState(base.hour, base.minute, is24Hour = s.use24h)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = c.surface,
            confirmButton = {
                TextButton(onClick = {
                    manualTime = true
                    if (parsed != null) title = parsed.rest
                    val d = effDue?.toLocalDate() ?: e.repo.clock.today()
                    var z = ZonedDateTime.of(d, LocalTime.of(st.hour, st.minute), zone)
                    if (effDue == null && z.isBefore(e.repo.clock.now())) z = z.plusDays(1)
                    due = z; allDay = false; remind = true
                    pickTime = false
                }) { Text("OK", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel", color = c.textDim) } },
            text = { TimePicker(st, colors = TimePickerDefaults.colors(clockDialColor = c.surfaceHi, selectorColor = c.accent, timeSelectorSelectedContainerColor = c.accentSoft)) },
        )
    }
}

fun whenLabel(at: ZonedDateTime, allDay: Boolean, use24h: Boolean): String {
    val today = ZonedDateTime.now(at.zone).toLocalDate()
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> Fmt.shortDay(at)
    }
    if (allDay) return day
    val t = at.format(java.time.format.DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", java.util.Locale.ENGLISH))
    return "$day $t"
}

/** Add or edit an expense: big amount, category chips (auto-suggested from the note). */
@Composable
fun ExpenseSheet(existing: Expense?, onDismiss: () -> Unit) {
    val e = env
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    val cur = e.repo.db.settings.currency
    var amount by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var cat by remember { mutableStateOf(existing?.category) }
    var dayOffset by remember { mutableStateOf(0) }
    val suggested = remember(note) { if (note.isBlank()) null else Commands.categoryFor(note).takeIf { it != Category.OTHER } }
    val effCat = cat ?: suggested ?: Category.OTHER
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }
    val value = amount.replace(",", "").toDoubleOrNull()

    SarSheet(onDismiss) {
        Text(if (existing == null) "Add expense" else "Edit expense", style = Sar.t.title, color = c.text)
        Spacer(Modifier.height(12.dp))
        SarField(
            amount, { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }.take(12) }, Modifier.focusRequester(focus),
            placeholder = "0", textStyle = Sar.t.bigNum,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailing = { Text(cur, style = Sar.t.title, color = c.textDim, modifier = Modifier.padding(end = 12.dp)) },
        )
        Spacer(Modifier.height(12.dp))
        SarField(note, { note = it }, placeholder = "What for? e.g. lunch, uber, rent")
        Text("CATEGORY", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { k -> Pill("${k.emoji} ${k.label}", selected = effCat == k, onClick = { cat = k; haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }) }
        }
        if (existing == null) {
            Text("WHEN", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Today", selected = dayOffset == 0, onClick = { dayOffset = 0 })
                Pill("Yesterday", selected = dayOffset == 1, onClick = { dayOffset = 1 })
                Pill("2 days ago", selected = dayOffset == 2, onClick = { dayOffset = 2 })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) GhostButton("Delete", icon = Icons.Rounded.Delete, color = c.bad, onClick = {
                e.repo.deleteExpense(existing.id)?.let { x -> e.bus.undo("Removed ${Money.fmt(x.amount, cur)}") { e.repo.restoreExpense(x) } }
                onDismiss()
            })
            PrimaryButton(
                if (existing == null) "Add ${value?.let { Money.fmt(it, cur) } ?: ""}".trim() else "Save",
                Modifier.weight(1f), enabled = value != null && value > 0,
            ) {
                val v = value ?: return@PrimaryButton
                if (existing == null) {
                    val at = e.repo.clock.now().minusDays(dayOffset.toLong()).toInstant().toEpochMilli()
                    e.repo.logExpense(v, effCat, note.trim().replaceFirstChar { it.uppercase() }, at)
                    val m = Money.month(e.repo.db, e.repo.clock.now())
                    e.bus.snack("${effCat.emoji} ${Money.fmt(v, cur)} logged" + if (m.budget > 0) " · ${Money.fmt(m.left, cur)} left" else "")
                } else e.repo.updateExpense(existing.copy(amount = v, category = effCat, note = note.trim()))
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismiss()
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Single number input dialog (budgets etc.). */
@Composable
fun AmountDialog(title: String, initial: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val c = Sar.c
    var v by remember { mutableStateOf(if (initial > 0) initial.toLong().toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = Sar.t.title, color = c.text) },
        text = {
            Column {
                SarField(v, { s -> v = s.filter { it.isDigit() }.take(9) }, placeholder = "0", textStyle = Sar.t.bigNum.copy(fontSize = sp(28f)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Leave empty or 0 to clear.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v.toDoubleOrNull() ?: 0.0); onDismiss() }) { Text("SAVE", color = c.accent, style = Sar.t.label) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = c.textDim, style = Sar.t.label) } },
    )
}

@Composable
fun ChipToggleRow(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, (label, sel) -> Pill(label, selected = sel, onClick = { onPick(i) }) }
    }
}
