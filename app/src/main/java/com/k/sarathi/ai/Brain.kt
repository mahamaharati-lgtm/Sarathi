package com.k.sarathi.ai

import com.k.sarathi.data.ChatMsg
import com.k.sarathi.data.Conversation
import com.k.sarathi.data.Repo
import com.k.sarathi.data.ToolCard
import com.k.sarathi.data.UsageEntry
import com.k.sarathi.domain.Command
import com.k.sarathi.domain.Commands
import com.k.sarathi.domain.Models
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatState(
    val conversationId: Long? = null,
    val busy: Boolean = false,
    val streaming: String = "",
    val tool: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val lastFailedText: String? = null,
)

/** A reply ready for the UI to speak aloud. */
data class Spoken(val text: String, val fromVoice: Boolean)

/**
 * Turns what the user says into actions. On-device commands run first (instant,
 * free, offline); everything else goes to Claude with tools. Conversations are
 * append-only so thinking blocks and the prompt cache stay valid.
 */
class Brain(
    private val repo: Repo,
    private val tools: ToolExecutor,
    private val client: LlmClient,
    private val apiKey: () -> String?,
    private val scope: CoroutineScope,
    private val onSpoken: (Spoken) -> Unit = {},
) {
    private val _state = MutableStateFlow(ChatState(conversationId = repo.db.conversations.firstOrNull()?.id))
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private var job: Job? = null

    val hasKey get() = !apiKey().isNullOrBlank()

    fun conversation(): Conversation? = _state.value.conversationId?.let { id -> repo.db.conversations.firstOrNull { it.id == id } }

    fun newChat() {
        cancel()
        _state.value = ChatState()
    }

    fun open(id: Long) {
        cancel()
        _state.value = ChatState(conversationId = id)
    }

    fun cancel() {
        job?.cancel(); job = null
        _state.update { it.copy(busy = false, streaming = "", tool = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null, notice = null) }

    fun retry() = _state.value.lastFailedText?.let { send(it) }

    fun send(raw: String, fromVoice: Boolean = false) {
        val text = raw.trim()
        if (text.isEmpty() || _state.value.busy) return
        job = scope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null, streaming = "", tool = null, lastFailedText = null) }
            try {
                handle(text, fromVoice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = (e as? ClaudeException)?.friendly() ?: (e.message ?: "Something went wrong"), lastFailedText = text) }
            } finally {
                _state.update { it.copy(busy = false, streaming = "", tool = null) }
            }
        }
    }

    private suspend fun handle(text: String, fromVoice: Boolean) {
        val now = repo.clock.now()
        val cmd = Commands.parse(text, now, repo.db.habits.filter { !it.archived }.map { it.name })
        if (cmd != null) {
            val out = tools.runCommand(cmd)
            val reply = when {
                cmd is Command.Open -> "Opening ${cmd.screen.title}."
                out == null -> "Done."
                else -> out.content
            }
            appendLocal(text, reply, listOfNotNull(out?.card))
            onSpoken(Spoken(reply, fromVoice))
            return
        }
        if (!hasKey) {
            appendLocal(
                text,
                "I can do that kind of request once Claude is connected — add an API key in Settings → AI. " +
                    "Offline I understand things like “spent 200 on lunch”, “remind me to call amma at 6pm”, “timer 10 min”, “15% of 2000” or “open gita”.",
                emptyList(), excluded = true,
            )
            return
        }
        askClaude(text, fromVoice)
    }

    private fun contextLine(): String {
        val s = repo.db.settings
        val now = repo.clock.now()
        val f = DateTimeFormatter.ofPattern(if (s.use24h) "EEE d MMM yyyy · HH:mm" else "EEE d MMM yyyy · h:mm a", Locale.ENGLISH)
        return "[${now.format(f)} · ${s.city}]"
    }

    private fun ensureConversation(firstText: String): Conversation {
        conversation()?.let { return it }
        val id = repo.newConversationId()
        val now = repo.clock.millis()
        val c = Conversation(
            id = id,
            title = firstText.lineSequence().first().take(48),
            created = now, updated = now,
            system = Prompts.system(repo),
            tools = ToolSpecs.all,
        )
        repo.upsertConversation(c)
        _state.update { it.copy(conversationId = id) }
        return c
    }

    private fun save(c: Conversation) = repo.upsertConversation(c.copy(updated = repo.clock.millis()))

    private fun appendLocal(userText: String, reply: String, cards: List<ToolCard>, excluded: Boolean = false) {
        var c = ensureConversation(userText)
        val ts = repo.clock.millis()
        c = c.copy(messages = c.messages +
            ChatMsg("user", ClaudeWire.userText("${contextLine()}\n$userText")["content"]!!, display = userText, ts = ts, excluded = excluded) +
            ChatMsg("assistant", ClaudeWire.assistantText(reply)["content"]!!, display = reply, cards = cards, ts = ts, excluded = excluded, model = "on-device"))
        save(c)
    }

    private fun apiMessages(c: Conversation): List<JsonObject> = c.messages.filter { !it.excluded }.map { m ->
        JsonObject(mapOf("role" to kotlinx.serialization.json.JsonPrimitive(m.role), "content" to m.content))
    }

    private suspend fun askClaude(text: String, fromVoice: Boolean) {
        val key = apiKey() ?: return
        var c = ensureConversation(text)
        c = c.copy(messages = c.messages + ChatMsg(
            "user", ClaudeWire.userText("${contextLine()}\n$text")["content"]!!, display = text, ts = repo.clock.millis(),
        ))
        save(c)

        val s = repo.db.settings
        var model = s.model
        if (s.monthlyCapUsd > 0 && Models.monthSpend(repo.db, repo.clock.now()) >= s.monthlyCapUsd && model != Models.CHEAP) {
            model = Models.CHEAP
            _state.update { it.copy(notice = "Monthly AI cap reached — answering with ${Models.get(model).name}.") }
        }

        var finalText = ""
        try {
            for (round in 0 until MAX_ROUNDS) {
                _state.update { it.copy(streaming = "", tool = null) }
                val req = ClaudeRequest(model, c.system, c.tools, apiMessages(c), s.effort.api)
                val turn = client.stream(
                    key, req,
                    onText = { d -> _state.update { it.copy(streaming = it.streaming + d) } },
                    onTool = { name -> _state.update { it.copy(tool = name) } },
                )
                recordUsage(turn, model)

                if (turn.stopReason == "refusal") {
                    c = conversation() ?: c
                    val last = c.messages.lastOrNull()
                    c = if (last != null && last.role == "user" && !last.hidden) {
                        c.copy(messages = c.messages.dropLast(1) + last.copy(excluded = true) +
                            ChatMsg("assistant", ClaudeWire.assistantText(REFUSAL)["content"]!!, display = REFUSAL, excluded = true, ts = repo.clock.millis()))
                    } else {
                        c.copy(messages = c.messages + ChatMsg("assistant", ClaudeWire.assistantText(REFUSAL)["content"]!!, display = REFUSAL, ts = repo.clock.millis()))
                    }
                    save(c)
                    finalText = REFUSAL
                    break
                }

                var assistant = ChatMsg("assistant", turn.content, display = turn.text, ts = repo.clock.millis(), model = turn.model.ifBlank { model })
                val needsTools = turn.toolUses.isNotEmpty() && (turn.stopReason == "tool_use" || turn.stopReason == "max_tokens")
                if (!needsTools) {
                    c = c.copy(messages = c.messages + assistant)
                    save(c)
                    finalText = turn.text
                    break
                }

                val cards = mutableListOf<ToolCard>()
                val results = turn.toolUses.map { tu ->
                    _state.update { it.copy(tool = tu.name) }
                    val problem = if (turn.stopReason == "max_tokens") "The reply was cut off before this call was complete; send it again."
                    else ToolSpecs.validate(tu.name, tu.input)
                    if (problem != null) {
                        Triple(tu.id, "Invalid input: $problem Received: ${tu.raw.take(400)}", true)
                    } else {
                        val out = tools.run(tu.name, tu.input!!)
                        out.card?.let(cards::add)
                        Triple(tu.id, out.content, out.isError)
                    }
                }
                assistant = assistant.copy(cards = cards)
                c = c.copy(messages = c.messages + assistant +
                    ChatMsg("user", ClaudeWire.toolResults(results)["content"]!!, hidden = true, ts = repo.clock.millis()))
                save(c)
                if (round == MAX_ROUNDS - 1) {
                    c = c.copy(messages = c.messages + ChatMsg("assistant", ClaudeWire.assistantText("Done.")["content"]!!, display = "Done.", ts = repo.clock.millis()))
                    save(c)
                    finalText = "Done."
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Keep the history well-formed for the next request.
            c = conversation() ?: c
            val last = c.messages.lastOrNull()
            c = when {
                last == null -> c
                last.role == "user" && !last.hidden -> c.copy(messages = c.messages.dropLast(1) + last.copy(excluded = true))
                last.role == "user" -> c.copy(messages = c.messages + ChatMsg("assistant", ClaudeWire.assistantText("(interrupted)")["content"]!!, display = "", hidden = true, ts = repo.clock.millis()))
                else -> c
            }
            save(c)
            throw e
        }
        if (finalText.isNotBlank()) onSpoken(Spoken(finalText, fromVoice))
    }

    private fun recordUsage(t: AssistantTurn, requested: String) {
        val m = t.model.ifBlank { requested }
        val u = t.usage
        repo.addUsage(UsageEntry(repo.clock.millis(), m, u.input, u.output, u.cacheRead, u.cacheWrite,
            Models.cost(m, u.input, u.output, u.cacheRead, u.cacheWrite)))
    }

    companion object {
        const val MAX_ROUNDS = 6
        const val REFUSAL = "I can't help with that one."
    }
}

object Prompts {
    fun system(repo: Repo): String {
        val s = repo.db.settings
        val facts = repo.db.memory.takeLast(40)
        return buildString {
            appendLine("You are Sarathi (सारथी, \"charioteer\"), ${s.userName}'s personal assistant on their Android phone. Like Krishna guiding Arjuna's chariot, you are calm, loyal, direct and quietly encouraging.")
            appendLine()
            appendLine("How to reply:")
            appendLine("- Keep replies short: one to three sentences unless asked for more. Plain text; use a short list only when it helps.")
            appendLine("- Reply in the language the user writes in (English by default; Kannada or Hindi if they use it).")
            appendLine("- When a tool fits, use it rather than describing how to do something. After tools run, confirm what changed in one line.")
            appendLine("- Each user message starts with [weekday date · time · city]. Treat it as the current local time, resolve words like \"tomorrow\" or \"tonight\" from it, and pass ISO local date-times to tools.")
            appendLine("- Amounts of money are in ${s.currency}. Read real numbers with tools; never invent them.")
            appendLine("- To act on an existing task, use its id from list_tasks or a clear title.")
            appendLine("- If something can't be done with the tools you have, say so plainly.")
            appendLine("- When the user shares a lasting preference or fact about themselves, save it with remember_fact.")
            appendLine()
            appendLine("About ${s.userName}:")
            if (facts.isEmpty()) appendLine("- Nothing remembered yet.")
            else facts.forEach { appendLine("- ${it.text}") }
        }.trim()
    }
}
