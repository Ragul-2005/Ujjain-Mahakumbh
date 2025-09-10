package com.mahakumbh.crowdsafety.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

class MockRepository : CrowdRepository, FeatureRepository {
	// Guard to make start() idempotent — prevents missed initialization when a different
	// ViewModel starts the repo and avoids duplicate background loops if start() is
	// invoked multiple times from various ViewModels or code paths.
	private var started = false

	private val _zoneRisks = MutableStateFlow<List<ZoneRisk>>(emptyList())
	override val zoneRisks: Flow<List<ZoneRisk>> = _zoneRisks.asStateFlow()

	private val _heat = MutableStateFlow<List<HeatCell>>(emptyList())
	override val heat: Flow<List<HeatCell>> = _heat.asStateFlow()

	private val _playbooks = MutableStateFlow(
		listOf(
			Playbook("pb1", "Divert via Gate C", "VMS A3, 10 volunteers, rope lane. ETA 8 min"),
			Playbook("pb2", "Soft Metering at Entry E", "Throttle inflow to 60% for 10 min. ETA 5 min"),
			Playbook("pb3", "Clear Emergency Corridor", "Hold counterflow, create 3m lane. ETA 3 min")
		)
	)
	override val playbooks: Flow<List<Playbook>> = _playbooks.asStateFlow()

	private val _tasks = MutableStateFlow(
		listOf(
			TaskItem("t1", "Open Gate C", "Team 12", "200m", TaskStatus.Pending, "High"),
			TaskItem("t2", "Create one-way lane", "Team 5", "Bridge C", TaskStatus.InProgress, "Medium"),
			TaskItem("t3", "Report blockage type", "Volunteer", "Zone A", TaskStatus.Completed, "Low")
		)
	)
	override val tasks: Flow<List<TaskItem>> = _tasks.asStateFlow()

	private val _incidents = MutableStateFlow(
		listOf(
			Incident("i1", "Yellow risk predicted in Zone B in 7 min", Severity.Yellow),
			Incident("i2", "VMS A3 offline - ticket created", Severity.Green),
			Incident("i3", "Reverse flow detected near Bridge C", Severity.Red)
		)
	)
	override val incidents: Flow<List<Incident>> = _incidents.asStateFlow()

	private val _kpis = MutableStateFlow(
		listOf(
			Kpi("Predictions Accuracy", "88%"),
			Kpi("Avg alert->action", "1m 32s"),
			Kpi("Near misses reduced", "62%")
		)
	)
	override val kpis: Flow<List<Kpi>> = _kpis.asStateFlow()

	private val _activeVisitors = MutableStateFlow<List<ActiveVisitor>>(emptyList())
	override val activeVisitors: Flow<List<ActiveVisitor>> = _activeVisitors.asStateFlow()

	private val _donations = MutableStateFlow<List<Donation>>(emptyList())
	override val donations: Flow<List<Donation>> = _donations.asStateFlow()

	// Events store
	private val _events = MutableStateFlow<List<Event>>(
		listOf(
			Event(id = "e1", name = "Morning Aarti", description = "Blessings at Sangam Ghat", startTime = System.currentTimeMillis() + 3600000, endTime = System.currentTimeMillis() + 5400000, location = "Sangam Ghat"),
			Event(id = "e2", name = "Cultural Dance", description = "Folk performances", startTime = System.currentTimeMillis() + 7200000, endTime = System.currentTimeMillis() + 9000000, location = "Cultural Arena"),
			Event(id = "e3", name = "Evening Aarti", description = "Ceremony at sunset", startTime = System.currentTimeMillis() + 18000000, endTime = System.currentTimeMillis() + 19800000, location = "Sangam Ghat")
		)
	)

	// expose events as read-only Flow
	override val events: kotlinx.coroutines.flow.Flow<List<Event>> = _events.asStateFlow()

