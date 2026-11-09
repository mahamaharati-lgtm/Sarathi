package com.k.sarathi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.rememberUpdatedState
import com.k.sarathi.ui.screens.SharedSheet
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.k.sarathi.domain.Calc
import com.k.sarathi.domain.Commands
import com.k.sarathi.domain.Screen
import com.k.sarathi.ui.components.Orb
import com.k.sarathi.ui.components.OrbMode
import com.k.sarathi.ui.components.SarField
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.screens.ChatsScreen
import com.k.sarathi.ui.screens.FocusScreen
import com.k.sarathi.ui.screens.GitaScreen
import com.k.sarathi.ui.screens.HabitsScreen
import com.k.sarathi.ui.screens.HudScreen
import com.k.sarathi.ui.screens.JournalScreen
import com.k.sarathi.ui.screens.MoneyScreen
import com.k.sarathi.ui.screens.MoreScreen
import com.k.sarathi.ui.screens.NoteEditorScreen
import com.k.sarathi.ui.screens.NotesScreen
import com.k.sarathi.ui.screens.OnboardingScreen
import com.k.sarathi.ui.screens.PlanScreen
import com.k.sarathi.ui.screens.ReviewScreen
import com.k.sarathi.ui.screens.SarathiScreen
import com.k.sarathi.ui.screens.SettingsScreen
import com.k.sarathi.ui.screens.ShlokaScreen
import com.k.sarathi.ui.screens.WeatherScreen
import com.k.sarathi.ui.theme.Palettes
import com.k.sarathi.ui.theme.Sar
import com.k.sarathi.ui.theme.SarathiTheme
import kotlinx.coroutines.launch

@Composable
fun SarathiRoot(e: AppEnv, onFeedback: (confirm: Boolean) -> Unit = {}) {
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() })
    val palette = Palettes.resolve(db.settings.theme, now.toLocalTime(), db.settings.dayThemeInDaylight)
    val base = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(db.settings.haptics)
    val feedback by rememberUpdatedState(onFeedback)
    // Haptics follow the setting, and confirmations get a soft sound.
    val haptics = remember(base) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticsOn) base.performHapticFeedback(hapticFeedbackType)
                if (hapticFeedbackType == HapticFeedbackType.Confirm) feedback(true)
            }
        }
    }
    SarathiTheme(palette) {
        CompositionLocalProvider(LocalEnv provides e, LocalHapticFeedback provides haptics) {
            Box(Modifier.fillMaxSize().background(Sar.c.bg)) {
                AnimatedContent(db.settings.onboarded, label = "onboard") { done ->
                    if (done) Shell() else OnboardingScreen()
                }
            }
        }
    }
}

@Composable
fun Shell() {
    val e = env
    val nav = e.nav
    val snackHost = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        e.bus.snacks.collect { s ->
            scope.launch {
                snackHost.currentSnackbarData?.dismiss()
                val r = snackHost.showSnackbar(s.message, actionLabel = s.action, duration = if (s.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) s.onAction?.invoke()
            }
        }
    }

    BackHandler(enabled = e.bus.paletteOpen || nav.stack.isNotEmpty() || nav.tab != "hud") {
        if (e.bus.paletteOpen) e.bus.paletteOpen = false else nav.back()
    }

    val atRoot = nav.stack.isEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = { (fadeIn() + slideInVertically { it / 24 }) togetherWith fadeOut() },
                    label = "route",
                ) { route -> RouteHost(route) }
            }
            AnimatedVisibility(atRoot, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                BottomBar()
            }
        }
        SnackbarHost(
            snackHost,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (atRoot) 84.dp else 12.dp, start = 12.dp, end = 12.dp),
        ) { data ->
            Snackbar(
                data, shape = RoundedCornerShape(14.dp),
                containerColor = Sar.c.surfaceHi, contentColor = Sar.c.text, actionColor = Sar.c.accent,
            )
        }
        AnimatedVisibility(e.bus.paletteOpen, enter = fadeIn(), exit = fadeOut()) { CommandPalette() }
    }
    e.bus.sharedText?.let { text -> SharedSheet(text) { e.bus.sharedText = null } }
}

@Composable
fun RouteHost(route: String) {
    val parts = route.split("/", limit = 2)
    when (parts[0]) {
        "hud" -> HudScreen()
        "sarathi" -> SarathiScreen()
        "plan" -> PlanScreen()
        "habits" -> HabitsScreen(standalone = true)
        "money" -> MoneyScreen()
        "notes" -> NotesScreen()
        "note" -> NoteEditorScreen(parts.getOrNull(1)?.toLongOrNull())
        "journal" -> JournalScreen()
        "gita" -> GitaScreen()
        "shloka" -> ShlokaScreen(parts.getOrNull(1) ?: "2.47")
        "focus" -> FocusScreen()
        "review" -> ReviewScreen()
        "weather" -> WeatherScreen()
        "chats" -> ChatsScreen()
        "settings" -> SettingsScreen(parts.getOrNull(1))
        "more" -> MoreScreen()
        else -> HudScreen()
    }
}

