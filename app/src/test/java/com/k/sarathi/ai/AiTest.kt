package com.k.sarathi.ai

import com.k.sarathi.core.AppJson
import com.k.sarathi.core.Store
import com.k.sarathi.data.Clock
import com.k.sarathi.data.Content
import com.k.sarathi.data.Repo
import com.k.sarathi.data.Shloka
import com.k.sarathi.data.WeatherCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private fun events(sse: String): AssistantTurn {
    val p = SseParser()
    val acc = StreamAccumulator()
    (sse.trimIndent() + "\n\n").lines().forEach { l -> p.feed(l)?.let { (e, d) -> acc.onEvent(e, d) } }
    return acc.result()
}

class StreamTest {
    @Test fun textThinkingAndToolUseAreRebuilt() {
        val t = events(
            """
            event: message_start
            data: {"type":"message_start","message":{"id":"m1","model":"claude-opus-5-5","usage":{"input_tokens":1200,"cache_read_input_tokens":3000,"cache_creation_input_tokens":0,"output_tokens":1}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig-abc"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: ping
            data: {"type":"ping"}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Adding "}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"it."}}

            event: content_block_start
            data: {"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"create_task","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"title\": \"Call"}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":" amma\"}"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}

            event: message_stop
            data: {"type":"message_stop"}
            """
        )
        assertEquals("tool_use", t.stopReason)
        assertEquals("Adding it.", t.text)
        assertEquals(3, t.content.size)
        val thinking = t.content[0].jsonObject
        assertEquals("thinking", thinking["type"]!!.jsonPrimitive.content)
        assertEquals("sig-abc", thinking["signature"]!!.jsonPrimitive.content)
        val tu = t.toolUses.single()
        assertEquals("Call amma", tu.input!!["title"]!!.jsonPrimitive.content)
        assertEquals("toolu_1", t.content[2].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(Usage(1200, 42, 3000, 0), t.usage)
        assertEquals("claude-opus-5-5", t.model)
    }

    @Test fun invalidToolJsonIsFlaggedNotThrown() {
        val t = events(
            """
            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t","name":"log_expense","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"amount\": 12"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"max_tokens"}}
            """
        )
        assertNull(t.toolUses.single().input)
        assertEquals("max_tokens", t.stopReason)
        // The replayed block still has an object input so the history stays valid.
        assertEquals(JsonObject(emptyMap()), t.content[0].jsonObject["input"])
    }

    @Test fun midOutputFallbackDropsDeclinedNonTextBlocks() {
        val t = events(
            """
            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":"s1"}}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":"Part one. "}}

            event: content_block_start
            data: {"type":"content_block_start","index":2,"content_block":{"type":"fallback","from":{"model":"claude-opus-5-5"},"to":{"model":"claude-opus-5"}}}

            event: content_block_start
            data: {"type":"content_block_start","index":3,"content_block":{"type":"text","text":"Part two."}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}
            """
        )
        val types = t.content.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("text", "text"), types)
        assertEquals("Part one. Part two.", t.text)
    }

    @Test fun streamErrorEventSurfaces() {
        val p = SseParser(); val acc = StreamAccumulator()
        listOf("event: error", """data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""", "")
            .forEach { l -> p.feed(l)?.let { (e, d) -> acc.onEvent(e, d) } }
        assertEquals("overloaded_error", acc.error!!.kind)
        assertTrue(acc.error!!.retryable)
    }
}