	// allow adding events in mock
	fun addEvent(event: Event) {
		_events.update { list -> list + event }
		// Announce the new event so visitors receive a notification
		_incidents.update { list -> list + Incident("ev-notif-${event.id}", "Event posted: ${event.name} at ${event.location} — starts at ${event.startTime}", Severity.Green) }
	}


	// Reservations store
	private val _reservations = MutableStateFlow<List<Reservation>>(emptyList())
	override val reservations: Flow<List<Reservation>> = _reservations.asStateFlow()

	// eligibility docs store (simple mock: map reservationId -> pair(idProof, doctorCert))
	private val _eligibilityDocs = mutableMapOf<String, Pair<String?, String?>>()

	// reminders tracking (reservationId -> job simulated by coroutine)


	private val _weatherData = MutableStateFlow(
		WeatherData(
			temperature = 25.0,
			humidity = 60.0,
			rainfall = 0.0,
			weatherType = WeatherType.Sunny,
			alerts = emptyList()
		)
	)
	override val weatherData: Flow<WeatherData> = _weatherData.asStateFlow()

	private val _userRole = MutableStateFlow<UserRole?>(null)
	override val currentUserRole: StateFlow<UserRole?> = _userRole
	private val _userId = MutableStateFlow<String?>(null)
	override val currentUserId: StateFlow<String?> = _userId

	private val _lostPersonReports = MutableStateFlow(
		listOf(
			LostPersonReport(
				"1",
				"user1",
				"Ramesh Kumar",
				70,
				"Male",
				"Blue shirt, white pants",
				"Sangam Zone",
				"",
				ReportStatus.Active,
				System.currentTimeMillis()
			),
			LostPersonReport(
				"2",
				"user2",
				"Sita Devi",
				65,
				"Female",
				"Red saree",
				"Hanuman Garhi",
				"",
				ReportStatus.Active,
				System.currentTimeMillis()
			)
		)
	)
	override val lostPersonReports: Flow<List<LostPersonReport>> = _lostPersonReports.asStateFlow()

	override fun login(role: UserRole, id: String) { _userRole.value = role; _userId.value = id }
	override fun logout() { _userRole.value = null; _userId.value = null }

	// FeatureRepository compatibility: accept SosAlerts as well
	override fun sendSos(alert: SosAlert) {
		// forward to the lower-level logSos implementation
		logSos(alert.type, alert.userId, alert.userRole, alert.lat, alert.lng)
	}

