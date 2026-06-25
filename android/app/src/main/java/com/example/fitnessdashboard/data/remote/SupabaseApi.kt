package com.example.fitnessdashboard.data.remote

import com.example.fitnessdashboard.data.model.ActivityDto
import com.example.fitnessdashboard.data.model.ExercisePr
import com.example.fitnessdashboard.data.model.LiftSessionDto
import com.example.fitnessdashboard.data.model.MonthlyActivity
import com.example.fitnessdashboard.data.model.RouteDto
import com.example.fitnessdashboard.data.model.YtdActivityStat
import com.example.fitnessdashboard.data.model.YtdLiftStats
import retrofit2.http.GET
import retrofit2.http.Query

/** PostgREST endpoints exposed by Supabase under /rest/v1/. */
interface SupabaseApi {

    @GET("activities")
    suspend fun activities(
        @Query("select") select: String = "*",
        @Query("order") order: String = "start_time.desc",
    ): List<ActivityDto>

    @GET("activities")
    suspend fun routes(
        @Query("select") select: String = "polyline",
        @Query("sport") sport: String = "eq.run",
        @Query("polyline") hasPolyline: String = "not.is.null",
    ): List<RouteDto>

    @GET("v_ytd_activity_stats")
    suspend fun ytdActivityStats(
        @Query("select") select: String = "*",
    ): List<YtdActivityStat>

    @GET("v_monthly_activity")
    suspend fun monthlyActivity(
        @Query("select") select: String = "*",
        @Query("order") order: String = "month.asc",
    ): List<MonthlyActivity>

    @GET("v_ytd_lift_stats")
    suspend fun ytdLiftStats(
        @Query("select") select: String = "*",
    ): List<YtdLiftStats>

    @GET("v_exercise_prs")
    suspend fun exercisePrs(
        @Query("select") select: String = "*",
        @Query("order") order: String = "max_weight_kg.desc",
    ): List<ExercisePr>

    @GET("lift_sessions")
    suspend fun liftSessions(
        @Query("select") select: String = "id,date,title,lift_sets(exercise,sets,reps,weight_kg)",
        @Query("order") order: String = "date.desc",
    ): List<LiftSessionDto>
}