class WireTest {
    @Test fun requestBodyHasCachingEffortAndFallbacks() {
        val body = ClaudeWire.body(ClaudeRequest("claude-opus-5-5", "sys", ToolSpecs.all, listOf(ClaudeWire.userText("hi")), "low"))
        assertEquals("ephemeral", body["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertNull(body["thinking"]) // adaptive by default; never disabled
        assertNull(body["temperature"])
        val h = ClaudeWire.headers("k", "claude-opus-5-5")
        assertEquals("2026-10-21", h["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", h["anthropic-beta"])
        // Haiku 5.5 has no server-side fallback.
        val haiku = ClaudeWire.body(ClaudeRequest("claude-haiku-5-5", "s", JsonArray(emptyList()), emptyList(), "low"))
        assertNull(haiku["fallbacks"])
        assertNull(ClaudeWire.headers("k", "claude-haiku-5-5")["anthropic-beta"])
    }

    @Test fun toolSpecsAreWellFormed() {
        val names = ToolSpecs.all.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(names.size, names.toSet().size)
        assertTrue(ToolSpecs.all.all { it.jsonObject["eager_input_streaming"]!!.jsonPrimitive.content == "true" })
        assertTrue(names.size >= 25)
    }

    @Test fun validation() {
        assertNull(ToolSpecs.validate("log_expense", buildJsonObject { put("amount", 120); put("category", "food") }))
        assertNotNull(ToolSpecs.validate("log_expense", buildJsonObject { put("amount", "120") }))
        assertNotNull(ToolSpecs.validate("log_expense", buildJsonObject { put("category", "food") }))
        assertNotNull(ToolSpecs.validate("log_expense", buildJsonObject { put("amount", 5); put("category", "cars") }))
        assertNotNull(ToolSpecs.validate("set_alarm", buildJsonObject { put("hour", 6.5); put("minute", 0) }))
        assertNotNull(ToolSpecs.validate("nope", buildJsonObject { }))
        assertNotNull(ToolSpecs.validate("create_task", null))
    }

    @Test fun errorsAreFriendly() {
        val e = ClaudeWire.parseError(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""", null)
        assertTrue(e.friendly().contains("API key"))
        assertFalse(e.retryable)
    }
}

class FakeDevice : Device {
    val log = mutableListOf<String>()
    override fun setAlarm(hour: Int, minute: Int, label: String, days: List<DayOfWeek>) = "Alarm set".also { log += "alarm $hour:$minute" }
    override fun setTimer(seconds: Int, label: String) = "Timer set".also { log += "timer $seconds" }
    override fun torch(on: Boolean) = "Torch".also { log += "torch $on" }
    override suspend fun weather(): WeatherCache? = null
    override fun events(date: LocalDate): List<CalEvent>? = null
    override fun createEvent(title: String, start: ZonedDateTime, end: ZonedDateTime, location: String) = "Opened calendar"
    override fun open(route: String) { log += "open $route" }
    override fun startFocus(minutes: Int, label: String) = "Focus".also { log += "focus $minutes" }
    override fun startBreak(minutes: Int) = "Break".also { log += "break $minutes" }
    override fun stopFocus(completed: Boolean) { log += "stop $completed" }
}

/** Plays back scripted turns and records the requests it received. */
class ScriptedLlm(private val turns: MutableList<AssistantTurn>) : LlmClient {
    val requests = mutableListOf<ClaudeRequest>()
    override suspend fun stream(apiKey: String, req: ClaudeRequest, onText: (String) -> Unit, onTool: (String) -> Unit): AssistantTurn {
        requests += req
        return turns.removeAt(0).also { onText(it.text) }
    }
}

class BrainTest {
    private val now = ZonedDateTime.of(2026, 11, 8, 19, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
    private val repo = Repo(Store(null, CoroutineScope(Dispatchers.Unconfined)), Clock { now })
    private val content = Content(listOf(Shloka("2.47", 2, 47, "Krishna", "कर्मण्येवाधिकारस्ते", "karmaṇy evādhikāras te", "Act, without clinging to results.")), listOf("Keep going."))
    private val device = FakeDevice()

    private fun turn(stop: String, text: String = "", tool: Pair<String, JsonObject>? = null): AssistantTurn {
        val blocks = buildList {
            add(buildJsonObject { put("type", "thinking"); put("thinking", ""); put("signature", "s-$stop") })
            if (text.isNotEmpty()) add(buildJsonObject { put("type", "text"); put("text", text) })
            tool?.let { (n, i) -> add(buildJsonObject { put("type", "tool_use"); put("id", "tu1"); put("name", n); put("input", i) }) }
        }
        return AssistantTurn(JsonArray(blocks), text, listOfNotNull(tool?.let { ToolUse("tu1", it.first, it.second, it.second.toString()) }), stop, Usage(1000, 50, 0, 900), "claude-opus-5-5")
    }

    @Test fun toolLoopIsAppendOnlyAndReplaysThinking() = runTest {
        val llm = ScriptedLlm(mutableListOf(
            turn("tool_use", "On it.", "create_task" to buildJsonObject { put("title", "Book train tickets"); put("due", "2026-11-10T09:00") }),
            turn("end_turn", "Added for Tuesday 9 AM."),
        ))
        val spoken = mutableListOf<Spoken>()
        val brain = Brain(repo, ToolExecutor(repo, content, device), llm, { "sk-test" }, this, onSpoken = { spoken += it })
        brain.send("can you plan my train booking for tuesday morning")
        testScheduler.advanceUntilIdle()

        assertEquals("Book train tickets", repo.db.tasks.single().title)
        val conv = repo.db.conversations.single()
        assertEquals(listOf("user", "assistant", "user", "assistant"), conv.messages.map { it.role })
        assertTrue(conv.messages[2].hidden)
        assertEquals("Book train tickets", conv.messages[1].cards.single().title)

        // Second request = first request + exactly the appended messages.
        val (r1, r2) = llm.requests
        assertEquals(r1.messages, r2.messages.take(r1.messages.size))
        assertEquals(r1.system, r2.system)
        assertEquals(r1.tools, r2.tools)
        val replayedThinking = r2.messages[1]["content"]!!.jsonArray[0].jsonObject
        assertEquals("s-tool_use", replayedThinking["signature"]!!.jsonPrimitive.content)
        // The timestamp lives in the user message, not in the frozen system prompt.
        assertTrue(r1.messages[0]["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content.startsWith("[Sun 8 Nov 2026"))
        assertFalse(r1.system.contains("2026"))

        assertEquals("Added for Tuesday 9 AM.", spoken.single().text)
        assertEquals(2, repo.db.usage.size)
        assertNull(brain.state.value.error)
    }

    @Test fun offlineCommandsSkipTheNetwork() = runTest {
        val llm = ScriptedLlm(mutableListOf())
        val brain = Brain(repo, ToolExecutor(repo, content, device), llm, { null }, this)
        brain.send("spent 250 on dinner")
        testScheduler.advanceUntilIdle()
        assertEquals(250.0, repo.db.expenses.single().amount, 0.0)
        assertTrue(llm.requests.isEmpty())
        brain.send("timer 5 min")
        testScheduler.advanceUntilIdle()
        assertEquals("timer 300", device.log.last())
    }

    @Test fun refusalExcludesThePromptFromHistory() = runTest {
        val llm = ScriptedLlm(mutableListOf(
            AssistantTurn(JsonArray(emptyList()), "", emptyList(), "refusal", Usage(), "claude-opus-5-5"),
            turn("end_turn", "Sure."),
        ))
        val brain = Brain(repo, ToolExecutor(repo, content, device), llm, { "k" }, this)
        brain.send("something declined")
        testScheduler.advanceUntilIdle()
        brain.send("hello again")
        testScheduler.advanceUntilIdle()
        val second = llm.requests[1]
        assertEquals(1, second.messages.size) // the declined prompt is not resent
        assertEquals(Brain.REFUSAL, repo.db.conversations.single().messages[1].display)
    }

    @Test fun invalidToolInputReturnsErrorResult() = runTest {
        val bad = AssistantTurn(
            JsonArray(listOf(buildJsonObject { put("type", "tool_use"); put("id", "tu9"); put("name", "log_expense"); put("input", JsonObject(emptyMap())) })),
            "", listOf(ToolUse("tu9", "log_expense", null, "{\"amount\": ")), "tool_use", Usage(), "claude-opus-5-5",
        )
        val llm = ScriptedLlm(mutableListOf(bad, turn("end_turn", "Sorry, how much was it?")))
        val brain = Brain(repo, ToolExecutor(repo, content, device), llm, { "k" }, this)
        brain.send("log my lunch")
        testScheduler.advanceUntilIdle()
        assertTrue(repo.db.expenses.isEmpty())
        val result = llm.requests[1].messages.last()["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("true", result["is_error"]!!.jsonPrimitive.content)
        assertNotNull(AppJson.encodeToString(JsonObject.serializer(), result))
    }
}
