package com.mahakumbh.crowdsafety.data

import kotlinx.coroutines.flow.Flow

interface FeatureRepository {
    // Emergency SOS
    fun sendSos(alert: SosAlert)
    fun getActiveSos(): List<SosAlert>
    fun respondToSos(alertId: String, volunteerId: String)
    fun escalateSos(alertId: String)

    // Lost People
    fun reportLostPerson(report: LostPersonReport)
    fun getLostPeople(): List<LostPersonReport>
    fun closeLostPersonCase(reportId: String, volunteerId: String)

    // Lost Items (small reports for lost/found objects) - show up as volunteer tasks
    fun reportLostItem(item: LostItemReport)

    // Donation
    fun makeDonation(donation: Donation)
    fun getDonations(userId: String): List<Donation>

    // Density
    fun getDensityZones(): List<DensityZone>

    // Reservation
    fun makeReservation(reservation: Reservation)
    fun getReservations(userId: String): List<Reservation>
    // Verify reservation (volunteer scans QR/pass)
    fun verifyReservation(reservationId: String, verifierId: String): Boolean
    // Upload eligibility documents (IDs, doctor certificate) - simple mock accepts a URI or base64 string
    fun uploadEligibilityDocuments(userId: String, reservationId: String, idProof: String?, doctorCertificate: String?): Boolean
    // Schedule a reminder (mock)
    fun scheduleReminder(reservationId: String, minutesBefore: Int)

    // Location Sharing
    fun startLocationSession(session: SharedLocationSession)
    fun getActiveSessions(userId: String): List<SharedLocationSession>
    fun stopLocationSession(sessionId: String)

    // Events
    fun getEvents(): List<Event>
    val events: Flow<List<Event>>
    fun setEventReminder(eventId: String, userId: String)

    // Places
    fun getPlaces(type: PlaceType?): List<Place>
}
