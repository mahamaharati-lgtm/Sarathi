package com.k.sarathi.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

/**
 * Small, safe arithmetic evaluator (no reflection/scripting): + - * / ^ ( ),
 * unary minus, "x"/"×"/"÷" spellings, "N% of M", "N% off M", "M + N%".
 */
object Calc {
    private val rePercentOf = Regex("""^\s*([\d.,]+)\s*%\s*(of|off)\s*([\d.,]+)\s*$""")
    private val rePlusPercent = Regex("""^\s*([\d.,]+)\s*([+-])\s*([\d.,]+)\s*%\s*$""")
    private val reLooksLikeMath = Regex("""^[\d\s.,+\-*/x×÷^()%]+$""")

    /** True when [s] is plausibly a calculation (used by the command router). */
    fun looksLikeMath(s: String): Boolean {
        val t = strip(s)
        if (rePercentOf.matches(t) || rePlusPercent.matches(t)) return true
        return reLooksLikeMath.matches(t) && t.any { it in "+-*/x×÷^%" } && t.any { it.isDigit() } &&
            !Regex("""^\s*-?[\d.,]+\s*$""").matches(t)
    }

    private fun strip(s: String) = s.lowercase()
        .removePrefix("what is").removePrefix("what's").removePrefix("calculate").removePrefix("calc")
        .replace("=", "").replace("?", "").trim()

    fun eval(input: String): Double {
        val s = strip(input)
        rePercentOf.matchEntire(s)?.let { m ->
            val p = num(m.groupValues[1]); val base = num(m.groupValues[3])
            return if (m.groupValues[2] == "of") base * p / 100 else base * (1 - p / 100)
        }
        rePlusPercent.matchEntire(s)?.let { m ->
            val base = num(m.groupValues[1]); val p = num(m.groupValues[3])
            return if (m.groupValues[2] == "+") base * (1 + p / 100) else base * (1 - p / 100)
        }
        return Parser(s.replace("×", "*").replace("x", "*").replace("÷", "/").replace(",", "")).parse()
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "undefined"
        val bd = BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() <= 0) "%,d".format(bd.toLong()) else bd.toPlainString()
    }

    private fun num(s: String) = s.replace(",", "").toDouble()

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double {
            val v = expr()
            skip()
            require(i == s.length) { "Unexpected '${s.substring(i)}'" }
            return v
        }
        fun skip() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                when {
                    eat('+') -> v += term()
                    eat('-') -> v -= term()
                    else -> return v
                }
            }
        }
        fun term(): Double {
            var v = factor()
            while (true) {
                skip()
                when {
                    eat('*') -> v *= factor()
                    eat('/') -> v /= factor()
                    eat('%') -> v /= 100.0
                    else -> return v
                }
            }
        }
        fun factor(): Double {
            val base = unary()
            skip()
            return if (eat('^')) base.pow(factor()) else base
        }
        fun unary(): Double {
            skip()
            if (eat('-')) return -unary()
            if (eat('+')) return unary()
            if (eat('(')) {
                val v = expr(); skip()
                require(eat(')')) { "Missing )" }
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start) { "Expected a number" }
            return s.substring(start, i).toDouble()
        }
        fun eat(c: Char): Boolean {
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }
    }
}
