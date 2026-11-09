package com.k.sarathi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.JournalEntry
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.components.EmptyState
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.PrimaryButton
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.SwipeToDelete
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.theme.Sar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotesScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    var q by remember { mutableStateOf("") }
    val notes = db.notes.filter { q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) }
        .sortedWith(compareByDescending<com.k.sarathi.data.Note> { it.pinned }.thenByDescending { it.updated })
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Notes", onBack = if (e.nav.isTab("notes")) null else ({ e.nav.back() }), subtitle = "${db.notes.size} notes")
            LazyVerticalStaggeredGrid(
                StaggeredGridCells.Fixed(2), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) { SarField(q, { q = it }, placeholder = "Search notes") }
                if (notes.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(Icons.Rounded.EditNote, if (q.isBlank()) "No notes yet" else "No matches", "Capture ideas here, or say “note: buy marigolds for Diwali”.")
                }
                items(notes, key = { it.id }) { n ->
                    HudCard(onClick = { e.nav.go("note/${n.id}") }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(n.title.ifBlank { "Untitled" }, style = Sar.t.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (n.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = c.accent, modifier = Modifier.size(14.dp))
                        }
                        if (n.body.isNotBlank() && n.body != n.title) Text(n.body, style = Sar.t.small, color = c.textDim, maxLines = 7, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(Fmt.shortDay(n.updated.toZoned(e.repo.clock.zone())), style = Sar.t.label.copy(fontSize = sp(10f)), color = c.textFaint, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        Fab("Note", Modifier.align(Alignment.BottomEnd)) { e.nav.go("note/new") }
    }
}

@Composable
fun Fab(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sar.c
    Row(
        modifier.navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(c.accent, c.accent2))).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.onAccent)
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(14f)), color = c.onAccent)
    }
}

