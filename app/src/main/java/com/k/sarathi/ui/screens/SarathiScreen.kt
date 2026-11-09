package com.k.sarathi.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.ChatMsg
import com.k.sarathi.data.ToolCard
import com.k.sarathi.domain.Models
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.Orb
import com.k.sarathi.ui.components.OrbMode
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.PrimaryButton
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.SwipeToDelete
import com.k.sarathi.ui.components.Waveform
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.theme.Fonts
import com.k.sarathi.ui.theme.Sar
import com.k.sarathi.ui.toolVerb
import com.k.sarathi.ui.Fmt
import com.k.sarathi.domain.toZoned

@Composable
fun SarathiScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val chat by e.brain.state.collectAsState()
    val voice by e.voice.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var input by remember { mutableStateOf(e.bus.chatDraft) }
    val conv = chat.conversationId?.let { id -> db.conversations.firstOrNull { it.id == id } }
    val messages = conv?.messages.orEmpty().filter { !it.hidden && it.display.isNotBlank() || it.cards.isNotEmpty() && !it.hidden }
    val list = rememberLazyListState()
    val s = db.settings
    val hasKey = e.brain.hasKey

    fun send(text: String = input, fromVoice: Boolean = false) {
        if (text.isBlank()) return
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        e.brain.send(text, fromVoice)
        input = ""
        e.bus.chatDraft = ""
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) e.voice.listen { said -> send(said, fromVoice = true) }
        else e.bus.snack("Sarathi needs the microphone to hear you", "Settings") { e.platform.openAppSettings() }
    }
    fun startListening() {
        if (e.platform.hasPermission(Manifest.permission.RECORD_AUDIO)) e.voice.listen { said -> send(said, fromVoice = true) }
        else runCatching { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    fun mic() {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (voice.listening) e.voice.stopListening() else startListening()
    }

    LaunchedEffect(Unit) {
        if (e.bus.listenOnOpen) { e.bus.listenOnOpen = false; startListening() }
    }
    LaunchedEffect(messages.size, chat.streaming.length / 40, chat.busy) {
        val total = messages.size + (if (chat.busy) 1 else 0)
        if (total > 0) list.animateScrollToItem(total - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            "Sarathi", onBack = null,
            subtitle = if (hasKey) "${Models.get(s.model).name} · ${s.effort.label}" else "Offline commands",
        ) {
            IconButton(onClick = { e.nav.go("chats") }) { Icon(Icons.Rounded.History, "Chat history", tint = c.textDim) }
            IconButton(onClick = { e.brain.newChat() }) { Icon(Icons.Rounded.Add, "New chat", tint = c.textDim) }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasKey) item {
                HudCard(accent = c.accent2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = c.accent2)
                        Spacer(Modifier.width(10.dp))
                        Text("Offline mode", style = Sar.t.bodyStrong, color = c.text)
                    }
                    Text("Instant commands work without internet. Connect Claude to plan, answer and act on anything.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    PrimaryButton("Add API key", Modifier.fillMaxWidth()) { e.nav.go("settings/ai") }
                }
            }
            if (messages.isEmpty() && !chat.busy) item { EmptyChat(hasKey, s.userName, s.animations) { send(it) } }
            items(messages, key = { "${it.ts}-${it.role}-${it.display.hashCode()}" }) { m -> Bubble(m) }
            if (chat.busy) item { Draft(chat.streaming, chat.tool, s.animations) }
        }

        chat.notice?.let { Banner(it, c.accent2, null) { e.brain.dismissError() } }
        chat.error?.let { err -> Banner(err, c.bad, if (chat.lastFailedText != null) "Retry" else null, onAction = { e.brain.retry() }) { e.brain.dismissError() } }

        AnimatedVisibility(voice.listening) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Waveform(voice.level)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(voice.partial.ifBlank { "Listening…" }, style = Sar.t.body, color = if (voice.partial.isBlank()) c.textDim else c.text, modifier = Modifier.weight(1f))
                    Pill(if (voice.conversation) "Hands-free on" else "Hands-free", selected = voice.conversation, leading = Icons.Rounded.AllInclusive,
                        onClick = { e.voice.setConversation(!voice.conversation) })
                }
            }
        }
        voice.error?.let { Text(it, style = Sar.t.small, color = c.warn, modifier = Modifier.padding(horizontal = 20.dp)) }

        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SarField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = if (hasKey) "Ask or tell Sarathi…" else "Try “spent 120 on coffee”",
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(8.dp))
            val sending = input.isNotBlank()
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (voice.listening) c.bad else if (sending) c.accent else c.surfaceHi)
                    .border(1.dp, if (sending || voice.listening) androidx.compose.ui.graphics.Color.Transparent else c.line, CircleShape)
                    .clickable { if (chat.busy) e.brain.cancel() else if (sending) send() else mic() },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    chat.busy -> Icon(Icons.Rounded.Stop, "Stop", tint = c.text)
                    voice.listening -> Icon(Icons.Rounded.Stop, "Stop listening", tint = c.onAccent)
                    sending -> Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = c.onAccent)
                    else -> Icon(Icons.Rounded.Mic, "Speak", tint = c.accent)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun EmptyChat(hasKey: Boolean, name: String, animate: Boolean, onPick: (String) -> Unit) {
    val c = Sar.c
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Orb(size = 140.dp, animate = animate)
        Spacer(Modifier.height(16.dp))
        Text("Namaste, $name.", style = Sar.t.headline, color = c.text)
        Text("I'm Sarathi — your charioteer. Where are we headed today?", style = Sar.t.small, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp))
        Spacer(Modifier.height(18.dp))
        val ideas = if (hasKey) listOf("Plan my day", "How much did I spend this week?", "Remind me to pay rent on the 1st every month", "A shloka for courage", "Start a 50 minute focus on my report")
        else listOf("spent 200 on lunch", "remind me to call amma at 6pm", "timer 10 min", "15% of 2000", "open gita")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas.forEach { Pill(it, onClick = { onPick(it) }) }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val c = Sar.c
    val mine = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (m.display.isNotBlank()) {
            if (mine) {
                Text(
                    m.display, style = Sar.t.body, color = c.text,
                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .background(c.accentSoft).border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp).alpha(if (m.excluded) 0.6f else 1f),
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (m.model == "on-device") c.good else c.accent))
                    Spacer(Modifier.width(10.dp))
                    Text(md(m.display), style = Sar.t.body, color = c.text, modifier = Modifier.widthIn(max = 330.dp))
                }
            }
        }
        m.cards.forEach { Card(it) }
        if (!mine && m.model == "on-device") Text("instant · on-device", style = Sar.t.label.copy(fontSize = sp(10f)), color = c.textFaint, modifier = Modifier.padding(start = 18.dp, top = 2.dp))
    }
}

