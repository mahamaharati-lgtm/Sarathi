package com.k.sarathi.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.k.sarathi.ai.Brain
import com.k.sarathi.ai.ClaudeWire
import com.k.sarathi.ai.FakeDevice
import com.k.sarathi.ai.ScriptedLlm
import com.k.sarathi.ai.ToolExecutor
import com.k.sarathi.core.Store
import com.k.sarathi.data.Category
import com.k.sarathi.data.ChatMsg
import com.k.sarathi.data.Clock
import com.k.sarathi.data.Content
import com.k.sarathi.data.Conversation
import com.k.sarathi.data.Db
import com.k.sarathi.data.FocusPhase
import com.k.sarathi.data.FocusSession
import com.k.sarathi.data.FocusState
import com.k.sarathi.data.Priority
import com.k.sarathi.data.Recur
import com.k.sarathi.data.Repo
import com.k.sarathi.data.ThemeChoice
import com.k.sarathi.data.ToolCard
import com.k.sarathi.data.WeatherCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime

private class NoVoice : Voice {
    override val state: StateFlow<VoiceState> = MutableStateFlow(VoiceState())
    override fun listen(onFinal: (String) -> Unit) {}
    override fun stopListening() {}
    override fun speak(text: String) {}
    override fun stopSpeaking() {}
    override fun setConversation(on: Boolean) {}
}

private class MemVault(var v: String? = "sk-ant-demo") : KeyVault {
    override fun get() = v
    override fun set(value: String?) { v = value }
}

private class NoPlatform : Platform {
    override fun share(text: String, subject: String) {}
    override fun shareFile(name: String, mime: String, content: String) {}
    override fun hasPermission(permission: String) = false
    override fun openAppSettings() {}
    override fun appVersion() = "3.0.0"
}