@Composable
fun NoteEditorScreen(id: Long?) {
    val e = env
    val c = Sar.c
    val existing = id?.let { nid -> e.repo.db.notes.firstOrNull { it.id == nid } }
    var noteId by remember { mutableStateOf(existing?.id) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var deleted by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(Triple(title, body, pinned))

    fun persist() {
        if (deleted) return
        val (t, b, p) = latest
        if (t.isBlank() && b.isBlank()) return
        val cur = noteId?.let { nid -> e.repo.db.notes.firstOrNull { it.id == nid } }
        if (cur == null) noteId = e.repo.addNote(t, b).also { n -> if (p) e.repo.updateNote(n.copy(pinned = true)) }.id
        else if (cur.title != t || cur.body != b || cur.pinned != p) e.repo.updateNote(cur.copy(title = t, body = b, pinned = p))
    }
    DisposableEffect(Unit) { onDispose { persist() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(if (existing == null) "New note" else "Note", onBack = { persist(); e.nav.back() }) {
            IconButton(onClick = { pinned = !pinned }) { Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin", tint = if (pinned) c.accent else c.textDim) }
            IconButton(onClick = { e.platform.share(listOf(title, body).filter { it.isNotBlank() }.joinToString("\n\n")) }) { Icon(Icons.Rounded.Share, "Share", tint = c.textDim) }
            IconButton(onClick = {
                deleted = true
                noteId?.let { nid -> e.repo.deleteNote(nid)?.let { n -> e.bus.undo("Note deleted") { e.repo.restoreNote(n) } } }
                e.nav.back()
            }) { Icon(Icons.Rounded.Delete, "Delete", tint = c.textDim) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)) {
            BasicTextField(
                title, { title = it }, Modifier.fillMaxWidth(), textStyle = Sar.t.title.copy(color = c.text),
                cursorBrush = SolidColor(c.accent), singleLine = true,
                decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", style = Sar.t.title, color = c.textFaint); inner() } },
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                body, { body = it }, Modifier.fillMaxWidth().weight(1f), textStyle = Sar.t.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner -> Box { if (body.isEmpty()) Text("Write anything…", style = Sar.t.body, color = c.textFaint); inner() } },
            )
        }
    }
}

private val MOODS = listOf("😞", "😕", "😐", "🙂", "😄")

@Composable
fun JournalScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val today = e.repo.clock.today()
    val pending = remember { e.bus.journalPrompt.also { e.bus.journalPrompt = null } }
    val ref = pending?.substringBefore('|')
    val prompt = pending?.substringAfter('|') ?: e.content.promptOfDay(today)
    val shloka = ref?.let { e.content.byRef(it) }
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(3) }
    var edit by remember { mutableStateOf<JournalEntry?>(null) }
    val entries = db.journal.sortedByDescending { it.created }
    val streak = run {
        val days = db.journal.map { it.date }.toSet()
        var d = if (today.toString() in days) today else today.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        n
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp)) {
        item { ScreenHeader("Journal", onBack = if (e.nav.isTab("journal")) null else ({ e.nav.back() }), subtitle = if (streak > 0) "✍️ $streak-day writing streak" else "${entries.size} entries", modifier = Modifier.padding(start = 0.dp)) }
        item {
            HudCard(accent = c.accent) {
                Text("TODAY'S REFLECTION", style = Sar.t.label, color = c.accent)
                Text(prompt, style = Sar.t.title, color = c.text, modifier = Modifier.padding(top = 6.dp))
                shloka?.let { Text("Gita ${it.ref}: ${it.en}", style = Sar.t.small.copy(fontStyle = FontStyle.Italic), color = c.textDim, modifier = Modifier.padding(top = 6.dp)) }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    MOODS.forEachIndexed { i, m ->
                        val sel = mood == i + 1
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(if (sel) c.accentSoft else c.surface)
                                .border(1.dp, if (sel) c.accent else c.line, CircleShape)
                                .clickable(role = Role.RadioButton) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); mood = i + 1 },
                            contentAlignment = Alignment.Center,
                        ) { Text(m, style = Sar.t.title) }
                    }
                }
                SarField(text, { text = it }, placeholder = "Write freely…", singleLine = false, minLines = 4)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Save entry", Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                    e.repo.addJournal(text, mood, ref)
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    text = ""
                    e.bus.snack("Saved to your journal")
                }
            }
        }
        if (entries.isNotEmpty()) item { SectionLabel("Past entries") }
        if (entries.isEmpty()) item { EmptyState(Icons.Rounded.AutoStories, "Your journal is empty", "A few honest lines a day. Sarathi will offer a prompt from the Gita.") }
        items(entries, key = { it.id }) { j ->
            SwipeToDelete(onDelete = { e.repo.deleteJournal(j.id)?.let { x -> e.bus.undo("Entry deleted") { e.repo.restoreJournal(x) } } }, modifier = Modifier.padding(vertical = 4.dp)) {
                HudCard(onClick = { edit = j }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(MOODS.getOrElse(j.mood - 1) { "😐" }, style = Sar.t.title)
                        Spacer(Modifier.width(10.dp))
                        Text(dateLabel(j.date, today), style = Sar.t.label, color = c.textDim, modifier = Modifier.weight(1f))
                        j.shlokaRef?.let { Text("Gita $it", style = Sar.t.label.copy(fontSize = sp(11f)), color = c.accent) }
                    }
                    Text(j.text, style = Sar.t.body, color = c.text, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    edit?.let { j ->
        var t by remember(j.id) { mutableStateOf(j.text) }
        SarSheet({ edit = null }) {
            Text(dateLabel(j.date, today), style = Sar.t.title, color = c.text)
            Spacer(Modifier.height(10.dp))
            SarField(t, { t = it }, singleLine = false, minLines = 6)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save", Modifier.fillMaxWidth(), enabled = t.isNotBlank()) { e.repo.updateJournal(j.copy(text = t)); edit = null }
        }
    }
}

private fun dateLabel(iso: String, today: LocalDate): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
    }
}

@Composable
fun SimpleList(content: @Composable () -> Unit) = LazyColumn { item { content() } }
