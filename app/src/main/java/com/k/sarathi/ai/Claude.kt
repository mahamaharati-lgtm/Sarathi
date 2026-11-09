package com.k.sarathi.ai

import com.k.sarathi.core.AppJson
import com.k.sarathi.domain.Models
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** One request to the Messages API. [messages] must be the full, append-only history. */
data class ClaudeRequest(
    val model: String,
    val system: String,
    val tools: JsonElement,
    val messages: List<JsonObject>,
    val effort: String,
    val maxTokens: Int = 64_000,
)

data class ToolUse(val id: String, val name: String, val input: JsonObject?, val raw: String)

data class Usage(val input: Long = 0, val output: Long = 0, val cacheRead: Long = 0, val cacheWrite: Long = 0)

/** A finished assistant turn. [content] is what must be replayed in later requests. */
data class AssistantTurn(
    val content: JsonArray,
    val text: String,
    val toolUses: List<ToolUse>,
    val stopReason: String?,
    val usage: Usage,
    val model: String,
)

class ClaudeException(val status: Int, val kind: String, message: String, val retryAfterSec: Long? = null) : IOException(message) {
    val retryable get() = status == 429 || status == 529 || status >= 500 || kind == "overloaded_error" || kind == "network"

    /** A message fit to show a person. */
    fun friendly(): String = when {
        status == 401 || kind == "authentication_error" -> "Claude rejected the API key. Check it in Settings → AI."
        status == 403 || kind == "permission_error" -> "This API key isn't allowed to use that model."
        status == 429 || kind == "rate_limit_error" -> "Claude is rate-limiting this key. Try again in a moment."
        status == 529 || kind == "overloaded_error" -> "Claude is busy right now. Try again shortly."
        kind == "network" -> "No connection to Claude. Offline commands still work."
        status == 400 -> "Claude couldn't process that request: $message"
        else -> "Claude error ($status): $message"
    }
}

object ClaudeWire {
    const val URL = "https://api.anthropic.com/v1/messages"
    const val VERSION = "2026-10-21"
    const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

    fun body(r: ClaudeRequest): JsonObject = buildJsonObject {
        put("model", r.model)
        put("max_tokens", r.maxTokens)
        put("stream", true)
        put("system", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", r.system) }) })
        put("tools", r.tools)
        put("messages", JsonArray(r.messages))
        put("output_config", buildJsonObject { put("effort", r.effort) })
        // Auto-caches the longest stable prefix (tools + system + history).
        put("cache_control", buildJsonObject { put("type", "ephemeral") })
        // Opt into server-side refusal fallbacks on models that support them.
        if (Models.get(r.model).fallbacks) put("fallbacks", "default")
    }

    fun headers(apiKey: String, model: String): Map<String, String> = buildMap {
        put("content-type", "application/json")
        put("accept", "text/event-stream")
        put("x-api-key", apiKey)
        put("anthropic-version", VERSION)
        if (Models.get(model).fallbacks) put("anthropic-beta", FALLBACK_BETA)
    }

    fun userText(text: String) = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
    }

    fun assistantText(text: String) = buildJsonObject {
        put("role", "assistant")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
    }

    fun toolResults(results: List<Triple<String, String, Boolean>>) = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray {
            for ((id, content, isError) in results) add(buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", id)
                put("content", content)
                if (isError) put("is_error", true)
            })
        })
    }

    fun parseError(status: Int, body: String, retryAfter: Long?): ClaudeException {
        val o = runCatching { AppJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val kind = o?.get("type")?.jsonPrimitive?.contentOrNull ?: "http_$status"
        val msg = o?.get("message")?.jsonPrimitive?.contentOrNull ?: body.take(200)
        return ClaudeException(status, kind, msg, retryAfter)
    }
}

/** Incremental Server-Sent Events parser: feed lines, get (event, data) pairs. */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()

    fun feed(line: String): Pair<String, String>? {
        if (line.isEmpty()) {
            if (data.isEmpty()) { event = null; return null }
            val out = (event ?: "message") to data.toString()
            event = null; data.setLength(0)
            return out
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> { if (data.isNotEmpty()) data.append('\n'); data.append(value) }
        }
        return null
    }
}

/**
 * Rebuilds the assistant message from stream events. Each block keeps exactly
 * the fields the API needs on replay; thinking blocks keep their signature.
 */
class StreamAccumulator(private val onText: (String) -> Unit = {}, private val onTool: (String) -> Unit = {}) {
    private class Block(val start: JsonObject) {
        val type = start["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val text = StringBuilder(start["text"]?.jsonPrimitive?.contentOrNull ?: "")
        val thinking = StringBuilder(start["thinking"]?.jsonPrimitive?.contentOrNull ?: "")
        var signature: String? = start["signature"]?.jsonPrimitive?.contentOrNull
        val json = StringBuilder()
    }

    private val blocks = sortedMapOf<Int, Block>()
    var model: String = ""; private set
    var stopReason: String? = null; private set
    private var usage = Usage()
    var error: ClaudeException? = null; private set
    var done = false; private set
    var sawOutput = false; private set

