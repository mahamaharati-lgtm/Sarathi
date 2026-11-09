package com.k.sarathi.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.k.sarathi.MainActivity
import com.k.sarathi.R
import com.k.sarathi.ai.CalEvent
import com.k.sarathi.ai.Device
import com.k.sarathi.core.AppJson
import com.k.sarathi.data.FocusPhase
import com.k.sarathi.data.Repo
import com.k.sarathi.data.WeatherCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** Focus sessions: state in the repo, an exact alarm for the end, a live countdown notification. */
class FocusController(private val ctx: Context, private val repo: Repo, private val sync: () -> Unit) {
    fun start(minutes: Int, label: String) {
        repo.startFocus(minutes, label)
        sync(); refreshNotification()
    }

    fun startBreak(minutes: Int) {
        repo.startFocus(minutes, "Break", FocusPhase.BREAK)
        sync(); refreshNotification()
    }

    fun stop(completed: Boolean) {
        repo.endFocus(completed)
        Notifs.cancel(ctx, Notifs.FOCUS_ID)
        sync()
    }

    /** Called by the end alarm: log the session and chime. */
    fun onTimeUp() {
        val f = repo.db.focus
        if (f.phase == FocusPhase.IDLE) return
        val wasBreak = f.phase == FocusPhase.BREAK
        repo.endFocus(completed = true)
        val b = NotificationCompat.Builder(ctx, Notifs.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_sarathi)
            .setContentTitle(if (wasBreak) "Break's over" else "Focus complete ✓")
            .setContentText(if (wasBreak) "Ready for the next session?" else "${f.minutes} minutes of ${f.label.ifBlank { "deep work" }}. Take a breather.")
            .setColor(0xFFFF9933.toInt())
            .setAutoCancel(true)
            .setContentIntent(Notifs.openApp(ctx, "focus", Notifs.FOCUS_ID))
        if (!wasBreak) b.addAction(0, "Start ${repo.db.settings.breakMin} min break", Notifs.action(ctx, Scheduler.ACTION_BREAK, 0, 77))
        Notifs.cancel(ctx, Notifs.FOCUS_ID)
        Notifs.post(ctx, Notifs.FOCUS_ID + 1, b.build())
    }

    fun refreshNotification() {
        val f = repo.db.focus
        if (f.phase == FocusPhase.IDLE || f.endsAt <= System.currentTimeMillis()) { Notifs.cancel(ctx, Notifs.FOCUS_ID); return }
        val n = NotificationCompat.Builder(ctx, Notifs.FOCUS)
            .setSmallIcon(R.drawable.ic_stat_sarathi)
            .setContentTitle(if (f.phase == FocusPhase.BREAK) "Break" else "Focus · ${f.label.ifBlank { "deep work" }}")
            .setContentText("${f.minutes} minute session")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(f.endsAt)
            .setShowWhen(true)
            .setColor(0xFFFF9933.toInt())
            .setContentIntent(Notifs.openApp(ctx, "focus", Notifs.FOCUS_ID))
            .addAction(0, "End", Notifs.action(ctx, Scheduler.ACTION_FOCUS_STOP, 0, 78))
            .build()
        Notifs.post(ctx, Notifs.FOCUS_ID, n)
    }
}

class DeviceImpl(
    private val ctx: Context,
    private val repo: Repo,
    private val focus: FocusController,
    private val navigate: (String) -> Boolean,
) : Device {

    private fun launch(i: Intent): Boolean = runCatching {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    override fun setAlarm(hour: Int, minute: Int, label: String, days: List<DayOfWeek>): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        if (days.isNotEmpty()) i.putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days.map { (it.value % 7) + 1 }))
        return if (launch(i)) "Alarm set for %02d:%02d in the Clock app.".format(hour, minute) else error("No clock app can set alarms")
    }

    override fun setTimer(seconds: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (launch(i)) "Timer started for $seconds seconds." else error("No clock app can start timers")
    }

    override fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: error("This phone has no torch")
        cm.setTorchMode(id, on)
        return if (on) "Torch on." else "Torch off."
    }

    private fun get(url: String): JsonObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return AppJson.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
        } finally { c.disconnect() }
    }

    /** Open-Meteo (no API key). Geocodes the city when it changes, caches for 30 minutes. */
    override suspend fun weather(): WeatherCache? = withContext(Dispatchers.IO) {
        val s = repo.db.settings
        val cached = repo.db.weather
        if (cached != null && cached.city == s.city && System.currentTimeMillis() - cached.at < 30 * 60_000) return@withContext cached
        runCatching {
            var lat = s.lat; var lon = s.lon
            if (cached?.city != s.city && s.city.isNotBlank() && !(s.city.equals("Bengaluru", true) && lat == 12.9716)) {
                val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(s.city, "UTF-8"))
                geo["results"]?.jsonArray?.firstOrNull()?.jsonObject?.let { r ->
                    lat = r["latitude"]!!.jsonPrimitive.double; lon = r["longitude"]!!.jsonPrimitive.double
                    repo.settings { it.copy(lat = lat, lon = lon) }
                }
            }
            val w = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=2" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code")
            val cur = w["current"]!!.jsonObject
            val d = w["daily"]!!.jsonObject
            fun arr(k: String) = d[k]!!.jsonArray
            WeatherCache(
                at = System.currentTimeMillis(), city = s.city,
                tempC = cur["temperature_2m"]!!.jsonPrimitive.double,
                feelsC = cur["apparent_temperature"]!!.jsonPrimitive.double,
                code = cur["weather_code"]!!.jsonPrimitive.int,
                humidity = cur["relative_humidity_2m"]!!.jsonPrimitive.int,
                windKmh = cur["wind_speed_10m"]!!.jsonPrimitive.double,
                maxC = arr("temperature_2m_max")[0].jsonPrimitive.double,
                minC = arr("temperature_2m_min")[0].jsonPrimitive.double,
                rainPct = arr("precipitation_probability_max")[0].jsonPrimitive.intOrNull ?: 0,
                tomorrowMaxC = arr("temperature_2m_max").getOrNull(1)?.jsonPrimitive?.doubleOrNull,
                tomorrowCode = arr("weather_code").getOrNull(1)?.jsonPrimitive?.intOrNull,
            ).also(repo::setWeather)
        }.getOrElse { cached }
    }

    override fun events(date: LocalDate): List<CalEvent>? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val zone = repo.clock.zone()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION)
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) add(CalEvent(c.getString(0) ?: "(no title)", c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getString(4) ?: ""))
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun createEvent(title: String, start: ZonedDateTime, end: ZonedDateTime, location: String): String {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
        if (location.isNotBlank()) i.putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        return if (launch(i)) "Opened the calendar with “$title” filled in; the user confirms it there." else error("No calendar app found")
    }

    override fun open(route: String) {
        if (!navigate(route)) launch(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route))
    }

    override fun startFocus(minutes: Int, label: String): String {
        focus.start(minutes, label)
        return "Focus session started for $minutes minutes" + (if (label.isNotBlank()) " on $label" else "") + "."
    }

    override fun startBreak(minutes: Int): String { focus.startBreak(minutes); return "Break started for $minutes minutes." }

    override fun stopFocus(completed: Boolean) = focus.stop(completed)
}
