package com.k.sarathi.device

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.k.sarathi.MainActivity
import com.k.sarathi.SarathiApp
import com.k.sarathi.domain.Tasks
import com.k.sarathi.domain.iso
import com.k.sarathi.domain.toZoned
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BG = Color(0xFF0D0906)
private val ACCENT = Color(0xFFFF9933)
private val TEXT = Color(0xFFFBEFE3)
private val DIM = Color(0xFFC9AE95)

private fun openIntent(ctx: Context, route: String) =
    Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

class ShlokaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val g = (context.applicationContext as SarathiApp).graph
        val s = g.content.shlokaOfDay(g.repo.clock.today())
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp).padding(14.dp)
                    .clickable(actionStartActivity(openIntent(context, "shloka/${s.ref}"))),
            ) {
                Text("GITA ${s.ref}", style = TextStyle(color = ColorProvider(ACCENT), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(6.dp))
                Text(s.sa.lines().first(), style = TextStyle(color = ColorProvider(TEXT), fontSize = 15.sp), maxLines = 2)
                Spacer(GlanceModifier.height(6.dp))
                Text(s.en, style = TextStyle(color = ColorProvider(DIM), fontSize = 13.sp), maxLines = 4)
            }
        }
    }
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val g = (context.applicationContext as SarathiApp).graph
        val db = g.repo.db
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val due = Tasks.sort(Tasks.overdue(db, now) + Tasks.dueOn(db, today, now.zone)).distinctBy { it.id }
        val habits = db.habits.filter { !it.archived }
        val timeFmt = DateTimeFormatter.ofPattern(if (db.settings.use24h) "HH:mm" else "h:mm a", Locale.ENGLISH)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp).padding(14.dp)
                    .clickable(actionStartActivity(openIntent(context, "hud"))),
            ) {
                Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)).uppercase(), style = TextStyle(color = ColorProvider(ACCENT), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(
                    "${due.size} due · ${habits.count { today.iso() in it.done }}/${habits.size} habits",
                    style = TextStyle(color = ColorProvider(TEXT), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(6.dp))
                if (due.isEmpty()) Text("Clear road ahead.", style = TextStyle(color = ColorProvider(DIM), fontSize = 13.sp))
                due.take(4).forEach { t ->
                    val time = t.due?.let { if (t.allDay) "" else it.toZoned(now.zone).format(timeFmt) + "  " } ?: ""
                    Text("$time${t.title}", style = TextStyle(color = ColorProvider(TEXT), fontSize = 13.sp), maxLines = 1)
                }
            }
        }
    }
}

class ShlokaWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ShlokaWidget() }
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

object Widgets {
    suspend fun refresh(ctx: Context) {
        val m = GlanceAppWidgetManager(ctx)
        runCatching { if (m.getGlanceIds(ShlokaWidget::class.java).isNotEmpty()) ShlokaWidget().updateAll(ctx) }
        runCatching { if (m.getGlanceIds(TodayWidget::class.java).isNotEmpty()) TodayWidget().updateAll(ctx) }
    }
}

private fun TileService.openApp(i: Intent) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) {
        startActivityAndCollapse(PendingIntent.getActivity(this, i.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(i)
    }
}

class VoiceTile : TileService() {
    override fun onClick() = openApp(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_LISTEN, true))
}

class ExpenseTile : TileService() {
    override fun onClick() = openApp(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADD_EXPENSE, true))
}

/** "Ask Sarathi" from the text-selection menu and the share sheet. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = (intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHARED, text.take(4000)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}