	override suspend fun start() {
		// make start idempotent
		if (started) return
		started = true
		// initialize baseline data
		_zoneRisks.value = listOf(
			ZoneRisk("zA", "Zone A", 0.72f, 3),
			ZoneRisk("zB", "Zone B", 0.35f, 7),
			ZoneRisk("bC", "Bridge C", 0.88f, 2)
		)
		_heat.value = generateHeat()
		
		// Initialize some mock active visitors
		_activeVisitors.value = listOf(
			ActiveVisitor("visitor1", UserRole.Visitor, 20.5937, 78.9629, "Zone A", System.currentTimeMillis() - 300000),
			ActiveVisitor("visitor2", UserRole.Visitor, 20.5938, 78.9630, "Zone B", System.currentTimeMillis() - 180000),
			ActiveVisitor("visitor3", UserRole.Visitor, 20.5936, 78.9628, "Zone C", System.currentTimeMillis() - 120000),
			ActiveVisitor("volunteer1", UserRole.Volunteer, 20.5939, 78.9631, "Zone D", System.currentTimeMillis() - 600000),
			ActiveVisitor("visitor4", UserRole.Visitor, 20.5935, 78.9627, "Zone A", System.currentTimeMillis() - 90000),
			ActiveVisitor("visitor5", UserRole.Visitor, 20.5940, 78.9632, "Zone B", System.currentTimeMillis() - 240000)
		)

		while (true) {
			delay(15000) // Increased from 5000 to 15000 to slow down alerts
			// Update zone risks and TTC
			_zoneRisks.update { list ->
				list.map { z ->
					val newRisk = (z.risk + Random.nextFloat() * 0.14f - 0.07f).coerceIn(0f, 1f)
					z.copy(risk = newRisk, minutesToCritical = ((1f - newRisk) * 10).toInt().coerceAtLeast(0))
				}
			}
			// Update heat grid
			_heat.value = generateHeat()
			// Nudge playbook ETAs to show visible change
			_playbooks.update { list ->
				list.map { p ->
					val delta = listOf(-1, 0, 1).random()
					val newEta = (Regex("ETA (\\d+) min").find(p.steps)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5)
					val bounded = (newEta + delta).coerceIn(2, 10)
					p.copy(steps = Regex("ETA \\d+ min").replace(p.steps) { "ETA $bounded min" })
				}
			}
			// Adjust task distances or leave as is if non-distance
			_tasks.update { list ->
				list.map { t ->
					val match = Regex("(\\d+)m$").find(t.location)
					if (match != null) {
						val meters = match.groupValues[1].toInt()
						val newMeters = (meters + listOf(-10, -5, 0, 5, 10).random()).coerceAtLeast(50)
						t.copy(location = "${newMeters}m")
					} else t
				}
			}
			// Rotate incidents less frequently - only every 3rd cycle
			if (Random.nextInt(3) == 0) {
				_incidents.update { list -> if (list.isNotEmpty()) list.drop(1) + list.first() else list }
			}
			// Wiggle KPIs within reasonable bounds
			_kpis.update { list ->
				list.map { k ->
					when {
						k.label.contains("Accuracy", ignoreCase = true) -> k.copy(value = wigglePercent(k.value, 80, 95))
						k.label.contains("alert->action", ignoreCase = true) -> k.copy(value = wiggleTime(k.value, 60, 150))
						k.label.contains("Near misses", ignoreCase = true) -> k.copy(value = wigglePercent(k.value, 40, 75))
						else -> k
					}
				}
			}
			
			// Simulate visitor movement
			_activeVisitors.update { visitors ->
				visitors.map { visitor ->
					val latDelta = (Random.nextFloat() - 0.5f) * 0.001f // Small movement
					val lngDelta = (Random.nextFloat() - 0.5f) * 0.001f
					visitor.copy(
						lat = (visitor.lat + latDelta).coerceIn(20.5900, 20.6000),
						lng = (visitor.lng + lngDelta).coerceIn(78.9600, 78.9700)
					)
				}
			}

			// Simulate weather changes
			_weatherData.update { current ->
				val tempChange = Random.nextDouble(-0.5, 0.5)
				val humidityChange = Random.nextDouble(-2.0, 2.0)
				val newTemp = (current.temperature + tempChange).coerceIn(15.0, 45.0)
				val newHumidity = (current.humidity + humidityChange).coerceIn(30.0, 90.0)

				val (newWeatherType, newRainfall, newAlerts) = when (Random.nextInt(10)) {
					in 0..5 -> Triple(WeatherType.Sunny, 0.0, emptyList())
					in 6..7 -> Triple(WeatherType.Cloudy, 0.0, listOf(
						WeatherAlert("Cloudy Skies", "Expect overcast conditions for the next few hours.", AlertLevel.Advisory)
					))
					8 -> Triple(WeatherType.Rain, Random.nextDouble(0.5, 5.0), listOf(
						WeatherAlert("Rain Expected", "Rain expected in 20 minutes near Sangam Zone.", AlertLevel.Watch)
					))
					9 -> Triple(WeatherType.Storm, Random.nextDouble(5.0, 15.0), listOf(
						WeatherAlert("Storm Warning", "Thunderstorm approaching. Seek shelter immediately.", AlertLevel.Warning)
					))
					else -> Triple(WeatherType.ExtremeHeat, 0.0, listOf(
						WeatherAlert("Extreme Heat", "High temperatures expected. Stay hydrated.", AlertLevel.Warning)
					))
				}

				current.copy(
					temperature = newTemp,
					humidity = newHumidity,
					rainfall = newRainfall,
					weatherType = newWeatherType,
					alerts = newAlerts
				)
			}
		}
	}

