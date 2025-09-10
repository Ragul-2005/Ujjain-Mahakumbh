package com.mahakumbh.crowdsafety.data

data class SosAlert(
    val id: String,
    val type: SosType,
    val userId: String,
    val userRole: UserRole,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val status: SosStatus = SosStatus.Active
)

enum class SosStatus { Active, Responded, Escalated, Resolved }


data class Donation(
    val id: String,
    val userId: String,
    val amount: Double,
    val method: String,
    val category: String,
    val to: String,
    val receiptUrl: String?,
    val timestamp: Long,
    val verified: Boolean = false
)

data class DensityZone(
    val zoneId: String,
    val name: String,
    val densityLevel: DensityLevel,
    val lastUpdate: Long
)

enum class DensityLevel { Green, Yellow, Red }

data class Reservation(
    val id: String,
    val userId: String,
    val type: ReservationType,
    val slot: String,
    val zone: String,
    val status: ReservationStatus = ReservationStatus.Active
)

enum class ReservationType { Pregnant, Elderly }
enum class ReservationStatus { Active, Completed, Cancelled }

data class SharedLocationSession(
    val sessionId: String,
    val ownerId: String,
    val participantIds: List<String>,
    val expiresAt: Long,
    val isActive: Boolean = true
)

data class Event(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val location: String = "",
    val isPriority: Boolean = false
)

data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val lat: Double,
    val lng: Double,
    val accessible: Boolean = false
)

enum class PlaceType { Camp, Food, Shop, Toilet, Hospital, Security, Water }
