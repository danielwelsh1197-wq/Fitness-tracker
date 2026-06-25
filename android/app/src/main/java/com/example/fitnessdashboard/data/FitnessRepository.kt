package com.example.fitnessdashboard.data

import com.example.fitnessdashboard.data.remote.ApiClient
import com.example.fitnessdashboard.data.remote.SupabaseApi

/** Thin repository over the Supabase API. Each call hits PostgREST directly. */
class FitnessRepository(private val api: SupabaseApi = ApiClient.api) {

    suspend fun activities() = api.activities()

    suspend fun routes() = api.routes()

    suspend fun ytdActivityStats() = api.ytdActivityStats()

    suspend fun monthlyActivity() = api.monthlyActivity()

    suspend fun ytdLiftStats() = api.ytdLiftStats().firstOrNull()

    suspend fun exercisePrs() = api.exercisePrs()

    suspend fun liftSessions() = api.liftSessions()
}