@Composable
private fun Card(card: ToolCard) {
    val c = Sar.c
    Row(
        Modifier.padding(start = 18.dp, top = 6.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, (if (card.ok) c.good else c.bad).copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (card.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (card.ok) c.good else c.bad, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(card.title, style = Sar.t.bodyStrong, color = c.text)
            if (card.detail.isNotBlank()) Text(card.detail, style = Sar.t.small, color = c.textDim)
        }
    }
}

@Composable
private fun Draft(text: String, tool: String?, animate: Boolean) {
    val c = Sar.c
    val inf = rememberInfiniteTransition(label = "cursor")
    val blink by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "blink")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Orb(size = 26.dp, mode = OrbMode.THINKING, animate = animate)
            Spacer(Modifier.width(8.dp))
            Text("${toolVerb(tool)}…", style = Sar.t.label, color = c.accent)
        }
        if (text.isNotBlank()) {
            Text(
                buildAnnotatedString { append(md(text)); withStyle(SpanStyle(color = c.accent.copy(alpha = if (animate) blink else 1f))) { append(" ▍") } },
                style = Sar.t.body, color = c.text, modifier = Modifier.padding(start = 18.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color, action: String?, onAction: () -> Unit = {}, onDismiss: () -> Unit) {
    val c = Sar.c
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Sar.t.small, color = c.text, modifier = Modifier.weight(1f))
        if (action != null) Text(action.uppercase(), style = Sar.t.label, color = color, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
    }
}

/** Minimal markdown: **bold**, `code`, and "- " bullets. */
@Composable
fun md(text: String): AnnotatedString {
    val c = Sar.c
    return buildAnnotatedString {
        text.lines().forEachIndexed { i, raw ->
            if (i > 0) append("\n")
            val line = raw.replace(Regex("^\\s*[-*•]\\s+"), "• ").replace(Regex("^#{1,6}\\s+"), "")
            var rest = line
            val re = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")
            while (true) {
                val m = re.find(rest) ?: break
                append(rest.substring(0, m.range.first))
                if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                else withStyle(SpanStyle(fontFamily = Fonts.Mono, color = c.accent)) { append(m.groupValues[2]) }
                rest = rest.substring(m.range.last + 1)
            }
            append(rest)
        }
    }
}

@Composable
fun ChatsScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Conversations", onBack = { e.nav.back() }, subtitle = "${db.conversations.size} chats") {
            IconButton(onClick = { e.brain.newChat(); e.nav.go("sarathi") }) { Icon(Icons.Rounded.Add, "New chat", tint = c.textDim) }
        }
        if (db.conversations.isEmpty()) com.k.sarathi.ui.components.EmptyState(Icons.Rounded.History, "No chats yet", "Conversations with Sarathi appear here.")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(db.conversations, key = { it.id }) { conv ->
                SwipeToDelete(onDelete = {
                    e.repo.deleteConversation(conv.id)
                    e.bus.undo("Chat deleted") { e.repo.upsertConversation(conv) }
                }) {
                    HudCard(onClick = { e.brain.open(conv.id); e.nav.go("sarathi") }) {
                        Text(conv.title, style = Sar.t.bodyStrong, color = c.text, maxLines = 1)
                        val last = conv.messages.lastOrNull { !it.hidden && it.display.isNotBlank() }?.display ?: ""
                        Text(last, style = Sar.t.small, color = c.textDim, maxLines = 2)
                        Text(Fmt.shortDay(conv.updated.toZoned(e.repo.clock.zone())) + " · ${conv.messages.count { !it.hidden }} messages", style = Sar.t.label.copy(fontSize = sp(11f)), color = c.textFaint, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
