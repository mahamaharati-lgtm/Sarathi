package com.k.sarathi.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.Content
import com.k.sarathi.data.Shloka
import com.k.sarathi.ui.components.GhostButton
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.RingGauge
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.theme.Sar

@Composable
fun GitaScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val all = e.content.shlokas
    var q by remember { mutableStateOf("") }
    var onlyBookmarks by remember { mutableStateOf(false) }
    var openChapter by remember { mutableStateOf<Int?>(null) }
    val today = e.repo.clock.today()
    val sod = e.content.shlokaOfDay(today)
    val read = db.gita.read
    val results = if (q.isBlank()) emptyList() else e.content.search(q)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)) {
        item {
            ScreenHeader("Bhagavad Gita", onBack = if (e.nav.isTab("gita")) null else ({ e.nav.back() }), subtitle = "${read.size} of ${all.size} shlokas read", modifier = Modifier.padding(start = 0.dp))
        }
        item {
            HudCard(onClick = { e.nav.go("shloka/${sod.ref}") }, accent = c.accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TODAY · ${sod.ref}", style = Sar.t.label, color = c.accent)
                        Text(sod.en, style = Sar.t.title, color = c.text, modifier = Modifier.padding(top = 6.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(12.dp))
                    RingGauge(read.size.toFloat() / all.size.coerceAtLeast(1), size = 64.dp) {
                        Text("${(read.size * 100 / all.size.coerceAtLeast(1))}%", style = Sar.t.num.copy(fontSize = sp(13f), fontWeight = FontWeight.Bold), color = c.text)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            SarField(q, { q = it }, placeholder = "Search meanings — courage, duty, fear…")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("All chapters", selected = !onlyBookmarks, onClick = { onlyBookmarks = false })
                Pill("Bookmarks · ${db.gita.bookmarks.size}", selected = onlyBookmarks, leading = Icons.Rounded.Bookmark, onClick = { onlyBookmarks = true })
            }
        }
        when {
            q.isNotBlank() -> {
                item { SectionLabel("${results.size} results") }
                items(results, key = { "r${it.ref}" }) { ShlokaRow(it, it.ref in read, it.ref in db.gita.bookmarks) }
            }
            onlyBookmarks -> {
                val list = all.filter { it.ref in db.gita.bookmarks }
                item { SectionLabel("Bookmarked") }
                if (list.isEmpty()) item { Text("Tap the bookmark on any shloka to keep it here.", style = Sar.t.small, color = c.textDim) }
                items(list, key = { "b${it.ref}" }) { ShlokaRow(it, it.ref in read, true) }
            }
            else -> {
                item { SectionLabel("Chapters") }
                e.content.chaptersPresent().forEach { ch ->
                    val inCh = e.content.inChapter(ch.n)
                    val readIn = inCh.count { it.ref in read }
                    item(key = "ch${ch.n}") {
                        HudCard(Modifier.padding(vertical = 4.dp), onClick = { openChapter = if (openChapter == ch.n) null else ch.n }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                                    Text("${ch.n}", style = Sar.t.num.copy(fontWeight = FontWeight.Bold), color = c.accent)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ch.english.replaceFirstChar { it.uppercase() }, style = Sar.t.bodyStrong, color = c.text)
                                    Text(ch.sanskrit, style = Sar.t.small, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("$readIn/${inCh.size}", style = Sar.t.num.copy(fontSize = sp(13f)), color = if (readIn == inCh.size) c.good else c.textDim)
                            }
                        }
                    }
                    if (openChapter == ch.n) items(inCh, key = { "s${it.ref}" }) { ShlokaRow(it, it.ref in read, it.ref in db.gita.bookmarks) }
                }
            }
        }
    }
}

@Composable
private fun ShlokaRow(s: Shloka, read: Boolean, bookmarked: Boolean) {
    val e = env
    val c = Sar.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { e.nav.go("shloka/${s.ref}") }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.ref, style = Sar.t.num.copy(fontSize = sp(13f)), color = if (read) c.good else c.accent, modifier = Modifier.width(52.dp))
        Text(s.en, style = Sar.t.small, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (bookmarked) Icon(Icons.Rounded.Bookmark, "Bookmarked", tint = c.accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ShlokaScreen(ref: String) {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    var current by remember { mutableStateOf(ref) }
    val all = e.content.shlokas
    val idx = all.indexOfFirst { it.ref == current }.coerceAtLeast(0)
    val s = all[idx]
    val chapter = Content.CHAPTERS.firstOrNull { it.n == s.ch }
    val bookmarked = s.ref in db.gita.bookmarks
    LaunchedEffect(s.ref) { e.repo.markRead(s.ref) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Gita ${s.ref}", onBack = { e.voice.stopSpeaking(); e.nav.back() }, subtitle = chapter?.let { "Chapter ${it.n} · ${it.sanskrit}" }) {
            IconButton(onClick = { e.repo.toggleBookmark(s.ref) }) {
                Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Bookmark", tint = if (bookmarked) c.accent else c.textDim)
            }
            IconButton(onClick = { e.platform.share("${s.sa}\n\n${s.iast}\n\n${s.en}\n— Bhagavad Gita ${s.ref}") }) { Icon(Icons.Rounded.Share, "Share", tint = c.textDim) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("${s.speaker.uppercase()} SAID", style = Sar.t.label, color = c.accent, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(
                s.sa, style = Sar.t.body.copy(fontFamily = FontFamily.Default, fontSize = sp(21f), lineHeight = sp(34f), fontWeight = FontWeight.Medium),
                color = c.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (db.settings.showIast) {
                Spacer(Modifier.height(14.dp))
                Text(s.iast, style = Sar.t.body.copy(fontStyle = FontStyle.Italic), color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(22.dp))
            HudCard(accent = c.accent) {
                Text("MEANING", style = Sar.t.label, color = c.textDim)
                Text(s.en, style = Sar.t.title, color = c.text, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Listen", Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.VolumeUp) { e.voice.speak(s.en) }
                GhostButton("Reflect", Modifier.weight(1f), icon = Icons.Rounded.EditNote) {
                    e.bus.journalPrompt = "${s.ref}|${e.content.promptOfDay(e.repo.clock.today())}"; e.nav.go("journal")
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Ask Sarathi about this verse", Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.MenuBook) {
                e.bus.chatDraft = "Explain Bhagavad Gita ${s.ref} and how I can apply it today."
                e.nav.go("sarathi")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (idx > 0) current = all[idx - 1].ref }, enabled = idx > 0) { Icon(Icons.Rounded.ChevronLeft, "Previous shloka", tint = if (idx > 0) c.accent else c.textFaint) }
            Text("${idx + 1} / ${all.size}", style = Sar.t.num, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (idx < all.lastIndex) current = all[idx + 1].ref }, enabled = idx < all.lastIndex) { Icon(Icons.Rounded.ChevronRight, "Next shloka", tint = if (idx < all.lastIndex) c.accent else c.textFaint) }
        }
    }
}
