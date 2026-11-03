package app.anchor.ui

import java.text.DateFormat
import java.util.Date

object Format {
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun minutes(min: Long): String = duration(min * 60_000)

    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun time(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60 % 24, m % 60)
}
