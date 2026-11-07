package org.freethinkers.transcriber.util

import java.util.Locale

object TimestampFormatter {
    fun format(milliseconds: Long): String {
        val safeMilliseconds = milliseconds.coerceAtLeast(0)
        val hours = safeMilliseconds / 3_600_000
        val minutes = safeMilliseconds / 60_000 % 60
        val seconds = safeMilliseconds / 1_000 % 60
        val millis = safeMilliseconds % 1_000
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