	override fun addLostPersonReport(report: LostPersonReport) {
		_lostPersonReports.update { it + report }
	}

	override fun makeDonation(donation: Donation) {
		_donations.update { it + donation }
	}

	override fun makeReservation(reservation: Reservation) {
		// Assign dynamic slot based on current zone risks: prefer zones with lower risk
		val zoneRisk = _zoneRisks.value.firstOrNull { it.zoneId == reservation.zone }
		val assignedOffsetMin = when {
			zoneRisk == null -> 10
			zoneRisk.risk > 0.7f -> 30 // busy zone, later slot
			zoneRisk.risk > 0.4f -> 15
			else -> 5
		}
		val slotTime = System.currentTimeMillis() + assignedOffsetMin * 60 * 1000L
		val slotLabel = android.text.format.DateFormat.format("hh:mm a", java.util.Date(slotTime)).toString()
		val resWithSlot = reservation.copy(slot = slotLabel, status = ReservationStatus.Active)
		_reservations.update { it + resWithSlot }
		// Notify volunteers by creating a task to assist (mock)
		val taskId = "assist-${resWithSlot.id}"
		_tasks.update { list ->
			list + TaskItem(
				id = taskId,
				title = "Assist reservation ${resWithSlot.id}",
				assignee = "Available Volunteer",
				location = resWithSlot.zone,
				status = TaskStatus.Pending,
				priority = "High",
				emergencyType = null,
				visitorLocation = ""
			)
		}
	}

	override fun verifyReservation(reservationId: String, verifierId: String): Boolean {
		val idx = _reservations.value.indexOfFirst { it.id == reservationId }
		if (idx == -1) return false
		// mark as completed/verified
		_reservations.update { list ->
			list.map { if (it.id == reservationId) it.copy(status = ReservationStatus.Completed) else it }
		}
		// create a small incident log to indicate volunteer assisted
		_incidents.update { it + Incident("ver-$reservationId", "Reservation $reservationId verified by $verifierId", Severity.Green) }
		// mark any active visitor matching this reservation's userId as assisted
		val res = _reservations.value.firstOrNull { it.id == reservationId }
		if (res != null) {
			_activeVisitors.update { list ->
				list.map { v -> if (v.id == res.userId) v.copy(needsAssist = true, assisted = true) else v }
			}
			// Also create a targeted notification/incident for the visitor so their client can display it
			val userMsg = "[to:${res.userId}] Your reservation ${res.id} at ${res.zone.ifBlank { "Triveni Bandh Prayag" }} for ${res.slot} has been accepted by $verifierId."
			_incidents.update { it + Incident("user-notif-${res.id}-${res.userId}", userMsg, Severity.Green) }
		}
		return true
	}

	override fun uploadEligibilityDocuments(userId: String, reservationId: String, idProof: String?, doctorCertificate: String?): Boolean {
		// very simple mock acceptance: store and return true
		_eligibilityDocs[reservationId] = Pair(idProof, doctorCertificate)
		_incidents.update { it + Incident("doc-$reservationId", "Eligibility docs uploaded for $reservationId", Severity.Green) }
		return true
	}

	override fun scheduleReminder(reservationId: String, minutesBefore: Int) {
		// In a mock, schedule a coroutine that will create an incident reminder minutesBefore minutes before the slot.
		val res = _reservations.value.firstOrNull { it.id == reservationId } ?: return
		// parse slot time if possible - here we approximate by adding a short delay for demo
		CoroutineScope(Dispatchers.Default).launch {
			delay((minutesBefore * 1000L).coerceAtLeast(5000L)) // scaled down for demo; 1s per minute
			_incidents.update { list -> list + Incident("rem-${reservationId}", "Reminder: Reservation ${reservationId} at ${res.slot} — arrive soon.", Severity.Green) }
		}
	}

	override fun getDonations(userId: String): List<Donation> {
		return _donations.value.filter { it.userId == userId }
	}

