package com.k.sarathi.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k.sarathi.core.AppJson
import com.k.sarathi.data.Db
import com.k.sarathi.data.Effort
import com.k.sarathi.data.ThemeChoice
import com.k.sarathi.domain.Models
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Routes
import com.k.sarathi.ui.components.GhostButton
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.NavRow
import com.k.sarathi.ui.components.Orb
import com.k.sarathi.ui.components.Pill
import com.k.sarathi.ui.components.PrimaryButton
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.SettingSwitch
import com.k.sarathi.ui.components.Stat
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.theme.Palettes
import com.k.sarathi.ui.theme.Sar
import java.time.YearMonth
import java.util.Locale

@Composable
fun SettingsScreen(section: String?) {
    val e = env
    val title = when (section) {
        "profile" -> "Profile"; "appearance" -> "Appearance"; "bar" -> "Bottom bar"; "ai" -> "AI · Claude"
        "voice" -> "Voice"; "focus" -> "Focus & briefing"; "money" -> "Money"; "memory" -> "Memory"
        "backup" -> "Backup & restore"; "about" -> "About"; else -> "Settings"
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(title, onBack = { e.nav.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)) {
            item {
                when (section) {
                    null -> SettingsIndex()
                    "profile" -> ProfileSection()
                    "appearance" -> AppearanceSection()
                    "bar" -> BarSection()
                    "ai" -> AiSection()
                    "voice" -> VoiceSection()
                    "focus" -> FocusSection()
                    "money" -> MoneySection()
                    "memory" -> MemorySection()
                    "backup" -> BackupSection()
                    "about" -> AboutSection()
                }
            }
        }
    }
}

@Composable
private fun SettingsIndex() {
    val e = env
    val db by e.repo.store.state.collectAsState()
    val s = db.settings
    Column {
        NavRow("Profile", "${s.userName} · ${s.city}", Icons.Rounded.Person) { e.nav.go("settings/profile") }
        NavRow("Appearance", "${s.theme.label} theme · animations, haptics, sounds", Icons.Rounded.Palette) { e.nav.go("settings/appearance") }
        NavRow("Bottom bar", s.tabs.joinToString(" · ") { Routes.info(it)?.label ?: it }, Icons.Rounded.ViewWeek) { e.nav.go("settings/bar") }
        NavRow("AI · Claude", if (e.brain.hasKey) "${Models.get(s.model).name} · ${s.effort.label}" else "Not connected — offline commands only", Icons.Rounded.Psychology) { e.nav.go("settings/ai") }
        NavRow("Voice", "Speech ${s.sttLang} · speak replies ${if (s.speakReplies) "on" else "off"}", Icons.Rounded.RecordVoiceOver) { e.nav.go("settings/voice") }
        NavRow("Focus & briefing", "${s.focusMin}/${s.breakMin} min · briefing ${if (s.briefing) s.briefingTime else "off"}", Icons.Rounded.Timer) { e.nav.go("settings/focus") }
        NavRow("Money", "Currency ${s.currency}", Icons.Rounded.AccountBalanceWallet) { e.nav.go("settings/money") }
        NavRow("Memory", "${db.memory.size} things Sarathi remembers", Icons.Rounded.Memory) { e.nav.go("settings/memory") }
        NavRow("Backup & restore", "Export everything to a file", Icons.Rounded.Backup) { e.nav.go("settings/backup") }
        NavRow("About", "Sarathi ${e.platform.appVersion()}", Icons.Rounded.Info) { e.nav.go("settings/about") }
    }
}

@Composable
private fun ProfileSection() {
    val e = env
    val s = e.repo.db.settings
    var name by remember { mutableStateOf(s.userName) }
    var city by remember { mutableStateOf(s.city) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SarField(name, { name = it }, label = "Your name")
        SarField(city, { city = it }, label = "City (for weather)")
        PrimaryButton("Save", Modifier.fillMaxWidth(), enabled = name.isNotBlank() && city.isNotBlank()) {
            e.repo.settings { it.copy(userName = name.trim(), city = city.trim()) }
            e.weatherRefresh.value++
            e.bus.snack("Saved"); e.nav.back()
        }
    }
}

