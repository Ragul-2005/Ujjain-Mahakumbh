package com.mahakumbh.crowdsafety.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// Simple data class for weather info
data class WeatherInfo(
    val main: String,
    val description: String,
    val icon: String,
    val temp: Double,
    val alert: String?
)

object WeatherApi {
    private const val API_KEY = "AIzaSyCWfxP7erhy0vAR3ld4mE4OpcciXzx5Q4Q"
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    suspend fun getWeather(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL?lat=$lat&lon=$lon&appid=$API_KEY&units=metric"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val weather = json.getJSONArray("weather").getJSONObject(0)
            val main = weather.getString("main")
            val description = weather.getString("description")
            val icon = weather.getString("icon")
            val temp = json.getJSONObject("main").getDouble("temp")
            val alert = null // OpenWeatherMap OneCall API needed for real alerts
            WeatherInfo(main, description, icon, temp, alert)
        } catch (e: Exception) {
            null
        }
    }
}
