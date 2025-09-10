package com.mahakumbh.crowdsafety.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahakumbh.crowdsafety.data.*
import com.mahakumbh.crowdsafety.di.Locator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
	private val repo = Locator.repo
	private var job: Job? = null

	val zoneRisks: StateFlow<List<ZoneRisk>> = repo.zoneRisks.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)
	val heat: StateFlow<List<HeatCell>> = repo.heat.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)

	init {
		job = viewModelScope.launch { repo.start() }
	}

	override fun onCleared() {
		job?.cancel()
		super.onCleared()
	}
}

class PlaybooksViewModel : ViewModel() {
	private val repo = Locator.repo
	val playbooks: StateFlow<List<Playbook>> = repo.playbooks.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)
	fun simulate(id: String) { repo.simulatePlaybook(id) }
	fun activate(id: String) { repo.activatePlaybook(id) }
}

class TasksViewModel : ViewModel() {
	private val repo = Locator.repo
	val tasks: StateFlow<List<TaskItem>> = repo.tasks.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)
	init {
		// Ensure repository background simulation / seeding runs when Tasks screen is used
		viewModelScope.launch { repo.start() }
	}
	fun accept(id: String) { repo.acceptTask(id) }
	fun done(id: String) { repo.completeTask(id) }
}

class IncidentsViewModel : ViewModel() {
	private val repo = Locator.repo
	val incidents: StateFlow<List<Incident>> = repo.incidents.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)
}

class AnalyticsViewModel : ViewModel() {
	private val repo = Locator.repo
	val kpis: StateFlow<List<Kpi>> = repo.kpis.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
	)
}
