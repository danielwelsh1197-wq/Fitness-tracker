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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.fitnessdashboard.ui.components.CenteredMessage
import com.example.fitnessdashboard.ui.components.LoadingBox
import com.example.fitnessdashboard.ui.components.sportVisual
import com.example.fitnessdashboard.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ActivitiesViewModel : ViewModel() {
    private val repo = FitnessRepository()
    private val _state = MutableStateFlow<UiState<List<ActivityDto>>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(repo.activities())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen() {
    val vm: ActivitiesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activities") },
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
                is UiState.Error -> CenteredMessage("Couldn't load activities.\n${s.message}")
                is UiState.Success -> {
                    val items = if (filter == "all") s.data else s.data.filter { it.sport == filter }
                    Column {
                        FilterRow(filter) { filter = it }
                        if (items.isEmpty()) {
                            CenteredMessage("No activities yet.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(items, key = { it.garminId }) { ActivityRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(filter: String, onSelect: (String) -> Unit) {
    val options = listOf("all" to "All", "run" to "Running", "swim" to "Swimming")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = filter == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
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
