package com.k.sarathi.domain

/** WMO weather interpretation codes, as returned by Open-Meteo. */
object WeatherCodes {
    fun label(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80 -> "light showers"
        81 -> "showers"
        82 -> "heavy showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown"
    }

    fun emoji(code: Int, night: Boolean = false): String = when (code) {
        0 -> if (night) "🌙" else "☀️"
        1 -> if (night) "🌙" else "🌤"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈"
        else -> "◌"
    }

    fun isRainy(code: Int) = code in 51..67 || code in 80..82 || code in 95..99
}
