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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.FocusPhase
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.Review
import com.k.sarathi.domain.WeatherCodes
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.Routes
import com.k.sarathi.ui.components.GhostButton
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.NavRow
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.PrimaryButton
import com.k.sarathi.ui.components.RingGauge
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.Stat
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.rememberNow
import com.k.sarathi.ui.theme.Sar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FocusScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() }, periodMs = 1000)
    val haptic = LocalHapticFeedback.current
    val f = db.focus
    val s = db.settings
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(s.focusMin) }
    val nowMs = now.toInstant().toEpochMilli()
    val running = f.phase != FocusPhase.IDLE
    val remaining = if (running) (f.endsAt - nowMs).coerceAtLeast(0) else minutes * 60_000L
    val total = if (running) (f.endsAt - f.startedAt).coerceAtLeast(1) else minutes * 60_000L
    val progress = if (running) 1f - remaining.toFloat() / total else 0f
    val finishedFocus = running && remaining == 0L
    val today = now.toLocalDate()
    val todaySessions = db.focusLog.filter { it.start.toZoned(now.zone).toLocalDate() == today }

    LaunchedEffect(finishedFocus) {
        if (finishedFocus) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            e.device.stopFocus(completed = true)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { ScreenHeader("Focus", onBack = if (e.nav.isTab("focus")) null else ({ e.nav.back() }), subtitle = "${todaySessions.sumOf { it.minutes }} min today · ${todaySessions.count { it.completed }} sessions", modifier = Modifier.padding(start = 0.dp)) }
        item {
            Spacer(Modifier.height(10.dp))
            val color = if (f.phase == FocusPhase.BREAK) c.good else c.accent
            RingGauge(progress, size = 260.dp, stroke = 12.dp, color = color) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mm = remaining / 60_000; val ss = (remaining / 1000) % 60
                    Text("%02d:%02d".format(mm, ss), style = Sar.t.clock.copy(fontSize = sp(48f)), color = c.text)
                    Text(
                        when (f.phase) { FocusPhase.FOCUS -> f.label.ifBlank { "Deep focus" }; FocusPhase.BREAK -> "Break — breathe"; FocusPhase.IDLE -> "Ready" }.uppercase(),
                        style = Sar.t.label, color = color, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (!running) {
            item {
                SarField(label, { label = it }, placeholder = "What are you focusing on?")
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 50, 90).forEach { m -> Pill("$m min", selected = minutes == m, onClick = { minutes = m }) }
                }
                PrimaryButton("Start focus", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    e.device.startFocus(minutes, label)
                }
                Text("Sarathi will chime when time's up, even if the app is closed.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 10.dp), textAlign = TextAlign.Center)
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(if (f.phase == FocusPhase.BREAK) "End break" else "End early", Modifier.weight(1f), icon = Icons.Rounded.Stop, color = c.bad) {
                        e.device.stopFocus(completed = false)
                    }
                    if (f.phase == FocusPhase.FOCUS) GhostButton("Break now", Modifier.weight(1f), icon = Icons.Rounded.Coffee, color = c.good) {
                        e.device.stopFocus(completed = false); e.device.startBreak(s.breakMin)
                    }
                }
                Text("Ends at ${Fmt.time(f.endsAt.toZoned(now.zone), s)}", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (!running && db.focusLog.lastOrNull()?.let { it.completed && nowMs - (it.start + it.minutes * 60_000L) < 10 * 60_000 } == true) {
            item {
                HudCard(Modifier.padding(top = 16.dp), accent = c.good) {
                    Text("Session complete ✓", style = Sar.t.title, color = c.text)
                    Text("Take a ${s.breakMin}-minute break before the next one.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(vertical = 6.dp))
                    GhostButton("Start ${s.breakMin} min break", Modifier.fillMaxWidth(), icon = Icons.Rounded.Coffee, color = c.good) { e.device.startBreak(s.breakMin) }
                }
            }
        }
        if (todaySessions.isNotEmpty()) {
            item { SectionLabel("Today") }
            todaySessions.reversed().forEach { sess ->
                item(key = "f${sess.id}") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = if (sess.completed) c.good else c.textFaint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(sess.label, style = Sar.t.body, color = c.text, modifier = Modifier.weight(1f))
                        Text("${sess.minutes} min · ${Fmt.time(sess.start.toZoned(now.zone), s)}", style = Sar.t.small, color = c.textDim)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val today = e.repo.clock.today()
    val r = Review.week(db, today, e.repo.clock.zone())
    val cur = db.settings.currency
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)) {
        item {
            ScreenHeader("Weekly review", onBack = { e.nav.back() }, subtitle = "Last 7 days", modifier = Modifier.padding(start = 0.dp)) {
                IconButton(onClick = { e.platform.share(Review.text(r, cur)) }) { Icon(Icons.Rounded.Share, "Share", tint = c.textDim) }
            }
        }
        item {
            HudCard(accent = c.accent) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${r.tasksDone}", "Tasks done", color = c.accent)
                    Stat("${(r.habitRate * 100).roundToInt()}%", "Habits", color = c.good)
                    Stat("${r.focusMinutes}m", "Focus", color = c.accent2)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(Money.fmt(r.spent, cur), "Spent")
                    Stat("${r.journalDays}/7", "Journal")
                    Stat("${r.shlokasRead}", "Shlokas")
                }
            }
        }
        item { SectionLabel("Highlights") }
        item {
            HudCard {
                val lines = buildList {
                    r.bestHabit?.let { add("🔥 Longest streak: ${it.first}, ${it.second} days") }
                    r.topCategory?.let { add("${it.first.emoji} Most spending went to ${it.first.label} (${Money.fmt(it.second, cur)})") }
                    if (r.focusSessions > 0) add("⏱ ${r.focusSessions} focus sessions, about ${r.focusMinutes / r.focusSessions.coerceAtLeast(1)} min each")
                    if (r.tasksOpen > 0) add("☐ ${r.tasksOpen} tasks still open")
                    if (isEmpty()) add("A quiet week. Start small: one habit, one task, one focus session.")
                }
                lines.forEachIndexed { i, l -> Text(l, style = Sar.t.body, color = c.text, modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp)) }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            GhostButton("Ask Sarathi for one improvement", Modifier.fillMaxWidth(), icon = Icons.Rounded.Psychology) {
                e.bus.chatDraft = "Review my week (use weekly_review) and suggest one specific improvement for next week."
                e.nav.go("sarathi")
            }
        }
    }
}

@Composable
fun WeatherScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val w = db.weather
    val now = e.repo.clock.now()
    fun refresh() { loading = true; scope.launch { runCatching { e.device.weather() }; loading = false } }
    LaunchedEffect(Unit) { if (w == null || e.repo.clock.millis() - w.at > 30 * 60_000) refresh() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Weather", onBack = { e.nav.back() }, subtitle = db.settings.city) {
            IconButton(onClick = ::refresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "Refresh", tint = c.textDim) }
        }
        if (w == null) {
            Text(if (loading) "Fetching weather…" else "Weather unavailable. Check the connection or your city in Settings.", style = Sar.t.body, color = c.textDim, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(WeatherCodes.emoji(w.code, now.hour !in 6..18), style = Sar.t.clock)
            Text("${w.tempC.roundToInt()}°", style = Sar.t.clock.copy(fontSize = sp(72f)), color = c.text)
            Text(WeatherCodes.label(w.code).replaceFirstChar { it.uppercase() }, style = Sar.t.title, color = c.textDim)
            Text("Feels like ${w.feelsC.roundToInt()}° · ${w.minC.roundToInt()}° / ${w.maxC.roundToInt()}°", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherTile(Icons.Rounded.WaterDrop, "${w.rainPct}%", "Rain", Modifier.weight(1f))
                WeatherTile(Icons.Rounded.Thermostat, "${w.humidity}%", "Humidity", Modifier.weight(1f))
                WeatherTile(Icons.Rounded.Air, "${w.windKmh.roundToInt()}", "km/h", Modifier.weight(1f))
            }
            w.tomorrowMaxC?.let { t ->
                HudCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("TOMORROW", style = Sar.t.label, color = c.textDim)
                    Text("${WeatherCodes.emoji(w.tomorrowCode ?: 0)}  Up to ${t.roundToInt()}°, ${WeatherCodes.label(w.tomorrowCode ?: 0)}", style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (WeatherCodes.isRainy(w.code) || w.rainPct >= 60) {
                Text("☔ Carry an umbrella today.", style = Sar.t.bodyStrong, color = c.accent, modifier = Modifier.padding(top = 16.dp))
            }
            Text("Updated ${Fmt.time(w.at.toZoned(now.zone), db.settings)} · Open-Meteo", style = Sar.t.small, color = c.textFaint, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun WeatherTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val c = Sar.c
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.accent)
        Text(value, style = Sar.t.num.copy(fontSize = sp(18f), fontWeight = FontWeight.Bold), color = c.text, modifier = Modifier.padding(top = 6.dp))
        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(10f)), color = c.textDim)
    }
}

@Composable
fun MoreScreen() {
    val e = env
    val c = Sar.c
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(3) }) { ScreenHeader("More", onBack = null, subtitle = "Every module, one tap away", modifier = Modifier.padding(start = 0.dp)) }
        items(Routes.modules) { m ->
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { e.nav.go(m.route) }.padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(m.icon, null, tint = c.accent)
                }
                Spacer(Modifier.height(8.dp))
                Text(m.label.uppercase(), style = Sar.t.label.copy(fontSize = sp(12f)), color = c.text)
            }
        }
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.padding(top = 12.dp)) {
                NavRow("Settings", "Theme, AI, voice, bottom bar, backup", Icons.Rounded.Settings) { e.nav.go("settings") }
                NavRow("About Sarathi", "Version ${e.platform.appVersion()} · what's new", Icons.Rounded.Info) { e.nav.go("settings/about") }
            }
        }
    }
}
