package com.example.fitnessdashboard.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitnessdashboard.data.FitnessRepository
import com.example.fitnessdashboard.data.UiState
import com.example.fitnessdashboard.data.model.ActivityDto
import com.example.fitnessdashboard.ui.components.CenteredMessage
import com.example.fitnessdashboard.ui.components.LineChart
import com.example.fitnessdashboard.ui.components.LineSeries
import com.example.fitnessdashboard.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** One year's cumulative-distance line: points are (dayOfYear, cumulative km). */
data class YearLine(val year: Int, val totalKm: Double, val points: List<Pair<Float, Float>>)

data class RunningYearStats(
    val years: List<YearLine>,
    val currentYear: Int,
    val currentKm: Double,
    val bestYear: Int?,
    val bestKm: Double,
    val neededKmPerWeek: Double?,
    val weeksRemaining: Int,
    val alreadyMatched: Boolean,
)

/** Pure computation (no Android deps) so it stays easy to reason about/test. */
fun computeRunningYearStats(
    activities: List<ActivityDto>,
    today: LocalDate = LocalDate.now(),
): RunningYearStats {
    val runsByYear = activities.asSequence()
        .filter { it.sport == "run" }
        .mapNotNull { a ->
            val date = a.startTime?.let(::parseDate) ?: return@mapNotNull null
            val km = (a.distanceM ?: return@mapNotNull null) / 1000.0
            date to km
        }
        .groupBy({ it.first.year }, { it.first to it.second })

    val years = runsByYear.toSortedMap().map { (year, runs) ->
        var cum = 0.0
        val points = mutableListOf(0f to 0f)   // anchor every line at the origin
        runs.sortedBy { it.first }.forEach { (date, km) ->
            cum += km
            points += date.dayOfYear.toFloat() to cum.toFloat()
        }
        YearLine(year, cum, points)
    }

    val currentYear = today.year
    val currentKm = years.firstOrNull { it.year == currentYear }?.totalKm ?: 0.0
    val best = years.filter { it.year < currentYear }.maxByOrNull { it.totalKm }

    val daysRemaining = ChronoUnit.DAYS
        .between(today, LocalDate.of(currentYear, 12, 31))
        .coerceAtLeast(0L)
    val weeksRemaining = daysRemaining / 7.0
    val remainingKm = (best?.totalKm ?: 0.0) - currentKm
    val matched = best != null && remainingKm <= 0
    val needed =
        if (best == null || matched || weeksRemaining <= 0) null else remainingKm / weeksRemaining

    return RunningYearStats(
        years = years,
        currentYear = currentYear,
        currentKm = currentKm,
        bestYear = best?.year,
        bestKm = best?.totalKm ?: 0.0,
        neededKmPerWeek = needed,
        weeksRemaining = weeksRemaining.roundToInt(),
        alreadyMatched = matched,
    )
}

private fun parseDate(iso: String): LocalDate? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate()
}.getOrElse { runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() }

class StatsViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<RunningYearStats>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(computeRunningYearStats(repo.activities()))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val vm: StatsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is UiState.Loading -> LoadingBox()
                is UiState.Error -> CenteredMessage("Couldn't load stats.\n${s.message}")
                is UiState.Success -> StatsContent(s.data)
            }
        }
    }
}

@Composable
private fun StatsContent(data: RunningYearStats) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Running — year on year", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cumulative distance by day of year",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (data.years.isEmpty()) {
            Text("No running data yet.")
            return@Column
        }

        // Most recent year gets the most prominent colour.
        val palette = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.error,
        )
        val colorOf = data.years.sortedByDescending { it.year }
            .mapIndexed { i, y -> y.year to palette[i % palette.size] }
            .toMap()

        LineChart(
            series = data.years.map {
                LineSeries(it.year.toString(), colorOf[it.year] ?: palette[0], it.points)
            },
            xTicks = listOf(1f to "Jan", 91f to "Apr", 182f to "Jul", 274f to "Oct", 335f to "Dec"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.years.sortedByDescending { it.year }.forEach { y ->
                val suffix = if (y.year == data.currentYear) " (to date)" else ""
                LegendRow(
                    color = colorOf[y.year] ?: palette[0],
                    label = "${y.year}$suffix",
                    value = km(y.totalKm),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        TargetCard(data)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TargetCard(d: RunningYearStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                d.bestYear == null ->
                    Text("Not enough history yet to compare against a full year.")

                d.alreadyMatched ->
                    Text("🎉 You've already matched your best year (${d.bestYear}: ${km(d.bestKm)}).")

                d.neededKmPerWeek == null ->
                    Text("Best year — ${d.bestYear}: ${km(d.bestKm)}.")

                else -> {
                    Text("Catch your best year", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Best: ${d.bestYear} (${km(d.bestKm)}). You're at ${km(d.currentKm)} so far in ${d.currentYear}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        String.format(Locale.US, "%.1f km/week", d.neededKmPerWeek ?: 0.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "needed over the remaining ${d.weeksRemaining} weeks to match it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun km(value: Double): String = String.format(Locale.US, "%,.0f km", value)
