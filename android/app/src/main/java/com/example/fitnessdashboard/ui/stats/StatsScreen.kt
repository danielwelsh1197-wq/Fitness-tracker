package com.example.fitnessdashboard.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.fitnessdashboard.ui.components.RouteHeatmap
import com.example.fitnessdashboard.ui.components.StatCard
import com.example.fitnessdashboard.ui.components.decodePolyline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------

/** A single run reduced to the fields these stats need. */
private data class Run(val date: LocalDate, val km: Double, val secPerKm: Double?, val area: String?)

data class YearLine(val year: Int, val totalKm: Double, val points: List<Pair<Float, Float>>)

data class AreaStat(val area: String, val runs: Int, val totalKm: Double, val paceSecPerKm: Double?)

data class RunningYearStats(
    val years: List<YearLine>,
    val currentYear: Int,
    val currentKm: Double,
    val bestYear: Int?,
    val bestKm: Double,
    val neededKmPerWeek: Double?,
    val weeksRemaining: Int,
    val alreadyMatched: Boolean,
    val areas: List<AreaStat>,
)

/** Pace over time: x is days-since-first-run, y is seconds per km. */
data class PaceTrend(
    val raw: List<Pair<Float, Float>>,
    val rolling: List<Pair<Float, Float>>,
    val spanDays: Float,
    val yearTicks: List<Pair<Float, String>>,
    val yMin: Float,
    val yMax: Float,
)

/** One day in the consistency calendar; null km means "no run". */
data class DayCell(val date: LocalDate, val km: Double)

data class Consistency(
    val weeks: List<List<DayCell?>>,   // 7 cells (Mon..Sun) per week column
    val maxDayKm: Double,
    val runsLast30: Int,
    val currentStreakWeeks: Int,
    val longestStreakWeeks: Int,
)

data class StatsBundle(
    val yearly: RunningYearStats,
    val pace: PaceTrend,
    val consistency: Consistency,
    val routes: List<List<Pair<Double, Double>>> = emptyList(),
)

// ---------------------------------------------------------------------------
// Computation (pure; no Android deps)
// ---------------------------------------------------------------------------

fun computeStats(activities: List<ActivityDto>, today: LocalDate = LocalDate.now()): StatsBundle {
    val runs = activities.asSequence()
        .filter { it.sport == "run" }
        .mapNotNull { a ->
            val date = a.startTime?.let(::parseDate) ?: return@mapNotNull null
            val km = (a.distanceM ?: return@mapNotNull null) / 1000.0
            if (km <= 0) return@mapNotNull null
            val sec = a.durationS
            val pace = if (sec != null && sec > 0) sec / km else null
            Run(date, km, pace, a.locationName?.takeIf { it.isNotBlank() })
        }
        .sortedBy { it.date }
        .toList()

    return StatsBundle(
        yearly = yearlyStats(runs, today),
        pace = paceTrend(runs),
        consistency = consistency(runs, today),
    )
}

private fun yearlyStats(runs: List<Run>, today: LocalDate): RunningYearStats {
    val years = runs.groupBy { it.date.year }.toSortedMap().map { (year, yrRuns) ->
        var cum = 0.0
        val points = mutableListOf(0f to 0f)
        yrRuns.forEach { r ->
            cum += r.km
            points += r.date.dayOfYear.toFloat() to cum.toFloat()
        }
        YearLine(year, cum, points)
    }
    val currentYear = today.year
    val currentKm = years.firstOrNull { it.year == currentYear }?.totalKm ?: 0.0
    val best = years.filter { it.year < currentYear }.maxByOrNull { it.totalKm }
    val daysRemaining = ChronoUnit.DAYS.between(today, LocalDate.of(currentYear, 12, 31)).coerceAtLeast(0L)
    val weeksRemaining = daysRemaining / 7.0
    val remainingKm = (best?.totalKm ?: 0.0) - currentKm
    val matched = best != null && remainingKm <= 0
    val needed = if (best == null || matched || weeksRemaining <= 0) null else remainingKm / weeksRemaining

    val areas = runs.asSequence()
        .filter { it.area != null }
        .groupBy { it.area!! }
        .map { (area, rs) ->
            val km = rs.sumOf { it.km }
            val paced = rs.filter { it.secPerKm != null }
            val pace = if (paced.isNotEmpty())
                paced.sumOf { it.secPerKm!! * it.km } / paced.sumOf { it.km } else null
            AreaStat(area, rs.size, km, pace)
        }
        .sortedByDescending { it.runs }

    return RunningYearStats(
        years = years,
        currentYear = currentYear,
        currentKm = currentKm,
        bestYear = best?.year,
        bestKm = best?.totalKm ?: 0.0,
        neededKmPerWeek = needed,
        weeksRemaining = weeksRemaining.roundToInt(),
        alreadyMatched = matched,
        areas = areas,
    )
}

