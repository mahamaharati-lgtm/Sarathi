package com.k.sarathi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.k.sarathi.ui.SarathiRoot

class MainActivity : ComponentActivity() {
    private val graph get() = (application as SarathiApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { SarathiRoot(graph.env, onFeedback = { confirm -> if (confirm) graph.playConfirm() else graph.playTap() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() { super.onResume(); graph.foreground = true; graph.focus.refreshNotification() }
    override fun onPause() { graph.foreground = false; graph.store.flush(); super.onPause() }

    private fun handle(i: Intent?) {
        i ?: return
        val env = graph.env
        i.getStringExtra(EXTRA_ROUTE)?.let { env.nav.go(it) }
        if (i.getBooleanExtra(EXTRA_LISTEN, false) || i.action == Intent.ACTION_ASSIST || i.action == Intent.ACTION_VOICE_COMMAND) {
            env.bus.listenOnOpen = true
            env.nav.go("sarathi")
        }
        if (i.getBooleanExtra(EXTRA_ADD_EXPENSE, false)) {
            env.bus.addExpenseOnOpen = true
            env.nav.go("money")
        }
        i.getStringExtra(EXTRA_SHARED)?.let { env.bus.sharedText = it }
        i.removeExtra(EXTRA_ROUTE); i.removeExtra(EXTRA_LISTEN); i.removeExtra(EXTRA_ADD_EXPENSE); i.removeExtra(EXTRA_SHARED)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_LISTEN = "listen"
        const val EXTRA_ADD_EXPENSE = "add_expense"
        const val EXTRA_SHARED = "shared_text"
    }
}