@Composable
private fun AppearanceSection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val s = db.settings
    Column {
        SectionLabel("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeChoice.entries.forEach { t -> ThemeTile(t, s.theme == t) { e.repo.settings { it.copy(theme = t) } } }
        }
        Text(
            "Auto follows the sun: Saffron at dawn, Arc by day, Ember at dusk, Nebula at night.",
            style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 10.dp),
        )
        SettingSwitch("Day theme in daylight", "Auto uses the light Day theme from 9 am to 5 pm — easier to read outdoors", s.dayThemeInDaylight) { v -> e.repo.settings { it.copy(dayThemeInDaylight = v) } }
        SectionLabel("Feel")
        SettingSwitch("Animations", "Orb, rings and transitions. Off saves battery.", s.animations) { v -> e.repo.settings { it.copy(animations = v) } }
        SettingSwitch("Haptics", "Gentle ticks on taps and completions", s.haptics) { v -> e.repo.settings { it.copy(haptics = v) } }
        SettingSwitch("UI sounds", "Soft synthesized blips", s.sounds) { v -> e.repo.settings { it.copy(sounds = v) } }
        SettingSwitch("24-hour clock", null, s.use24h) { v -> e.repo.settings { it.copy(use24h = v) } }
        SettingSwitch("Show IAST transliteration", "Roman letters under Sanskrit shlokas", s.showIast) { v -> e.repo.settings { it.copy(showIast = v) } }
    }
}

@Composable
private fun ThemeTile(t: ThemeChoice, selected: Boolean, onClick: () -> Unit) {
    val c = Sar.c
    val p = if (t == ThemeChoice.AUTO) null else Palettes.of(t)
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(16.dp)).border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(
                if (p == null) Brush.horizontalGradient(listOf(Palettes.Saffron.bg2, Palettes.Arc.bg2, Palettes.Ember.bg2, Palettes.Nebula.bg2))
                else Brush.verticalGradient(listOf(p.surfaceHi, p.bg)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (p == null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Palettes.Saffron, Palettes.Arc, Palettes.Ember, Palettes.Nebula).forEach { q -> Box(Modifier.size(12.dp).clip(CircleShape).background(q.accent)) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(p.accent))
                Box(Modifier.size(18.dp).clip(CircleShape).background(p.accent2))
            }
        }
        Text(t.label.uppercase(), style = Sar.t.label.copy(fontSize = sp(12f)), color = if (selected) c.accent else c.text, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BarSection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val tabs = db.settings.tabs.let { if (it.size < 2) listOf("plan", "money") else it }
    Column {
        Text("Pin your two most-used modules beside the Sarathi orb. HUD and More always stay.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
        listOf("Left of the orb", "Right of the orb").forEachIndexed { slot, label ->
            SectionLabel(label)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Routes.pinnable.forEach { r ->
                    val info = Routes.info(r)!!
                    val other = tabs[1 - slot]
                    Pill(info.label, selected = tabs[slot] == r, leading = info.icon, onClick = {
                        if (r != other) e.repo.settings { s -> s.copy(tabs = tabs.toMutableList().also { it[slot] = r }) }
                        else e.repo.settings { s -> s.copy(tabs = listOf(tabs[1], tabs[0])) }
                    })
                }
            }
        }
    }
}