	override fun getReservations(userId: String): List<Reservation> {
		return _reservations.value.filter { it.userId == userId }
	}

	override fun resolveLostPersonReport(reportId: String) {
		_lostPersonReports.update { reports ->
			reports.map {
				if (it.id == reportId) it.copy(status = ReportStatus.Resolved) else it
			}
		}
	}

	private fun generateHeat(): List<HeatCell> = buildList {
		for (x in 0 until 10) {
			for (y in 0 until 5) {
				add(HeatCell(x, y, Random.nextFloat()))
			}
		}
	}

	private fun wigglePercent(current: String, min: Int, max: Int): String {
		val num = current.removeSuffix("%").toIntOrNull() ?: ((min + max) / 2)
		val delta = listOf(-3, -2, -1, 0, 1, 2, 3).random()
		return "${(num + delta).coerceIn(min, max)}%"
	}

	private fun wiggleTime(current: String, minSec: Int, maxSec: Int): String {
		val parts = Regex("(\\dm \\d{1,2}s)").find(current)
		val total = if (parts != null) {
			val m = Regex("(\\d+)m").find(current)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
			val s = Regex("(\\d+)s").find(current)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 30
			m * 60 + s
		} else 90
		val delta = listOf(-10, -5, 0, 5, 10).random()
		val bounded = (total + delta).coerceIn(minSec, maxSec)
		val m = bounded / 60
		val s = bounded % 60
		return "${m}m ${s}s"
	}

	override fun activatePlaybook(id: String) {
		_playbooks.update { list -> list.map { if (it.id == id) it.copy(isActive = true) else it } }
	}

	override fun simulatePlaybook(id: String) {
		// no-op placeholder for now
	}

	override fun acceptTask(id: String) {
		_tasks.update { list -> list.map { if (it.id == id) it.copy(status = TaskStatus.InProgress) else it } }
	}

	override fun completeTask(id: String) {
		_tasks.update { list -> list.map { if (it.id == id) it.copy(status = TaskStatus.Completed) else it } }
	}

	override fun logSos(type: SosType, sourceId: String, sourceRole: UserRole, lat: Double, lng: Double) {
		val ts = System.currentTimeMillis()
		val id = "sos-${ts}"
		// Create incident
		val sev = when (type) { 
			SosType.Panic -> Severity.Red
			SosType.Medical -> Severity.Yellow
			SosType.Security -> Severity.Red 
		}
		
		val emergencyMessage = when (type) {
			SosType.Panic -> "🚨 URGENT: Panic emergency reported by visitor ${sourceId} - Immediate response required!"
			SosType.Medical -> "🏥 MEDICAL: Health emergency reported by visitor ${sourceId} - Medical team needed!"
			SosType.Security -> "🔒 SECURITY: Safety threat reported by visitor ${sourceId} - Security response required!"
		}
		
		_incidents.update { it + Incident(id, emergencyMessage, sev) }
		
		// Create urgent task for volunteers
		val taskTitle = when (type) {
			SosType.Panic -> "🚨 URGENT: Panic Emergency Response"
			SosType.Medical -> "🏥 Medical Emergency Response"
			SosType.Security -> "🔒 Security Threat Response"
		}
		
		_tasks.update { tasks ->
			tasks + TaskItem(
				id = "task-${ts}",
				title = taskTitle,
				assignee = "Available Volunteer",
				location = ("%.5f, %.5f").format(lat, lng),
				status = TaskStatus.Pending,
				priority = if (type == SosType.Panic) "CRITICAL" else "High",
				emergencyType = type.name,
				visitorLocation = ("%.5f, %.5f").format(lat, lng),
				message = emergencyMessage
			)
		}

		// Escalation: if no volunteer accepts within a window, add escalation incident
		// (In mock: schedule after 20s to demonstrate quickly)
		CoroutineScope(Dispatchers.Default).launch {
			delay(20000)
			val stillPending = _tasks.value.any { it.id == "task-${ts}" && it.status == TaskStatus.Pending }
			if (stillPending) {
				_incidents.update { list -> list + Incident("esc-$ts", "Escalation: No response yet to ${type.name} SOS from $sourceId. Supervisors notified.", Severity.Red) }
			}
		}
	}