    fun onEvent(type: String, data: String) {
        if (type == "ping") return
        val o = runCatching { AppJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        when (o["type"]?.jsonPrimitive?.contentOrNull ?: type) {
            "message_start" -> {
                val m = o["message"]?.jsonObject
                model = m?.get("model")?.jsonPrimitive?.contentOrNull ?: model
                m?.get("usage")?.jsonObject?.let { usage = merge(usage, it) }
            }
            "content_block_start" -> {
                val idx = o["index"]?.jsonPrimitive?.longOrNull?.toInt() ?: blocks.size
                val b = Block(o["content_block"]?.jsonObject ?: JsonObject(emptyMap()))
                blocks[idx] = b
                if (b.type == "tool_use") b.start["name"]?.jsonPrimitive?.contentOrNull?.let(onTool)
                if (b.text.isNotEmpty()) { sawOutput = true; onText(b.text.toString()) }
            }
            "content_block_delta" -> {
                val b = blocks[o["index"]?.jsonPrimitive?.longOrNull?.toInt() ?: return] ?: return
                val d = o["delta"]?.jsonObject ?: return
                when (d["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> d["text"]?.jsonPrimitive?.contentOrNull?.let { b.text.append(it); sawOutput = true; onText(it) }
                    "thinking_delta" -> d["thinking"]?.jsonPrimitive?.contentOrNull?.let { b.thinking.append(it) }
                    "signature_delta" -> b.signature = d["signature"]?.jsonPrimitive?.contentOrNull
                    "input_json_delta" -> d["partial_json"]?.jsonPrimitive?.contentOrNull?.let { b.json.append(it); sawOutput = true }
                }
            }
            "message_delta" -> {
                o["delta"]?.jsonObject?.get("stop_reason")?.let { if (it !is JsonNull) stopReason = it.jsonPrimitive.contentOrNull }
                o["usage"]?.jsonObject?.let { usage = merge(usage, it) }
            }
            "message_stop" -> done = true
            "error" -> {
                val e = o["error"]?.jsonObject
                error = ClaudeException(
                    status = if (e?.get("type")?.jsonPrimitive?.contentOrNull == "overloaded_error") 529 else 500,
                    kind = e?.get("type")?.jsonPrimitive?.contentOrNull ?: "stream_error",
                    message = e?.get("message")?.jsonPrimitive?.contentOrNull ?: "stream error",
                )
            }
        }
    }

    private fun merge(u: Usage, o: JsonObject): Usage {
        fun f(k: String) = o[k]?.jsonPrimitive?.longOrNull
        return Usage(
            input = f("input_tokens") ?: u.input,
            output = f("output_tokens") ?: u.output,
            cacheRead = f("cache_read_input_tokens") ?: u.cacheRead,
            cacheWrite = f("cache_creation_input_tokens") ?: u.cacheWrite,
        )
    }

    fun result(): AssistantTurn {
        val ordered = blocks.values.toList()
        // After a mid-output fallback, blocks other than text that precede the
        // last fallback marker must not be echoed back; the marker is dropped.
        val lastFallback = ordered.indexOfLast { it.type == "fallback" }
        val content = mutableListOf<JsonObject>()
        val tools = mutableListOf<ToolUse>()
        val text = StringBuilder()
        ordered.forEachIndexed { i, b ->
            if (b.type == "fallback") return@forEachIndexed
            if (i < lastFallback && b.type != "text") return@forEachIndexed
            when (b.type) {
                "text" -> {
                    if (b.text.isEmpty()) return@forEachIndexed
                    text.append(b.text)
                    content += buildJsonObject { put("type", "text"); put("text", b.text.toString()) }
                }
                "thinking" -> content += buildJsonObject {
                    put("type", "thinking"); put("thinking", b.thinking.toString())
                    b.signature?.let { put("signature", it) }
                }
                "tool_use" -> {
                    val raw = b.json.toString().trim()
                    val parsed: JsonObject? = if (raw.isEmpty()) JsonObject(emptyMap())
                    else runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                    val id = b.start["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val name = b.start["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    tools += ToolUse(id, name, parsed, raw)
                    content += buildJsonObject {
                        put("type", "tool_use"); put("id", id); put("name", name)
                        put("input", parsed ?: JsonObject(emptyMap()))
                    }
                }
                else -> content += b.start // redacted_thinking and anything new: echo as received
            }
        }
        return AssistantTurn(JsonArray(content), text.toString(), tools, stopReason, usage, model)
    }
}

interface LlmClient {
    suspend fun stream(apiKey: String, req: ClaudeRequest, onText: (String) -> Unit = {}, onTool: (String) -> Unit = {}): AssistantTurn
}

/** Streams one request over HTTPS. Cancelling the coroutine closes the socket. */
class ClaudeClient(
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) : LlmClient {
    override suspend fun stream(
        apiKey: String,
        req: ClaudeRequest,
        onText: (String) -> Unit,
        onTool: (String) -> Unit,
    ): AssistantTurn = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val acc = StreamAccumulator(onText, onTool)
            try {
                once(apiKey, req, acc)
                acc.error?.let { throw it }
                return@withContext acc.result()
            } catch (e: ClaudeException) {
                if (!e.retryable || acc.sawOutput || attempt >= 2) throw e
                attempt++
                delay(((e.retryAfterSec ?: (1L shl attempt)).coerceIn(1, 20)) * 1000)
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun once(apiKey: String, req: ClaudeRequest, acc: StreamAccumulator) {
        val conn = open(ClaudeWire.URL)
        val closer = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { if (it != null) conn.disconnect() }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            ClaudeWire.headers(apiKey, req.model).forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val status = try {
                conn.outputStream.use { it.write(ClaudeWire.body(req).toString().toByteArray()) }
                conn.responseCode
            } catch (e: IOException) {
                throw ClaudeException(0, "network", e.message ?: "network error")
            }
            if (status != 200) {
                val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull() ?: ""
                throw ClaudeWire.parseError(status, body, conn.getHeaderField("retry-after")?.toLongOrNull())
            }
            val parser = SseParser()
            try {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = r.readLine() ?: break
                        parser.feed(line)?.let { (ev, data) -> acc.onEvent(ev, data) }
                        if (acc.done) break
                    }
                }
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                throw ClaudeException(0, "network", e.message ?: "stream interrupted")
            }
        } finally {
            closer?.dispose()
            conn.disconnect()
        }
    }
}