private fun paceTrend(runs: List<Run>, window: Int = 7): PaceTrend {
    val paced = runs.filter { it.secPerKm != null }
    if (paced.size < 2) return PaceTrend(emptyList(), emptyList(), 1f, emptyList(), 0f, 1f)

    val first = paced.first().date
    fun x(d: LocalDate) = ChronoUnit.DAYS.between(first, d).toFloat()

    val raw = paced.map { x(it.date) to it.secPerKm!!.toFloat() }
    val rolling = paced.indices.map { i ->
        val from = maxOf(0, i - (window - 1))
        val avg = paced.subList(from, i + 1).map { it.secPerKm!! }.average()
        x(paced[i].date) to avg.toFloat()
    }

    val last = paced.last().date
    val paces = paced.map { it.secPerKm!! }
    // Pad the axis to whole half-minutes around the data for tidy gridlines.
    val yMin = (Math.floor(paces.minOrNull()!! / 30.0) * 30.0).toFloat()
    val yMax = (Math.ceil(paces.maxOrNull()!! / 30.0) * 30.0).toFloat()
    val ticks = (first.year..last.year).mapNotNull { y ->
        val jan1 = LocalDate.of(y, 1, 1)
        if (!jan1.isBefore(first)) x(jan1) to y.toString() else null
    }
    return PaceTrend(raw, rolling, x(last).coerceAtLeast(1f), ticks, yMin, yMax)
}

private fun consistency(runs: List<Run>, today: LocalDate, weeksBack: Int = 52): Consistency {
    val dailyKm = runs.groupBy { it.date }.mapValues { (_, rs) -> rs.sumOf { it.km } }

    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val gridStart = thisMonday.minusWeeks((weeksBack - 1).toLong())
    val weeks = (0 until weeksBack).map { col ->
        val weekMonday = gridStart.plusWeeks(col.toLong())
        (0..6).map { d ->
            val date = weekMonday.plusDays(d.toLong())
            if (date.isAfter(today)) null else DayCell(date, dailyKm[date] ?: 0.0)
        }
    }

    val runWeeks = runs.map { it.date.minusDays((it.date.dayOfWeek.value - 1).toLong()) }.toSortedSet()
    var longest = 0
    var streak = 0
    var prev: LocalDate? = null
    for (w in runWeeks) {
        streak = if (prev != null && ChronoUnit.DAYS.between(prev, w) == 7L) streak + 1 else 1
        longest = maxOf(longest, streak)
        prev = w
    }
    var current = 0
    var w = if (thisMonday in runWeeks) thisMonday else thisMonday.minusWeeks(1)
    while (w in runWeeks) { current++; w = w.minusWeeks(1) }

    return Consistency(
        weeks = weeks,
        maxDayKm = dailyKm.values.maxOrNull() ?: 1.0,
        runsLast30 = runs.count { !it.date.isBefore(today.minusDays(30)) },
        currentStreakWeeks = current,
        longestStreakWeeks = longest,
    )
}

private fun parseDate(iso: String): LocalDate? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate()
}.getOrElse { runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() }

// ---------------------------------------------------------------------------
// ViewModel + screen
// ---------------------------------------------------------------------------

class StatsViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<StatsBundle>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                val bundle = computeStats(repo.activities())
                val routes = repo.routes().map { decodePolyline(it.polyline) }
                UiState.Success(bundle.copy(routes = routes))
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
private fun StatsContent(data: StatsBundle) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        YearOnYear(data.yearly)
        PaceTrendSection(data.pace)
        ConsistencySection(data.consistency)
        if (data.yearly.areas.isNotEmpty()) AreasSection(data.yearly.areas)
        if (data.routes.isNotEmpty()) RoutesSection(data.routes)
    }
}

// --- Section: year-on-year cumulative -------------------------------------

@Composable
private fun YearOnYear(data: RunningYearStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Running — year on year", "Cumulative distance by day of year")
        if (data.years.isEmpty()) {
            Text("No running data yet.")
            return@Column
        }
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
            series = data.years.map { LineSeries(it.year.toString(), colorOf[it.year] ?: palette[0], it.points) },
            xTicks = listOf(1f to "Jan", 91f to "Apr", 182f to "Jul", 274f to "Oct", 335f to "Dec"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.years.sortedByDescending { it.year }.forEach { y ->
                val suffix = if (y.year == data.currentYear) " (to date)" else ""
                LegendRow(colorOf[y.year] ?: palette[0], "${y.year}$suffix", km(y.totalKm))
            }
        }
        TargetCard(data)
    }
}

// --- Section: pace trend ---------------------------------------------------

@Composable
private fun PaceTrendSection(p: PaceTrend) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Pace trend", "Average pace per run, smoothed (lower is faster)")
        if (p.raw.size < 2) {
            Text("Not enough runs with a pace yet.")
            return@Column
        }
        LineChart(
            series = listOf(
                LineSeries("runs", MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), p.raw, asPoints = true),
                LineSeries("trend", MaterialTheme.colorScheme.primary, p.rolling),
            ),
            xMax = p.spanDays,
            xTicks = p.yearTicks,
            yMin = p.yMin,
            yMax = p.yMax,
            yLabel = ::paceLabel,
        )
    }
}

// --- Section: consistency calendar ----------------------------------------

@Composable
private fun ConsistencySection(c: Consistency) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Consistency", "Runs per day over the last year")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Runs (30d)", c.runsLast30.toString(), Modifier.weight(1f))
            StatCard("Streak", "${c.currentStreakWeeks} wk", Modifier.weight(1f))
            StatCard("Longest", "${c.longestStreakWeeks} wk", Modifier.weight(1f))
        }
        val base = MaterialTheme.colorScheme.primary
        val empty = MaterialTheme.colorScheme.surfaceVariant
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            c.weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell ->
                        val color = if (cell == null || cell.km <= 0.0) empty
                        else base.copy(alpha = intensity(cell.km, c.maxDayKm))
                        Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    }
                }
            }
        }
        Text(
            "Mon at top of each column · darker = further that day",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun intensity(km: Double, max: Double): Float {
    val r = if (max <= 0) 0.0 else km / max
    return when {
        r < 0.25 -> 0.35f
        r < 0.5 -> 0.55f
        r < 0.75 -> 0.78f
        else -> 1f
    }
}

// --- Section: areas --------------------------------------------------------

@Composable
private fun AreasSection(areas: List<AreaStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Where you run", "Runs & average pace by neighbourhood")
        val max = (areas.firstOrNull()?.runs ?: 1).coerceAtLeast(1)
        areas.take(12).forEach { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(a.area, Modifier.width(108.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(16.dp).padding(horizontal = 8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (a.runs.toFloat() / max).coerceIn(0.04f, 1f))
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                    Text("${a.runs} run${if (a.runs == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        a.paceSecPerKm?.let { "${paceLabel(it.toFloat())}/km" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutesSection(routes: List<List<Pair<Double, Double>>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Your routes", "Every run overlaid — brighter where you go more often")
        RouteHeatmap(routes)
    }
}

// --- Shared pieces ---------------------------------------------------------

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun paceLabel(secPerKm: Float): String {
    val t = secPerKm.roundToInt()
    return String.format(Locale.US, "%d:%02d", t / 60, t % 60)
}