@Composable
private fun AiSection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val s = db.settings
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(e.keys.get() != null) }
    var capText by remember { mutableStateOf(if (s.monthlyCapUsd > 0) s.monthlyCapUsd.toString() else "") }
    val now = e.repo.clock.now()
    val ym = YearMonth.from(now)
    val month = db.usage.filter { YearMonth.from(it.at.toZoned(now.zone)) == ym }
    val spend = month.sumOf { it.usd }
    Column {
        HudCard(accent = if (hasKey) c.good else c.accent2) {
            Text(if (hasKey) "Connected to Claude" else "Connect Claude", style = Sar.t.title, color = c.text)
            Text(
                if (hasKey) "Your key is encrypted with the Android Keystore and never leaves this phone except to call Anthropic."
                else "Create an API key at console.anthropic.com and paste it here. It is stored encrypted on this phone.",
                style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(vertical = 8.dp),
            )
            SarField(key, { key = it.trim() }, placeholder = if (hasKey) "Replace key (sk-ant-…)" else "sk-ant-…", visualTransformation = PasswordVisualTransformation())
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasKey) GhostButton("Remove", color = c.bad) { e.keys.set(null); hasKey = false; e.bus.snack("Key removed") }
                PrimaryButton("Save key", Modifier.weight(1f), enabled = key.startsWith("sk-") && key.length > 20) {
                    e.keys.set(key); key = ""; hasKey = true; e.bus.snack("Claude connected")
                }
            }
        }
        SectionLabel("Model")
        Models.all.forEach { m ->
            val sel = s.model == m.id
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) c.accentSoft else c.surface)
                    .border(1.dp, if (sel) c.accent else c.line, RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) { e.repo.settings { it.copy(model = m.id) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.name + if (m.id == Models.DEFAULT) " · default" else "", style = Sar.t.bodyStrong, color = c.text)
                    Text(m.blurb, style = Sar.t.small, color = c.textDim)
                }
                Text("$${fmt(m.price.input)} / $${fmt(m.price.output)}", style = Sar.t.num.copy(fontSize = sp(12f)), color = c.textDim)
            }
        }
        Text("Prices per million input / output tokens.", style = Sar.t.small, color = c.textFaint, modifier = Modifier.padding(top = 4.dp))
        SectionLabel("Thinking effort")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Effort.entries.forEach { ef -> Pill(ef.label, selected = s.effort == ef, onClick = { e.repo.settings { it.copy(effort = ef) } }) }
        }
        Text(s.effort.blurb, style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
        SectionLabel("This month")
        HudCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("$" + String.format(Locale.US, "%.2f", spend), "Est. cost", color = c.accent)
                Stat("${month.size}", "Requests")
                Stat("${(month.sumOf { it.input + it.cacheRead + it.cacheWrite } / 1000)}k", "Input tok")
                Stat("${(month.sumOf { it.output } / 1000)}k", "Output tok")
            }
            val cached = month.sumOf { it.cacheRead }
            val totalIn = month.sumOf { it.input + it.cacheRead + it.cacheWrite }
            if (totalIn > 0) Text("${cached * 100 / totalIn}% of input served from the prompt cache.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 10.dp))
        }
        SectionLabel("Monthly cap")
        SarField(capText, { capText = it.filter { ch -> ch.isDigit() || ch == '.' } }, placeholder = "No cap", label = "USD per month",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        Text("Over the cap, Sarathi switches to Haiku 5.5 (about 40× cheaper) until next month.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
        GhostButton("Save cap", Modifier.padding(top = 8.dp)) { e.repo.settings { it.copy(monthlyCapUsd = capText.toDoubleOrNull() ?: 0.0) }; e.bus.snack("Cap saved") }
        SectionLabel("Replies")
        SettingSwitch("Speak replies", "Read every answer aloud (voice questions are always answered aloud)", s.speakReplies) { v -> e.repo.settings { it.copy(speakReplies = v) } }
    }
}

private fun fmt(v: Double) = if (v < 1) String.format(Locale.US, "%.2f", v) else String.format(Locale.US, "%.0f", v)

@Composable
private fun VoiceSection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val s = db.settings
    Column {
        SectionLabel("Speech recognition language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en-IN" to "English", "hi-IN" to "हिन्दी", "kn-IN" to "ಕನ್ನಡ").forEach { (code, label) ->
                Pill(label, selected = s.sttLang == code, onClick = { e.repo.settings { it.copy(sttLang = code) } })
            }
        }
        SectionLabel("Speaking rate · ${String.format(Locale.US, "%.1f", s.ttsRate)}×")
        Slider(
            s.ttsRate, { v -> e.repo.settings { it.copy(ttsRate = (v * 10).toInt() / 10f) } }, valueRange = 0.6f..1.6f,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
        )
        GhostButton("Test voice", Modifier.fillMaxWidth()) { e.voice.speak("Namaste ${s.userName}. I am Sarathi, your charioteer.") }
        Text("Tip: in the chat, turn on Hands-free and Sarathi keeps listening after each answer until you say “thank you”.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 5, onChange: (Int) -> Unit) {
    val c = Sar.c
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−", style = Sar.t.title, color = c.accent) }
        Text("$value min", style = Sar.t.num, color = c.text, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+", style = Sar.t.title, color = c.accent) }
    }
}

