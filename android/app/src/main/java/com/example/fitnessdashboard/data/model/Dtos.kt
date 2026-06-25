package com.example.fitnessdashboard.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single activity row (sourced from Strava). */
@Serializable
data class ActivityDto(
    @SerialName("activity_id") val activityId: Long,
    val sport: String,
    val name: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("duration_s") val durationS: Double? = null,
    @SerialName("avg_speed_mps") val avgSpeedMps: Double? = null,
    @SerialName("avg_hr") val avgHr: Int? = null,
    val calories: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
)

/** Year-to-date totals for one sport (from v_ytd_activity_stats). */
@Serializable
data class YtdActivityStat(
    val sport: String,
    val sessions: Int,
    @SerialName("total_distance_m") val totalDistanceM: Double,
    @SerialName("total_duration_s") val totalDurationS: Double,
    @SerialName("longest_distance_m") val longestDistanceM: Double,
    @SerialName("avg_sec_per_km") val avgSecPerKm: Double? = null,
    @SerialName("avg_sec_per_100m") val avgSecPer100m: Double? = null,
)

/** Per-month rollup for charts (from v_monthly_activity). */
@Serializable
data class MonthlyActivity(
    val sport: String,
    val month: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    val sessions: Int,
)

/** Year-to-date lifting summary (from v_ytd_lift_stats). */
@Serializable
data class YtdLiftStats(
    val sessions: Int,
    @SerialName("total_volume_kg") val totalVolumeKg: Double,
)

/** Per-exercise best weight this year (from v_exercise_prs). */
@Serializable
data class ExercisePr(
    val exercise: String,
    @SerialName("max_weight_kg") val maxWeightKg: Double,
    @SerialName("set_entries") val setEntries: Int,
)

/** A lifting session (Keep note) with its embedded sets. */
@Serializable
data class LiftSessionDto(
    val id: String,
    val date: String? = null,
    val title: String? = null,
    @SerialName("lift_sets") val sets: List<LiftSetDto> = emptyList(),
)

@Serializable
data class LiftSetDto(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    @SerialName("weight_kg") val weightKg: Double,
)
