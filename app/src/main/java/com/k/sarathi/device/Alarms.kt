package com.k.sarathi.device

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.k.sarathi.Graph
import com.k.sarathi.MainActivity
import com.k.sarathi.R
import com.k.sarathi.SarathiApp
import com.k.sarathi.data.Db
import com.k.sarathi.data.FocusPhase
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.Tasks
import com.k.sarathi.domain.WeatherCodes
import com.k.sarathi.domain.iso
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.roundToInt

object Notifs {
    const val REMINDERS = "reminders_v3"
    const val FOCUS = "focus_v3"
    const val BRIEFING = "briefing_v3"
    const val FOCUS_ID = 9_001
    const val BRIEFING_ID = 9_002

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun sound(res: Int) = Uri.parse("android.resource://${ctx.packageName}/$res")
        nm.createNotificationChannel(NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tasks and reminders at their due time"
            setSound(sound(R.raw.chime_reminder), attrs); enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "The running focus session and its end chime"
        })
        nm.createNotificationChannel(NotificationChannel(BRIEFING, "Daily briefing", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your morning summary"
            setSound(sound(R.raw.chime_dawn), attrs)
        })
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canPost(ctx)) return
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    fun openApp(ctx: Context, route: String, req: Int): PendingIntent = PendingIntent.getActivity(
        ctx, req, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun action(ctx: Context, act: String, id: Long, req: Int, extra: Int = 0): PendingIntent = PendingIntent.getBroadcast(
        ctx, req, Intent(ctx, AlarmReceiver::class.java).setAction(act).putExtra("id", id).putExtra("n", extra),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Keeps AlarmManager in step with the database: reminders, focus end and the daily briefing. */
class Scheduler(private val ctx: Context) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val prefs = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    @Volatile private var lastSignature = ""

    fun sync(db: Db, now: ZonedDateTime = ZonedDateTime.now()) {
        val nowMs = now.toInstant().toEpochMilli()
        val reminders = db.tasks.filter { it.remind && !it.done && (it.due ?: 0) > nowMs }.associate { it.id to it.due!! }
        val focusEnd = db.focus.endsAt.takeIf { db.focus.phase != FocusPhase.IDLE && it > nowMs }
        val briefingAt = if (db.settings.briefing) nextBriefing(db.settings.briefingTime, now) else null
        val sig = "$reminders|$focusEnd|$briefingAt"
        if (sig == lastSignature) return
        lastSignature = sig

        val previous = prefs.getStringSet("ids", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        (previous - reminders.keys).forEach { am.cancel(pending(ACTION_REMIND, it)) }
        reminders.forEach { (id, at) -> exact(at, pending(ACTION_REMIND, id)) }
        prefs.edit().putStringSet("ids", reminders.keys.map { it.toString() }.toSet()).apply()

        if (focusEnd != null) exact(focusEnd, pending(ACTION_FOCUS_END, 0)) else am.cancel(pending(ACTION_FOCUS_END, 0))
        if (briefingAt != null) exact(briefingAt, pending(ACTION_BRIEFING, 0)) else am.cancel(pending(ACTION_BRIEFING, 0))
    }

    private fun nextBriefing(hhmm: String, now: ZonedDateTime): Long {
        val t = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.of(7, 0))
        var at = now.with(t).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun pending(action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, (action.hashCode() * 31 + id.hashCode()),
        Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun exact(at: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.k.sarathi.REMIND"
        const val ACTION_DONE = "com.k.sarathi.DONE"
        const val ACTION_SNOOZE = "com.k.sarathi.SNOOZE"
        const val ACTION_FOCUS_END = "com.k.sarathi.FOCUS_END"
        const val ACTION_FOCUS_STOP = "com.k.sarathi.FOCUS_STOP"
        const val ACTION_BREAK = "com.k.sarathi.BREAK"
        const val ACTION_BRIEFING = "com.k.sarathi.BRIEFING"
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val g = (ctx.applicationContext as SarathiApp).graph
        val repo = g.repo
        val id = intent.getLongExtra("id", 0)
        val nid = id.toInt()
        when (intent.action) {
            Scheduler.ACTION_REMIND -> {
                val t = repo.db.tasks.firstOrNull { it.id == id && !it.done } ?: return
                val n = NotificationCompat.Builder(ctx, Notifs.REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_sarathi)
                    .setContentTitle(t.title)
                    .setContentText(t.notes.ifBlank { "Reminder from Sarathi" })
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setColor(0xFFFF9933.toInt())
                    .setAutoCancel(true)
                    .setContentIntent(Notifs.openApp(ctx, "plan", nid))
                    .addAction(0, "Done", Notifs.action(ctx, Scheduler.ACTION_DONE, id, nid * 4 + 1))
                    .addAction(0, "Snooze 10 min", Notifs.action(ctx, Scheduler.ACTION_SNOOZE, id, nid * 4 + 2, 10))
                    .addAction(0, "1 hour", Notifs.action(ctx, Scheduler.ACTION_SNOOZE, id, nid * 4 + 3, 60))
                    .build()
                Notifs.post(ctx, nid, n)
            }
            Scheduler.ACTION_DONE -> { repo.complete(id); Notifs.cancel(ctx, nid) }
            Scheduler.ACTION_SNOOZE -> { repo.snoozeTask(id, intent.getIntExtra("n", 10).toLong()); Notifs.cancel(ctx, nid) }
            Scheduler.ACTION_FOCUS_END -> g.focus.onTimeUp()
            Scheduler.ACTION_FOCUS_STOP -> g.focus.stop(completed = false)
            Scheduler.ACTION_BREAK -> g.focus.startBreak(repo.db.settings.breakMin)
            Scheduler.ACTION_BRIEFING -> briefing(ctx, g)
        }
        g.store.flush()
        g.scheduler.sync(repo.db)
    }

    private fun briefing(ctx: Context, g: Graph) {
        val db = g.repo.db
        val now = g.repo.clock.now()
        val today = now.toLocalDate()
        val due = Tasks.dueOn(db, today, now.zone).size
        val overdue = Tasks.overdue(db, now).size
        val habits = db.habits.filter { !it.archived }
        val m = Money.month(db, now)
        val sh = g.content.shlokaOfDay(today)
        val lines = buildList {
            add("📋 $due due today" + if (overdue > 0) " · $overdue overdue" else "")
            if (habits.isNotEmpty()) add("🔥 ${habits.size} habits to keep · ${habits.count { today.iso() in it.done }} done")
            db.weather?.let { add("${WeatherCodes.emoji(it.code)} ${it.tempC.roundToInt()}° now, up to ${it.maxC.roundToInt()}° · rain ${it.rainPct}%") }
            if (m.budget > 0) add("₹ ${Money.fmt(m.left, db.settings.currency)} left this month")
            add("🕉 ${sh.ref}: ${sh.en}")
        }
        val n = NotificationCompat.Builder(ctx, Notifs.BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_sarathi)
            .setContentTitle("Good morning, ${db.settings.userName}")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setColor(0xFFFF9933.toInt())
            .setAutoCancel(true)
            .setContentIntent(Notifs.openApp(ctx, "hud", Notifs.BRIEFING_ID))
            .build()
        Notifs.post(ctx, Notifs.BRIEFING_ID, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val g = (ctx.applicationContext as SarathiApp).graph
        g.scheduler.sync(g.repo.db)
        g.focus.refreshNotification()
    }
}