@Composable
private fun FocusSection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val s = db.settings
    Column {
        SectionLabel("Focus")
        Stepper("Focus length", s.focusMin, 5..180) { v -> e.repo.settings { it.copy(focusMin = v) } }
        Stepper("Break length", s.breakMin, 1..60, step = 1) { v -> e.repo.settings { it.copy(breakMin = v) } }
        SectionLabel("Morning briefing")
        SettingSwitch("Daily briefing", "A notification with today's tasks, habits, weather and shloka", s.briefing) { v -> e.repo.settings { it.copy(briefing = v) } }
        if (s.briefing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("06:00", "06:30", "07:00", "07:30", "08:00").forEach { t -> Pill(t, selected = s.briefingTime == t, onClick = { e.repo.settings { it.copy(briefingTime = t) } }) }
            }
        }
        Text("Briefings and reminders use exact alarms, so they arrive on time even in battery saver.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun MoneySection() {
    val e = env
    val db by e.repo.store.state.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    Column {
        SectionLabel("Currency")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("₹", "$", "€", "£").forEach { cur -> Pill(cur, selected = db.settings.currency == cur, onClick = { e.repo.settings { it.copy(currency = cur) } }) }
        }
        SectionLabel("Monthly budget")
        NavRow("Overall budget", if (db.budget.monthly > 0) com.k.sarathi.domain.Money.fmt(db.budget.monthly, db.settings.currency) else "Not set") { dialog = true }
    }
    if (dialog) AmountDialog("Monthly budget", db.budget.monthly, { dialog = false }) { v -> e.repo.setMonthlyBudget(v) }
}

@Composable
private fun MemorySection() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    var fact by remember { mutableStateOf("") }
    Column {
        Text("Facts Sarathi uses in new chats. Say “remember that I'm vegetarian” or add them here.", style = Sar.t.small, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        SarField(fact, { fact = it }, placeholder = "e.g. K prefers morning workouts")
        GhostButton("Remember", Modifier.padding(top = 8.dp)) { if (fact.isNotBlank()) { e.repo.remember(fact); fact = "" } }
        SectionLabel("${db.memory.size} remembered")
        db.memory.reversed().forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.text, style = Sar.t.body, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { e.repo.forget(m.id); e.bus.undo("Forgotten") { e.repo.remember(m.text) } }) { Icon(Icons.Rounded.Delete, "Forget", tint = c.textDim) }
            }
        }
    }
}

