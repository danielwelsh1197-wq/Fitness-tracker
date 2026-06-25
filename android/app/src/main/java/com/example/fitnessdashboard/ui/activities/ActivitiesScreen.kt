package com.example.fitnessdashboard.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitnessdashboard.data.FitnessRepository
import com.example.fitnessdashboard.data.UiState
import com.example.fitnessdashboard.data.model.ActivityDto
import com.example.fitnessdashboard.data.model.MonthlyActivity
import com.example.fitnessdashboard.data.model.YtdActivityStat
import com.example.fitnessdashboard.ui.components.BarChart
import com.example.fitnessdashboard.ui.components.CenteredMessage
import com.example.fitnessdashboard.ui.components.LoadingBox
import com.example.fitnessdashboard.ui.components.StatCard
import com.example.fitnessdashboard.ui.components.sportVisual
import com.example.fitnessdashboard.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ActivityTypeData(
    val activities: List<ActivityDto>,
    val stats: List<YtdActivityStat>,
    val monthly: List<MonthlyActivity>,
)

class ActivityTypeViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<ActivityTypeData>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(
                    ActivityTypeData(
                        activities = repo.activities(),
                        stats = repo.ytdActivityStats(),
                        monthly = repo.monthlyActivity(),
                    ),
                )
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTypeScreen(sport: String, title: String, onOpenStats: () -> Unit) {
    val vm: ActivityTypeViewModel = viewModel(key = "activity-$sport")
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.Insights, contentDescription = "Stats")
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
                is UiState.Error -> CenteredMessage("Couldn't load $title.\n${s.message}")
                is UiState.Success -> ActivityTypeContent(sport, s.data)
            }
        }
    }
}

@Composable
private fun ActivityTypeContent(sport: String, data: ActivityTypeData) {
    val stat = data.stats.firstOrNull { it.sport == sport }
    val monthly = data.monthly.filter { it.sport == sport }.sortedBy { it.month }
    val activities = data.activities.filter { it.sport == sport }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { YtdStats(sport, stat) }

        item {
            Text("Monthly distance", style = MaterialTheme.typography.titleMedium)
            val bars = monthly.map { Format.monthLabel(it.month) to (it.distanceM / 1000.0).toFloat() }
            BarChart(bars, Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        item { Text("Recent sessions", style = MaterialTheme.typography.titleMedium) }
        if (activities.isEmpty()) {
            item { Text("No sessions yet.") }
        } else {
            items(activities, key = { it.activityId }) { ActivityRow(it) }
        }
    }
}

@Composable
private fun YtdStats(sport: String, stat: YtdActivityStat?) {
    val pace = when (sport) {
        "swim" -> Format.paceSwim(stat?.avgSecPer100m)
        else -> Format.paceRun(stat?.avgSecPerKm)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Distance (YTD)", Format.distance(stat?.totalDistanceM), Modifier.weight(1f))
            StatCard("Time (YTD)", Format.duration(stat?.totalDurationS), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Sessions", (stat?.sessions ?: 0).toString(), Modifier.weight(1f))
            StatCard("Avg pace", pace, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Longest", Format.distance(stat?.longestDistanceM), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityRow(a: ActivityDto) {
    val visual = sportVisual(a.sport)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(visual.icon, contentDescription = visual.label, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name ?: visual.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    Format.shortDate(a.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.distance(a.distanceM), style = MaterialTheme.typography.titleSmall)
                Text(Format.duration(a.durationS), style = MaterialTheme.typography.labelSmall)
                val pace = activityPace(a)
                if (pace.isNotEmpty()) {
                    Text(
                        pace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun activityPace(a: ActivityDto): String {
    val v = a.avgSpeedMps ?: return ""
    if (v <= 0) return ""
    return when (a.sport) {
        "run" -> Format.paceRun(1000.0 / v)
        "swim" -> Format.paceSwim(100.0 / v)
        else -> String.format(Locale.US, "%.1f km/h", v * 3.6)
    }
}
