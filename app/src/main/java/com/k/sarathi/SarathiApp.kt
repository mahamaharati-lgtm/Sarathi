package com.k.sarathi

import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.k.sarathi.ai.Brain
import com.k.sarathi.ai.ClaudeClient
import com.k.sarathi.ai.ToolExecutor
import com.k.sarathi.core.Store
import com.k.sarathi.data.Content
import com.k.sarathi.data.Repo
import com.k.sarathi.device.AndroidPlatform
import com.k.sarathi.device.AndroidVoice
import com.k.sarathi.device.DeviceImpl
import com.k.sarathi.device.FocusController
import com.k.sarathi.device.KeystoreVault
import com.k.sarathi.device.Notifs
import com.k.sarathi.device.Scheduler
import com.k.sarathi.device.Widgets
import com.k.sarathi.ui.AppEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class SarathiApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        Notifs.channels(this)
        graph.scheduler.sync(graph.repo.db)
    }
}

/** Object graph for the whole app; created once per process. */
class Graph(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    val scheduler = Scheduler(app)
    private var widgetJob: Job? = null

    val store: Store = Store(File(app.filesDir, "sarathi.json"), scope) { db ->
        scheduler.sync(db)
        widgetJob?.cancel()
        widgetJob = scope.launch { delay(1500); Widgets.refresh(app) }
    }
    val repo = Repo(store)
    val content: Content = Content.load(app)
    val keys = KeystoreVault(app)
    val focus = FocusController(app, repo) { scheduler.sync(repo.db) }

    /** Set while MainActivity is in the foreground so tools can navigate in-app. */
    @Volatile var foreground = false

    private lateinit var envRef: AppEnv
    val device = DeviceImpl(app, repo, focus) { route ->
        if (foreground && ::envRef.isInitialized) { main.post { envRef.nav.go(route) }; true } else false
    }
    val voice = AndroidVoice(app) { repo.db.settings }
    val brain = Brain(
        repo, ToolExecutor(repo, content, device), ClaudeClient(), { keys.get() }, scope,
        onSpoken = { sp -> if (sp.fromVoice || repo.db.settings.speakReplies) voice.speak(sp.text) },
    )
    val env: AppEnv = AppEnv(repo, content, brain, device, voice, keys, AndroidPlatform(app)).also { envRef = it }

    private val sounds = SoundPool.Builder().setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val tapSound = sounds.load(app, R.raw.blip_tap, 1)
    private val confirmSound = sounds.load(app, R.raw.blip_confirm, 1)

    fun playTap() { if (repo.db.settings.sounds) sounds.play(tapSound, 0.25f, 0.25f, 0, 0, 1f) }
    fun playConfirm() { if (repo.db.settings.sounds) sounds.play(confirmSound, 0.4f, 0.4f, 0, 0, 1f) }
}
