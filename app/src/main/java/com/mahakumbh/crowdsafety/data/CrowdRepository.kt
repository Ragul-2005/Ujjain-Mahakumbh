package com.mahakumbh.crowdsafety.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CrowdRepository {
	val zoneRisks: Flow<List<ZoneRisk>>
	val heat: Flow<List<HeatCell>>
	val playbooks: Flow<List<Playbook>>
	val tasks: Flow<List<TaskItem>>
	val incidents: Flow<List<Incident>>
	val kpis: Flow<List<Kpi>>
	val activeVisitors: Flow<List<ActiveVisitor>>
	val weatherData: Flow<WeatherData>
	val lostPersonReports: Flow<List<LostPersonReport>>

	// Auth
	val currentUserRole: StateFlow<UserRole?>
	val currentUserId: StateFlow<String?>
	fun login(role: UserRole, id: String)
	fun logout()

	suspend fun start()
	fun activatePlaybook(id: String)
	fun simulatePlaybook(id: String)
	fun acceptTask(id: String)
	fun completeTask(id: String)

	// SOS
	fun logSos(type: SosType, sourceId: String, sourceRole: UserRole, lat: Double, lng: Double)
	
	// Visitor Tracking
	fun checkInVisitor(visitorId: String, role: UserRole, lat: Double, lng: Double, zone: String)
	fun checkOutVisitor(visitorId: String)
	fun updateVisitorLocation(visitorId: String, lat: Double, lng: Double)

	// Lost and Found
	fun addLostPersonReport(report: LostPersonReport)
	fun resolveLostPersonReport(reportId: String)

	// Donations
	val donations: kotlinx.coroutines.flow.Flow<List<Donation>>
	fun makeDonation(donation: Donation)
	fun getDonations(userId: String): List<Donation>

	// Reservations / Priority slots
	val reservations: kotlinx.coroutines.flow.Flow<List<Reservation>>
	fun makeReservation(reservation: Reservation)
	fun getReservations(userId: String): List<Reservation>
	fun verifyReservation(reservationId: String, verifierId: String): Boolean
	fun uploadEligibilityDocuments(userId: String, reservationId: String, idProof: String?, doctorCertificate: String?): Boolean
	fun scheduleReminder(reservationId: String, minutesBefore: Int)
}
