package com.mahakumbh.crowdsafety.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahakumbh.crowdsafety.data.CrowdRepository
import com.mahakumbh.crowdsafety.data.WeatherData
import com.mahakumbh.crowdsafety.data.WeatherType
import com.mahakumbh.crowdsafety.di.Locator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WeatherViewModel : ViewModel() {
    private val repo: CrowdRepository = Locator.repo

    val weatherData: StateFlow<WeatherData> = repo.weatherData.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeatherData(
            temperature = 25.0,
            humidity = 60.0,
            rainfall = 0.0,
            weatherType = WeatherType.Sunny,
            alerts = emptyList()
        )
    )
}
