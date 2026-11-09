package com.k.sarathi.data

import android.content.Context
import com.k.sarathi.core.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate

@Serializable
data class Shloka(
    val ref: String,
    val ch: Int,
    val v: Int,
    val speaker: String,
    val sa: String,
    val iast: String,
    val en: String,
)

data class Chapter(val n: Int, val sanskrit: String, val english: String)

/** Bundled, offline content: Gita shlokas, original quotes, journal prompts. */
class Content(val shlokas: List<Shloka>, val quotes: List<String>) {

    fun shlokaOfDay(d: LocalDate): Shloka = shlokas[Math.floorMod(seed(d) * 7 + 3, shlokas.size)]
    fun quoteOfDay(d: LocalDate): String = quotes[Math.floorMod(seed(d) * 13 + 5, quotes.size)]
    fun promptOfDay(d: LocalDate): String = PROMPTS[Math.floorMod(seed(d), PROMPTS.size)]
    fun byRef(ref: String) = shlokas.firstOrNull { it.ref == ref }
    fun inChapter(n: Int) = shlokas.filter { it.ch == n }
    fun chaptersPresent() = CHAPTERS.filter { c -> shlokas.any { it.ch == c.n } }

    fun search(q: String): List<Shloka> {
        val n = q.trim().lowercase()
        if (n.isEmpty()) return shlokas
        return shlokas.filter { it.en.lowercase().contains(n) || it.iast.lowercase().contains(n) || it.ref == n }
    }

    private fun seed(d: LocalDate) = (d.toEpochDay() % 100_000).toInt()

    companion object {
        fun load(ctx: Context): Content {
            val a = ctx.assets
            val sh = a.open("data/shlokas.json").use { it.readBytes().decodeToString() }
            val qs = a.open("data/quotes.json").use { it.readBytes().decodeToString() }
            return Content(
                AppJson.decodeFromString(ListSerializer(Shloka.serializer()), sh),
                AppJson.decodeFromString(ListSerializer(String.serializer()), qs),
            )
        }

        val CHAPTERS = listOf(
            Chapter(1, "Arjuna Viṣāda Yoga", "Arjuna's grief"),
            Chapter(2, "Sāṅkhya Yoga", "The yoga of knowledge"),
            Chapter(3, "Karma Yoga", "The yoga of action"),
            Chapter(4, "Jñāna Karma Sannyāsa Yoga", "Knowledge and renouncing action"),
            Chapter(5, "Karma Sannyāsa Yoga", "Renunciation"),
            Chapter(6, "Dhyāna Yoga", "Meditation"),
            Chapter(7, "Jñāna Vijñāna Yoga", "Knowledge and wisdom"),
            Chapter(8, "Akṣara Brahma Yoga", "The imperishable"),
            Chapter(9, "Rāja Vidyā Rāja Guhya Yoga", "The royal secret"),
            Chapter(10, "Vibhūti Yoga", "Divine glories"),
            Chapter(11, "Viśvarūpa Darśana Yoga", "The cosmic form"),
            Chapter(12, "Bhakti Yoga", "Devotion"),
            Chapter(13, "Kṣetra Kṣetrajña Vibhāga Yoga", "The field and its knower"),
            Chapter(14, "Guṇatraya Vibhāga Yoga", "The three guṇas"),
            Chapter(15, "Puruṣottama Yoga", "The supreme person"),
            Chapter(16, "Daivāsura Sampad Vibhāga Yoga", "Divine and demonic natures"),
            Chapter(17, "Śraddhātraya Vibhāga Yoga", "Three kinds of faith"),
            Chapter(18, "Mokṣa Sannyāsa Yoga", "Freedom through letting go"),
        )

        val PROMPTS = listOf(
            "Where did today ask you to act without worrying about the result?",
            "What did you do today only because it was your duty — and how did it feel?",
            "Which worry took more of your energy than it deserved today?",
            "When were you steady today, the same in success and in setback?",
            "What is one small thing you did with full attention today?",
            "Who helped steer your chariot today, and did you thank them?",
            "What would you do tomorrow if you were not afraid of failing?",
            "Which habit is quietly shaping who you are becoming?",
        )
    }
}