/** Renders real screens with realistic sample data, for design review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w392dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 9, 8, 40, 0, 0, zone)
    private fun at(days: Long, h: Int, m: Int = 0) = now.toLocalDate().plusDays(days).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun env(theme: ThemeChoice, mutate: (Db) -> Db = { it }): AppEnv {
        val repo = Repo(Store(null, CoroutineScope(Dispatchers.Unconfined)), Clock { now })
        val content = Content.load(RuntimeEnvironment.getApplication())
        val today = now.toLocalDate()
        repo.addTask("Pay electricity bill", at(-1, 10), priority = Priority.HIGH, remind = true)
        repo.addTask("Stand-up with the design team", at(0, 10, 30), remind = true)
        repo.addTask("Call amma", at(0, 18), remind = true)
        repo.addTask("Water the tulsi", at(0, 19), recur = Recur.DAILY, remind = true)
        repo.addTask("Book train tickets to Mysuru", at(1, 9))
        repo.addTask("Renew passport", at(4, 9), allDay = true)
        repo.addTask("Read Atomic Habits")
        repo.complete(repo.addTask("Morning walk", at(0, 6, 30)).id)
        val med = repo.addHabit("Meditate", "🧘")
        val walk = repo.addHabit("Walk 6k steps", "🚶")
        val read = repo.addHabit("Read 20 pages", "📖")
        for (i in 0..11) repo.markHabitDone(med.id, today.minusDays(i.toLong()))
        for (i in 1..5) repo.markHabitDone(walk.id, today.minusDays(i.toLong()))
        for (i in listOf(2, 3, 5, 8, 9)) repo.markHabitDone(read.id, today.minusDays(i.toLong()))
        listOf(
            Triple(4200.0, Category.GROCERIES, "BigBasket monthly"), Triple(1850.0, Category.BILLS, "Electricity"),
            Triple(640.0, Category.FOOD, "Dinner at Vidyarthi Bhavan"), Triple(350.0, Category.TRANSPORT, "Rapido to office"),
            Triple(1299.0, Category.SHOPPING, "Kurta"), Triple(2500.0, Category.INVEST, "SIP"), Triple(180.0, Category.FOOD, "Filter coffee"),
        ).forEachIndexed { i, (a, cat, note) -> repo.logExpense(a, cat, note, at(-(i % 8).toLong(), 13 - i)) }
        repo.setMonthlyBudget(25000.0)
        repo.setCategoryBudget(Category.FOOD, 4000.0)
        repo.setCategoryBudget(Category.GROCERIES, 6000.0)
        repo.addNote("Diwali shopping", "Marigolds, diyas, rangoli colours, sweets from Asha")
        repo.addNote("Ideas for Sarathi", "Voice shortcuts\nWeekly review card\nGita reading plan")
        repo.addJournal("Calmer today. Finished the report early and went for a long walk.", 4, "2.47", today.minusDays(1))
        repo.remember("K is vegetarian")
        repo.store.update { db ->
            mutate(db.copy(
                settings = db.settings.copy(onboarded = true, theme = theme, animations = false, userName = "K"),
                weather = WeatherCache(now.toInstant().toEpochMilli(), "Bengaluru", 23.6, 24.1, 2, 68, 9.0, 28.0, 19.0, 20, 27.0, 61),
                focusLog = listOf(FocusSession(900, "Quarterly report", at(0, 7), 50, true)),
                conversations = listOf(Conversation(
                    1, "Plan my evening", now.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), "s", ClaudeWire.userText("x"),
                    listOf(
                        ChatMsg("user", ClaudeWire.userText("x")["content"]!!, display = "Plan my evening — I want to call amma and get some reading in", ts = 1),
                        ChatMsg("assistant", ClaudeWire.userText("x")["content"]!!, display = "Here's a calm evening:\n• **6:00 PM** call amma\n• **7:00 PM** water the tulsi\n• **9:00 PM** 20 pages of reading\nI added a reading reminder for 9 PM.", cards = listOf(ToolCard("create_task", "Read 20 pages", "Today 9:00 PM · reminder on")), ts = 2, model = "claude-opus-5-5"),
                        ChatMsg("user", ClaudeWire.userText("x")["content"]!!, display = "spent 180 on filter coffee", ts = 3),
                        ChatMsg("assistant", ClaudeWire.userText("x")["content"]!!, display = "Logged ₹180 Food “Filter coffee”.", cards = listOf(ToolCard("log_expense", "🍛 ₹180 · Food", "₹11,981 left this month")), ts = 4, model = "on-device"),
                    ),
                )),
            ))
        }
        val brain = Brain(repo, ToolExecutor(repo, content, FakeDevice()), ScriptedLlm(mutableListOf()), { "sk-ant-demo" }, CoroutineScope(Dispatchers.Unconfined))
        brain.open(1)
        return AppEnv(repo, content, brain, FakeDevice(), NoVoice(), MemVault(), NoPlatform())
    }

    private fun shot(name: String, e: AppEnv, before: (AppEnv) -> Unit = {}) {
        before(e)
        compose.mainClock.autoAdvance = false
        compose.setContent { SarathiRoot(e) }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test fun hudArc() = shot("01-hud-arc", env(ThemeChoice.ARC))
    @Test fun hudDay() = shot("02-hud-day", env(ThemeChoice.DAY))
    @Test fun chat() = shot("03-sarathi-chat", env(ThemeChoice.NEBULA)) { it.nav.go("sarathi") }
    @Test fun plan() = shot("04-plan-tasks", env(ThemeChoice.ARC)) { it.nav.go("plan") }
    @Test fun habits() = shot("05-habits", env(ThemeChoice.SAFFRON)) { it.nav.go("habits") }
    @Test fun money() = shot("06-money", env(ThemeChoice.ARC)) { it.nav.go("money") }
    @Test fun gita() = shot("07-gita", env(ThemeChoice.SAFFRON)) { it.nav.go("gita") }
    @Test fun shloka() = shot("08-shloka", env(ThemeChoice.SAFFRON)) { it.nav.go("shloka/2.47") }
    @Test fun focus() = shot("09-focus", env(ThemeChoice.EMBER) { db -> db.copy(focus = FocusState(FocusPhase.FOCUS, "Quarterly report", at(0, 8, 20), at(0, 9, 10), 50)) }) { it.nav.go("focus") }
    @Test fun settingsAi() = shot("10-settings-ai", env(ThemeChoice.ARC)) { it.nav.go("settings/ai") }
    @Test fun appearance() = shot("11-appearance", env(ThemeChoice.NEBULA)) { it.nav.go("settings/appearance") }
    @Test fun palette() = shot("12-command-palette", env(ThemeChoice.ARC)) { it.bus.paletteOpen = true }
    @Test fun onboarding() = shot("13-onboarding", env(ThemeChoice.SAFFRON) { db -> db.copy(settings = db.settings.copy(onboarded = false)) })
    @Test fun more() = shot("14-more", env(ThemeChoice.ARC)) { it.nav.go("more") }
    @Test fun journal() = shot("15-journal", env(ThemeChoice.DAY)) { it.nav.go("journal") }

}