@Composable
private fun BackupSection() {
    val e = env
    val c = Sar.c
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Db?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val db = text?.let { runCatching { AppJson.decodeFromString(Db.serializer(), it) }.getOrNull() }
        if (db == null) e.bus.snack("That file isn't a Sarathi backup") else pending = db
    }
    Column {
        Text("A backup contains your tasks, habits, money, notes, journal, chats and settings. Your Claude API key is never included.", style = Sar.t.small, color = c.textDim)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Export backup", Modifier.fillMaxWidth(), icon = Icons.Rounded.Backup) {
            e.platform.shareFile("sarathi-backup-${e.repo.clock.today()}.json", "application/json", AppJson.encodeToString(Db.serializer(), e.repo.db))
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Restore from file", Modifier.fillMaxWidth()) { runCatching { open.launch(arrayOf("application/json", "text/plain", "*/*")) } }
    }
    pending?.let { db ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = c.surface,
            title = { Text("Restore backup?", style = Sar.t.title, color = c.text) },
            text = { Text("This replaces everything with the backup: ${db.tasks.size} tasks, ${db.expenses.size} expenses, ${db.notes.size} notes, ${db.journal.size} journal entries.", style = Sar.t.body, color = c.textDim) },
            confirmButton = { TextButton(onClick = { e.repo.store.replace(db); pending = null; e.bus.snack("Backup restored") }) { Text("RESTORE", color = c.bad, style = Sar.t.label) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("CANCEL", color = c.textDim, style = Sar.t.label) } },
        )
    }
}

@Composable
private fun AboutSection() {
    val e = env
    val c = Sar.c
    val s = e.repo.db.settings
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Orb(size = 110.dp, animate = s.animations)
        Text("Sarathi · सारथी", style = Sar.t.headline, color = c.text, modifier = Modifier.padding(top = 10.dp))
        Text("Version ${e.platform.appVersion()}", style = Sar.t.small, color = c.textDim)
        Text("Your charioteer: a personal assistant for plans, habits, money, focus and the Gita — instant on-device commands, with Claude for everything else.",
            style = Sar.t.small, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
    SectionLabel("New in 3.0")
    HudCard {
        listOf(
            "Rebuilt from the ground up in Jetpack Compose, with smooth animations and edge-to-edge layouts",
            "Today timeline on the HUD: overdue, timed tasks, calendar events, focus and habits in one place",
            "Bottom bar you can customise around the Sarathi orb",
            "New light Day theme, and an option to use it automatically in daylight",
            "Gita module: chapters, search, bookmarks, reading progress, listen and reflect",
            "Hands-free voice conversations",
            "Claude Opus 5.5 by default, plus Sonnet 5.5, Haiku 5.5 and Fable 5.1, with prompt caching and refusal fallbacks",
            "AI cost meter with a monthly cap",
            "Category budgets (envelopes) with pace markers and CSV export",
            "Undo for every delete, swipe gestures, and smarter natural-language input",
        ).forEach { Text("• $it", style = Sar.t.small, color = c.text, modifier = Modifier.padding(vertical = 3.dp)) }
    }
    SectionLabel("Credits")
    Text("Quotes are original to Sarathi. Shloka Sanskrit text is public domain; English meanings are original paraphrases. Fonts: Orbitron, Rajdhani, Inter, JetBrains Mono (SIL OFL). Icons: Material Symbols (Apache 2.0). Weather: Open-Meteo. AI: Claude by Anthropic.",
        style = Sar.t.small, color = c.textDim)
}

/** First run: welcome, name and city, theme, optional Claude key. */
@Composable
fun OnboardingScreen() {
    val e = env
    val c = Sar.c
    var step by remember { mutableStateOf(0) }
    val s = e.repo.db.settings
    var name by remember { mutableStateOf(if (s.userName == "K") "" else s.userName) }
    var city by remember { mutableStateOf(s.city) }
    var key by remember { mutableStateOf("") }
    val db by e.repo.store.state.collectAsState()
    fun finish() { e.repo.settings { it.copy(onboarded = true) } }
    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp).padding(top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i -> Box(Modifier.size(width = if (i == step) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i <= step) c.accent else c.line)) }
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                0 -> {
                    Orb(size = 180.dp, animate = db.settings.animations)
                    Spacer(Modifier.height(24.dp))
                    Text("Sarathi", style = Sar.t.display.copy(fontSize = sp(40f)), color = c.text)
                    Text("सारथी · your charioteer", style = Sar.t.title, color = c.accent)
                    Text("Plans, habits, money, focus and the Gita — guided by a calm voice that acts for you.", style = Sar.t.body, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                }
                1 -> {
                    Text("What should I call you?", style = Sar.t.headline, color = c.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    SarField(name, { name = it }, label = "Name")
                    Spacer(Modifier.height(12.dp))
                    SarField(city, { city = it }, label = "City, for weather")
                }
                2 -> {
                    Text("Choose your colours", style = Sar.t.headline, color = c.text)
                    Text("Auto changes with the time of day.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeChoice.entries.forEach { t -> ThemeTile(t, db.settings.theme == t) { e.repo.settings { it.copy(theme = t) } } }
                    }
                }
                else -> {
                    Text("Connect Claude", style = Sar.t.headline, color = c.text)
                    Text("Optional. Without a key Sarathi still understands instant commands offline. With one, it can plan, answer and act on anything.",
                        style = Sar.t.small, color = c.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
                    SarField(key, { key = it.trim() }, placeholder = "sk-ant-…", visualTransformation = PasswordVisualTransformation())
                    Text("Get a key at console.anthropic.com. It's stored encrypted on this phone.", style = Sar.t.small, color = c.textFaint, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) GhostButton("Back", color = c.textDim) { step-- }
            PrimaryButton(
                when (step) { 0 -> "Begin"; 3 -> if (key.isBlank()) "Skip for now" else "Finish"; else -> "Next" },
                Modifier.weight(1f), enabled = step != 1 || name.isNotBlank(),
            ) {
                when (step) {
                    1 -> { e.repo.settings { it.copy(userName = name.trim(), city = city.trim().ifBlank { "Bengaluru" }) }; step++ }
                    3 -> { if (key.startsWith("sk-")) e.keys.set(key); finish() }
                    else -> step++
                }
            }
        }
    }
}