	// FeatureRepository wrappers / stubs
	override fun getActiveSos(): List<SosAlert> {
		// convert recent incidents of type Panic/Medical/Security into SosAlert stubs if needed
		return emptyList()
	}

	override fun respondToSos(alertId: String, volunteerId: String) {
		// mark task or incident as responded (no-op in mock)
	}

	override fun escalateSos(alertId: String) {
		_incidents.update { list -> list + Incident("esc-$alertId", "Escalation requested for SOS $alertId", Severity.Red) }
	}

	override fun reportLostPerson(report: LostPersonReport) {
		addLostPersonReport(report)
	}

	// Report a lost item: create a volunteer task and record an incident so volunteers see it
	override fun reportLostItem(item: LostItemReport) {
		// create a small incident for visibility
		_incidents.update { list -> list + Incident("lostitem-${item.id}", "Lost item: ${item.title} — ${item.location}", Severity.Green) }

		// create a volunteer task so volunteers can accept and help
		val ts = System.currentTimeMillis()
		_tasks.update { list ->
			list + TaskItem(
				id = "lostitem-${ts}",
				title = "🔎 Lost Item: ${item.title}",
				assignee = "Available Volunteer",
				location = item.location,
				status = TaskStatus.Pending,
				priority = "Medium",
				emergencyType = null,
				visitorLocation = item.location,
				message = item.description
			)
		}
	}

	override fun getLostPeople(): List<LostPersonReport> = _lostPersonReports.value

	override fun closeLostPersonCase(reportId: String, volunteerId: String) {
		resolveLostPersonReport(reportId)
	}

	override fun getDensityZones(): List<DensityZone> = emptyList()

	override fun startLocationSession(session: SharedLocationSession) {
		// no-op
	}

	override fun getActiveSessions(userId: String): List<SharedLocationSession> = emptyList()

	override fun stopLocationSession(sessionId: String) {
		// no-op
	}

	override fun getEvents(): List<Event> = _events.value

	override fun setEventReminder(eventId: String, userId: String) {
		// In the mock: schedule a short demo reminder (fires quickly so users see it)
		val ev = _events.value.firstOrNull { it.id == eventId } ?: return
		val ts = System.currentTimeMillis()
		// schedule a coroutine to add a reminder incident shortly (demo: 5 seconds)
		CoroutineScope(Dispatchers.Default).launch {
			// delay until shortly before event start; for demo clamp to 5s
			val delayMs = 5000L
			delay(delayMs)
			val note = "Reminder: ${ev.name} is starting at ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ev.startTime))} at ${ev.location}"
			_incidents.update { list -> list + Incident("rem-${eventId}-$ts", note, Severity.Green) }
		}
	}

	override fun getPlaces(type: PlaceType?): List<Place> = emptyList()

	override fun checkInVisitor(visitorId: String, role: UserRole, lat: Double, lng: Double, zone: String) {
		val visitor = ActiveVisitor(
			id = visitorId,
			role = role,
			lat = lat,
			lng = lng,
			zone = zone,
			checkInTime = System.currentTimeMillis(),
			isOnline = true
		)
		_activeVisitors.update { visitors ->
			visitors.filter { it.id != visitorId } + visitor
		}
	}

	override fun checkOutVisitor(visitorId: String) {
		_activeVisitors.update { visitors ->
			visitors.filter { it.id != visitorId }
		}
	}

	override fun updateVisitorLocation(visitorId: String, lat: Double, lng: Double) {
		_activeVisitors.update { visitors ->
			visitors.map { visitor ->
				if (visitor.id == visitorId) {
					visitor.copy(lat = lat, lng = lng)
				} else {
					visitor
				}
			}
		}
	}
}
