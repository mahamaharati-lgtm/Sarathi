package com.k.sarathi.core

import com.k.sarathi.data.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Single source of truth. Reads are a [StateFlow]; writes go through [update]
 * or [mutate], are applied atomically in memory, and are persisted (debounced)
 * as one JSON file using write-to-temp + rename so a crash never leaves a
 * half-written database. The previous good file is kept as `.bak`.
 */
class Store(
    private val file: File?,
    private val scope: CoroutineScope,
    private val onSaved: (Db) -> Unit = {},
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Db> = _state.asStateFlow()
    val db: Db get() = _state.value

    private var pending: Job? = null
    private val lock = Any()

    fun update(fn: (Db) -> Db) {
        _state.update(fn)
        scheduleSave()
    }

    /** Applies [fn] atomically and returns its side value (e.g. a new id). */
    fun <T> mutate(fn: (Db) -> Pair<Db, T>): T {
        var out: T? = null
        _state.update { cur -> fn(cur).also { out = it.second }.first }
        scheduleSave()
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    /** Replaces everything (restore from backup). */
    fun replace(db: Db) {
        _state.value = db
        scheduleSave(immediate = true)
    }

    fun flush() {
        pending?.cancel()
        write(_state.value)
    }

    private fun scheduleSave(immediate: Boolean = false) {
        if (file == null) return
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            if (!immediate) delay(250)
            write(_state.value)
        }
    }

    private fun write(db: Db) {
        val f = file ?: return
        synchronized(lock) {
            runCatching {
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(AppJson.encodeToString(Db.serializer(), db).toByteArray())
                    out.fd.sync()
                }
                if (f.exists()) f.copyTo(File(f.parentFile, f.name + ".bak"), overwrite = true)
                if (!tmp.renameTo(f)) {
                    tmp.copyTo(f, overwrite = true); tmp.delete()
                }
            }.onFailure { L.e("store write failed", it) }
        }
        onSaved(db)
    }

    private fun load(): Db {
        val f = file ?: return Db()
        for (candidate in listOf(f, File(f.parentFile, f.name + ".bak"))) {
            if (!candidate.exists()) continue
            val parsed = runCatching { AppJson.decodeFromString(Db.serializer(), candidate.readText()) }
            parsed.onFailure { L.e("store read failed: ${candidate.name}", it) }
            parsed.getOrNull()?.let { return it }
        }
        return Db()
    }
}

object L {
    var sink: (String, Throwable?) -> Unit = { msg, t ->
        runCatching { android.util.Log.w("Sarathi", msg, t) }
    }
    fun e(msg: String, t: Throwable? = null) = sink(msg, t)
}
