package com.mahakumbh.crowdsafety.data

import androidx.compose.ui.graphics.Color

data class ZoneRisk(
	val zoneId: String,
	val displayName: String,
	val risk: Float,
	val minutesToCritical: Int
)

data class HeatCell(
	val x: Int,
	val y: Int,
	val intensity: Float
)

data class Playbook(
	val id: String,
	val title: String,
	val steps: String,
	val isActive: Boolean = false
)

data class TaskItem(
	val id: String,
	val title: String,
	val assignee: String,
	val location: String,
	val status: TaskStatus = TaskStatus.Pending,
	val priority: String = "Medium",
    val emergencyType: String? = null,
    val visitorLocation: String? = null,
    val message: String? = null
)

enum class TaskStatus { Pending, InProgress, Completed }

enum class Severity { Red, Yellow, Green }

data class Incident(
	val id: String,
	val message: String,
	val severity: Severity
)

data class Kpi(
	val label: String,
	val value: String
)

data class SosReport(
	val id: String,
	val type: SosType,
	val sourceId: String,
	val sourceRole: UserRole,
	val lat: Double,
	val lng: Double,
	val timestampMs: Long
)

enum class SosType { Panic, Medical, Security }
enum class UserRole { Visitor, Volunteer }

data class ActiveVisitor(
    val id: String,
    val role: UserRole,
    val lat: Double,
    val lng: Double,
    val zone: String,
    val checkInTime: Long,
    val displayName: String = "",
    val isOnline: Boolean = true,
    val needsAssist: Boolean = false,
    val assisted: Boolean = false
)

// Weather Feature Models
data class WeatherData(
    val temperature: Double,
    val humidity: Double,
    val rainfall: Double, // in mm
    val weatherType: WeatherType,
    val alerts: List<WeatherAlert>
)

data class WeatherAlert(
    val title: String,
    val message: String,
    val level: AlertLevel
)

enum class WeatherType {
    Sunny,
    Cloudy,
    Rain,
    Storm,
    ExtremeHeat
}

enum class AlertLevel {
    Advisory, // Green/Yellow
    Watch,    // Yellow
    Warning   // Red
}

// Finding Lost People Feature Models
data class LostPersonReport(
    val id: String,
    val reporterId: String,
    val name: String,
    val age: Int,
    val gender: String,
    val clothingDescription: String,
    val lastKnownLocation: String, // For simplicity, a string for now. Could be a LatLng object.
    val photoUrl: String,
    val status: ReportStatus,
    val timestamp: Long
)

enum class ReportStatus {
    Active,
    Resolved
}

// Lost item reports (for found/lost objects)
data class LostItemReport(
    val id: String,
    val reporterId: String,
    val title: String,
    val description: String,
    val location: String,
    val status: ReportStatus = ReportStatus.Active,
    val timestamp: Long
)