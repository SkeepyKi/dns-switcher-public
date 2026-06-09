package com.example.dns_switcher.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val DarkBackground = Color(0xFF0F1120)
    val CardBackground = Color(0xFF1A1D33)
    val CardBorder = Color(0xFF2A2E4A)
    val AccentCyan = Color(0xFF00D4FF)
    val AccentGreen = Color(0xFF00E676)
    val AccentRed = Color(0xFFFF5252)
    val AccentOrange = Color(0xFFFFAB40)
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFF9AA0B8)
    val InputBackground = Color(0xFF141729)
    val InputBorder = Color(0xFF2A2E4A)

    fun getPingColor(ping: String): Color {
        if (ping.contains("ms")) {
            val value = ping.substringBefore(" ").toIntOrNull() ?: 100
            return when {
                value < 50 -> AccentGreen
                value < 100 -> AccentOrange
                else -> AccentRed
            }
        }
        if (ping == "таймаут" || ping == "недоступен") {
            return AccentRed
        }
        return TextSecondary
    }
}