@Composable
private fun BottomBar() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val chat by e.brain.state.collectAsState()
    val voice by e.voice.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val slots = db.settings.tabs.take(2).let { if (it.size < 2) listOf("plan", "money") else it }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.surface.copy(alpha = 0.96f), c.bg)))
                .border(width = 0.dp, color = Color.Transparent)
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Routes.hud, Modifier.weight(1f))
            TabItem(Routes.info(slots[0]) ?: Routes.modules[0], Modifier.weight(1f))
            Spacer(Modifier.weight(1.1f))
            TabItem(Routes.info(slots[1]) ?: Routes.modules[2], Modifier.weight(1f))
            TabItem(Routes.more, Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        val selected = e.nav.tab == "sarathi"
        val mode = when { voice.listening -> OrbMode.LISTENING; chat.busy -> OrbMode.THINKING; voice.speaking -> OrbMode.SPEAKING; else -> OrbMode.IDLE }
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(c.surfaceHi, c.bg)))
                    .border(1.5.dp, if (selected) c.accent else c.line, CircleShape)
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        e.nav.go("sarathi")
                    }
                    .semantics { contentDescription = "Sarathi assistant" },
                contentAlignment = Alignment.Center,
            ) { Orb(size = 52.dp, mode = mode, level = voice.level, animate = db.settings.animations) }
            Text("SARATHI", style = Sar.t.label.copy(fontSize = sp(10f)), color = if (selected) c.accent else c.textDim)
        }
    }
}

@Composable
private fun TabItem(info: RouteInfo, modifier: Modifier) {
    val e = env
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    val selected = e.nav.tab == info.route
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); e.nav.go(info.route) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.accentSoft else Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp),
        ) { Icon(info.icon, info.label, tint = if (selected) c.accent else c.textDim, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(3.dp))
        Text(info.label.uppercase(), style = Sar.t.label.copy(fontSize = sp(10.5f)), color = if (selected) c.text else c.textDim, maxLines = 1)
    }
}

/** Universal search + command bar: jump to any screen or run a command. */
@Composable
fun CommandPalette() {
    val e = env
    val c = Sar.c
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val now = e.repo.clock.now()
    val preview = remember(q) {
        val cmd = Commands.parse(q, now, e.repo.db.habits.map { it.name })
        when {
            q.isBlank() -> null
            cmd is com.k.sarathi.domain.Command.Calculate -> "= ${Calc.format(cmd.result)}"
            cmd != null -> "Runs instantly: ${cmd.javaClass.simpleName.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()}"
            else -> "Ask Sarathi"
        }
    }
    val screens = remember(q) {
        val n = q.trim().lowercase()
        if (n.isEmpty()) Screen.entries.filter { it != Screen.HUD }
        else Screen.entries.filter { s -> s.title.lowercase().contains(n) || s.words.any { it.startsWith(n) } }
    }
    fun submit() {
        val text = q.trim()
        if (text.isEmpty()) return
        e.bus.paletteOpen = false
        val cmd = Commands.parse(text, now, e.repo.db.habits.map { it.name })
        if (cmd is com.k.sarathi.domain.Command.Open) { e.nav.go(cmd.screen.route); return }
        if (cmd == null) e.nav.go("sarathi")
        e.brain.send(text)
        if (cmd != null) e.bus.snack("Done — see Sarathi for details", "Open") { e.nav.go("sarathi") }
    }
    Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.92f)).clickable(enabled = true, onClick = { e.bus.paletteOpen = false })) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().imePadding().padding(16.dp)
                .clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(14.dp),
        ) {
            SarField(
                q, { q = it }, Modifier.focusRequester(focus),
                placeholder = "Search, or type a command…",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailing = { IconButton(onClick = { e.bus.paletteOpen = false }) { Icon(Icons.Rounded.Close, "Close", tint = c.textDim) } },
            )
            preview?.let { p ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft).clickable { submit() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(p, style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Run", tint = c.accent)
                }
            }
            if (q.isBlank()) {
                Text("TRY", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                listOf("spent 200 on lunch", "remind me to call amma at 6pm", "timer 10 min", "15% of 2000", "focus 50 on report").forEach { ex ->
                    Text("“$ex”", style = Sar.t.small, color = c.textDim, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { q = ex }.padding(vertical = 7.dp, horizontal = 4.dp))
                }
            }
            Text("SCREENS", style = Sar.t.label, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            LazyColumn(Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(screens) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { e.bus.paletteOpen = false; e.nav.go(s.route) }.padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Routes.info(s.route)?.icon ?: Icons.Rounded.Search, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, style = Sar.t.body, color = c.text)
                    }
                }
            }
        }
    }
}
