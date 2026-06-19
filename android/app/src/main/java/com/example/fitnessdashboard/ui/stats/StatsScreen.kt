package com.example.fitnessdashboard.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
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
import com.example.fitnessdashboard.data.model.MonthlyActivity
import com.example.fitnessdashboard.data.model.YtdActivityStat
import com.example.fitnessdashboard.data.model.YtdLiftStats
import com.example.fitnessdashboard.ui.components.BarChart
import com.example.fitnessdashboard.ui.components.CenteredMessage
import com.example.fitnessdashboard.ui.components.LoadingBox
import com.example.fitnessdashboard.ui.components.StatCard
import com.example.fitnessdashboard.ui.components.sportVisual
import com.example.fitnessdashboard.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsData(
    val activity: List<YtdActivityStat>,
    val monthly: List<MonthlyActivity>,
    val lift: YtdLiftStats?,
)

class StatsViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<StatsData>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(
                    StatsData(
                        activity = repo.ytdActivityStats(),
                        monthly = repo.monthlyActivity(),
                        lift = repo.ytdLiftStats(),
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
fun StatsScreen() {
    val vm: StatsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year to date") },
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
private fun StatsContent(data: StatsData) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val order = mapOf("run" to 0, "swim" to 1)
        data.activity.sortedBy { order[it.sport] ?: 99 }.forEach { stat ->
            SportSection(stat, data.monthly.filter { it.sport == stat.sport })
        }
        LiftingSummary(data.lift)
        if (data.activity.isEmpty() && data.lift == null) {
            Text("No data yet this year. Once the sync runs, your stats appear here.")
        }
    }
}

@Composable
private fun SportSection(stat: YtdActivityStat, monthly: List<MonthlyActivity>) {
    val visual = sportVisual(stat.sport)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(visual.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Distance", Format.distance(stat.totalDistanceM), Modifier.weight(1f))
            StatCard("Time", Format.duration(stat.totalDurationS), Modifier.weight(1f))
            StatCard("Sessions", stat.sessions.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pace = if (stat.sport == "swim") Format.paceSwim(stat.avgSecPer100m)
            else Format.paceRun(stat.avgSecPerKm)
            StatCard("Avg pace", pace, Modifier.weight(1f))
            StatCard("Longest", Format.distance(stat.longestDistanceM), Modifier.weight(1f))
        }
        Text("Monthly distance (km)", style = MaterialTheme.typography.labelLarge)
        val bars = monthly.sortedBy { it.month }
            .map { Format.monthLabel(it.month) to (it.distanceM / 1000.0).toFloat() }
        BarChart(bars, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LiftingSummary(lift: YtdLiftStats?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Lifting", Icons.Filled.FitnessCenter)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Sessions", (lift?.sessions ?: 0).toString(), Modifier.weight(1f))
            StatCard("Volume", Format.volume(lift?.totalVolumeKg ?: 0.0), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector =
        sportVisualIcon(label),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private fun sportVisualIcon(label: String) = when (label) {
    "Running", "Swimming", "Lifting" -> sportVisual(
        when (label) {
            "Running" -> "run"
            "Swimming" -> "swim"
            else -> "lift"
        },
    ).icon
    else -> sportVisual("other").icon
}
