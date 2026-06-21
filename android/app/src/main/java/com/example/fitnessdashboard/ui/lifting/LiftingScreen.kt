package com.example.fitnessdashboard.ui.lifting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitnessdashboard.data.FitnessRepository
import com.example.fitnessdashboard.data.UiState
import com.example.fitnessdashboard.data.model.ExercisePr
import com.example.fitnessdashboard.data.model.LiftSessionDto
import com.example.fitnessdashboard.data.model.YtdLiftStats
import com.example.fitnessdashboard.ui.components.CenteredMessage
import com.example.fitnessdashboard.ui.components.LoadingBox
import com.example.fitnessdashboard.ui.components.StatCard
import com.example.fitnessdashboard.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiftingData(
    val ytd: YtdLiftStats?,
    val prs: List<ExercisePr>,
    val sessions: List<LiftSessionDto>,
)

class LiftingViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<LiftingData>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(
                    LiftingData(
                        ytd = repo.ytdLiftStats(),
                        prs = repo.exercisePrs(),
                        sessions = repo.liftSessions(),
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
fun LiftingScreen(onOpenStats: () -> Unit) {
    val vm: LiftingViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lifting") },
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
                is UiState.Error -> CenteredMessage("Couldn't load lifting data.\n${s.message}")
                is UiState.Success -> LiftingContent(s.data)
            }
        }
    }
}

@Composable
private fun LiftingContent(data: LiftingData) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Sessions (YTD)", (data.ytd?.sessions ?: 0).toString(), Modifier.weight(1f))
                StatCard("Volume (YTD)", Format.volume(data.ytd?.totalVolumeKg ?: 0.0), Modifier.weight(1f))
            }
        }

        if (data.prs.isNotEmpty()) {
            item { Text("Personal bests (this year)", style = MaterialTheme.typography.titleMedium) }
            items(data.prs, key = { it.exercise }) { PrRow(it) }
        }

        item { Text("Recent sessions", style = MaterialTheme.typography.titleMedium) }
        if (data.sessions.isEmpty()) {
            item { Text("No lifting sessions yet.") }
        } else {
            items(data.sessions, key = { it.id }) { SessionCard(it) }
        }
    }
}

@Composable
private fun PrRow(pr: ExercisePr) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(pr.exercise, style = MaterialTheme.typography.bodyLarge)
            Text(Format.weight(pr.maxWeightKg), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SessionCard(session: LiftSessionDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val header = session.title?.takeIf { it.isNotBlank() } ?: "Workout"
            Text(header, style = MaterialTheme.typography.titleSmall)
            Text(
                Format.shortDate(session.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.sets.forEach { set ->
                Text(
                    "${set.exercise}  ${set.sets}×${set.reps} @ ${Format.weight(set.weightKg)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
