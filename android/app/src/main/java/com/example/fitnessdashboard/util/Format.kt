package com.example.fitnessdashboard.util

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Display formatting for distances, durations, paces and dates. */
object Format {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    fun distance(meters: Double?): String {
        if (meters == null) return "—"
        return if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000.0)
        else "${meters.roundToInt()} m"
    }

    fun duration(seconds: Double?): String {
        if (seconds == null) return "—"
        val total = seconds.roundToInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun paceRun(secPerKm: Double?): String =
        if (secPerKm == null || secPerKm <= 0) "—" else mmss(secPerKm) + " /km"

    fun paceSwim(secPer100m: Double?): String =
        if (secPer100m == null || secPer100m <= 0) "—" else mmss(secPer100m) + " /100m"

    private fun mmss(seconds: Double): String {
        val t = seconds.roundToInt()
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60)
    }

    fun shortDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { OffsetDateTime.parse(iso).toLocalDate().format(dayMonth) }
            .getOrElse {
                runCatching { LocalDate.parse(iso.take(10)).format(dayMonth) }.getOrDefault("")
            }
    }

    fun monthLabel(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return runCatching { LocalDate.parse(isoDate.take(10)).format(monthShort) }.getOrDefault("")
    }

    fun weight(kg: Double): String =
        if (kg % 1.0 == 0.0) "${kg.toInt()} kg" else String.format(Locale.US, "%.1f kg", kg)

    fun volume(kg: Double): String =
        if (kg >= 1000) String.format(Locale.US, "%.1f t", kg / 1000.0) else "${kg.roundToInt()} kg"
}
