package com.healthsync.phone.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthsync.phone.data.FitnessRepository
import com.healthsync.phone.data.PhonePreferences
import com.healthsync.phone.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class DashboardUiState(
    val latestHeartRate: HeartRateEntity? = null,
    val heartRate24h: List<HeartRateEntity> = emptyList(),
    val todaySteps: StepsEntity? = null,
    val stepsLast7Days: List<StepsEntity> = emptyList(),
    val stepsLast24h: List<StepBucketEntity> = emptyList(),
    val latestSpO2: SpO2Entity? = null,
    val spO2Last24h: List<SpO2Entity> = emptyList(),
    val latestSleep: SleepEntity? = null,
    val sleepLast7Nights: List<SleepEntity> = emptyList(),
    val recentWorkouts: List<WorkoutEntity> = emptyList(),
    val selectedWorkout: WorkoutEntity? = null,
    val stepGoal: Int = 10000,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val repository: FitnessRepository
) : AndroidViewModel(application) {

    private val preferences = PhonePreferences(application)
    private val sharedPreferences = application.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "step_goal") _uiState.update { it.copy(stepGoal = preferences.stepGoal) }
    }
    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var dataJob: Job? = null

    init {
        observeData()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun observeData() {
        dataJob?.cancel()
        dataJob = combine(
            repository.getLatestHeartRate(),
            repository.getHeartRateLast24h(),
            repository.getTodaySteps(),
            repository.getStepsLast7Days(),
            repository.getStepsLast24h(),
            repository.getLatestSpO2(),
            repository.getSpO2Last24h(),
            repository.getLatestSleep(),
            repository.getLast7NightsSleep(),
            repository.getRecentWorkouts()
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            DashboardUiState(
                latestHeartRate = values[0] as HeartRateEntity?,
                heartRate24h = values[1] as List<HeartRateEntity>,
                todaySteps = values[2] as StepsEntity?,
                stepsLast7Days = values[3] as List<StepsEntity>,
                stepsLast24h = values[4] as List<StepBucketEntity>,
                latestSpO2 = values[5] as SpO2Entity?,
                spO2Last24h = values[6] as List<SpO2Entity>,
                latestSleep = values[7] as SleepEntity?,
                sleepLast7Nights = values[8] as List<SleepEntity>,
                recentWorkouts = values[9] as List<WorkoutEntity>,
                selectedWorkout = _uiState.value.selectedWorkout,
                stepGoal = preferences.stepGoal,
                isLoading = false
            )
        }.catch {
            _uiState.update { state -> state.copy(isLoading = false, errorMessage = "History could not be loaded. Tap retry.") }
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    fun refreshData() {
        val failed = _uiState.value.errorMessage != null
        _uiState.update { it.copy(stepGoal = preferences.stepGoal, errorMessage = null) }
        if (failed) observeData()
    }

    fun selectWorkout(workout: WorkoutEntity?) {
        _uiState.update { it.copy(selectedWorkout = workout) }
    }

    fun updateStepGoal(newGoal: Int) {
        preferences.stepGoal = newGoal.coerceIn(1, 100000)
        _uiState.update { it.copy(stepGoal = preferences.stepGoal) }
    }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onCleared()
    }
}
