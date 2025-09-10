@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mahakumbh.crowdsafety

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.withContext
import com.mahakumbh.crowdsafety.util.NotificationHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.android.gms.maps.model.LatLng
import com.mahakumbh.crowdsafety.data.*
import com.mahakumbh.crowdsafety.di.Locator
import com.mahakumbh.crowdsafety.ui.BottomNavigationBar
import com.mahakumbh.crowdsafety.ui.BottomNavItem
import com.mahakumbh.crowdsafety.ui.*
import com.mahakumbh.crowdsafety.vm.*

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

sealed class VisitorRoute(val route: String, val title: String) {
	data object Home : VisitorRoute("v_home", "Home")
	data object Map : VisitorRoute("v_map", "Map")
	data object Alerts : VisitorRoute("v_alerts", "Alerts")
	data object Profile : VisitorRoute("v_profile", "Profile")
	data object Weather : VisitorRoute("v_weather", "Weather")
	data object LostPeople : VisitorRoute("v_lost", "Lost People")
	data object LostItems : VisitorRoute("v_lost_items", "Lost Items")
	data object ReportLostPerson : VisitorRoute("report_lost_person", "Report Lost Person")
	data object LostPersonDetail : VisitorRoute("lost_person_detail/{reportId}", "Report Details") {
		fun createRoute(reportId: String) = "lost_person_detail/$reportId"
	}
	data object Donation : VisitorRoute("v_donation", "Donation")
	data object Density : VisitorRoute("v_density", "Heat Map")
	data object Reservation : VisitorRoute("v_reservation", "Reservation")
	data object SOS : VisitorRoute("v_sos", "SOS")
	data object ShareLocation : VisitorRoute("v_share", "Share Location")
	data object Events : VisitorRoute("v_events", "Events")
	data object Places : VisitorRoute("v_places", "Places")
}

fun applyLocale(context: Context, language: String) {
	try {
		val res = context.resources
		val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
			res.configuration.locales.get(0)
		} else {
			@Suppress("DEPRECATION")
			res.configuration.locale
		}
		if (currentLocale != null && currentLocale.language == language) {
			// already set, persist and return
			val prefs = context.getSharedPreferences("crowd_prefs", Context.MODE_PRIVATE)
			prefs.edit().putString("app_language", language).apply()
			return
		}

		val locale = Locale(language)
		Locale.setDefault(locale)
		val config = Configuration(res.configuration)
		config.setLocale(locale)
		res.updateConfiguration(config, res.displayMetrics)

		// persist preference
		val prefs = context.getSharedPreferences("crowd_prefs", Context.MODE_PRIVATE)
		prefs.edit().putString("app_language", language).apply()

		// recreate the activity to apply new resources when changed
		if (context is ComponentActivity) {
			context.recreate()
		}
	} catch (e: Exception) {
		// ignore
	}
}

sealed class VolunteerRoute(val route: String, val title: String) {
	data object Dashboard : VolunteerRoute("vol_dashboard", "Dashboard")
	data object Tasks : VolunteerRoute("vol_tasks", "Tasks")
	data object Map : VolunteerRoute("vol_map", "Map")
	data object CheckIn : VolunteerRoute("vol_checkin", "Check-in")
	data object ActiveVisitors : VolunteerRoute("vol_active_visitors", "Active Visitors")
	data object LiveStream : VolunteerRoute("vol_live_stream/{zone}", "Live Stream") {
		// encode spaces as percent-encoding so nav args and query params behave consistently
		fun createRoute(zone: String) = "vol_live_stream/${java.net.URLEncoder.encode(zone, "UTF-8").replace("+", "%20") }"
	}
	data object LostPeople : VolunteerRoute("vol_lost", "Lost People")
	data object LostItemDetail : VolunteerRoute("vol_lost_item/{taskId}", "Lost Item Detail") {
		fun createRoute(taskId: String) = "vol_lost_item/$taskId"
	}

	data object AlertDetail : VolunteerRoute("vol_alert/{taskId}", "Alert Detail") {
		fun createRoute(taskId: String) = "vol_alert/$taskId"
	}
}

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent { App() }
	}
}

sealed class Screen(val route: String, val title: String) {
	data object Dashboard : Screen("dashboard", "Dashboard")
	data object Playbooks : Screen("playbooks", "Playbooks")
	data object Tasks : Screen("tasks", "Tasks")
	data object Incidents : Screen("incidents", "Incidents")
	data object Analytics : Screen("analytics", "Analytics")
	data object Settings : Screen("settings", "Settings")
	data object Map : Screen("map", "Live Map")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
	// Apply saved language selection at startup (one-shot)
	val ctx = LocalContext.current
	LaunchedEffect(Unit) {
		try {
			val prefs = ctx.getSharedPreferences("crowd_prefs", Context.MODE_PRIVATE)
			val lang = prefs.getString("app_language", null)
			if (!lang.isNullOrBlank()) applyLocale(ctx, lang)
		} catch (_: Exception) { }
	}
	val piBase = "http://172.19.81.89:5000" // Base URL for the video feed
	val navController = rememberNavController()
	val currentBackStackEntry by navController.currentBackStackEntryAsState()
	val currentRoute = currentBackStackEntry?.destination?.route ?: Screen.Dashboard.route

	val repo = Locator.repo
	val currentUser by repo.currentUserRole.collectAsState()

	var useDark by remember { mutableStateOf(true) }
    

	val visitorItems = listOf(
		BottomNavItem(VisitorRoute.Home.route, "Home", Icons.Filled.Home),
		BottomNavItem(VisitorRoute.Map.route, "Map", Icons.Filled.Map),
		BottomNavItem(VisitorRoute.Alerts.route, "Alerts", Icons.Filled.Warning),
		BottomNavItem(VisitorRoute.Profile.route, "Profile", Icons.Filled.Person)
	)

	val volunteerItems = listOf(
		BottomNavItem(VolunteerRoute.Dashboard.route, "Dashboard", Icons.Filled.Dashboard),
		BottomNavItem(VolunteerRoute.Tasks.route, "Tasks", Icons.Filled.Task),
		BottomNavItem(VolunteerRoute.Map.route, "Map", Icons.Filled.Map),
		BottomNavItem(VolunteerRoute.CheckIn.route, "Check-in", Icons.Filled.LocationOn)
	)

	com.mahakumbh.crowdsafety.ui.CrowdSafetyTheme(darkTheme = useDark) {
		// observer that posts local notifications for new incidents emitted by the repo
		IncidentNotifier()

		// Optional: listen for SOS documents in Firestore and show local notifications to volunteers.
		// This is guarded by FirebaseApp initialization so it will no-op if Firebase isn't configured.
		val ctx = LocalContext.current
		val lifecycleOwner = LocalLifecycleOwner.current
		DisposableEffect(Unit) {
			var registration: com.google.firebase.firestore.ListenerRegistration? = null
			try {
				val app = FirebaseApp.initializeApp(ctx)
				if (app != null) {
					val db = FirebaseFirestore.getInstance()
					registration = db.collection("sos").whereEqualTo("handled", false)
						.addSnapshotListener { snaps, err ->
							if (err != null || snaps == null) return@addSnapshotListener
							snaps.documentChanges.forEach { dc ->
								if (dc.type == DocumentChange.Type.ADDED) {
									val sourceId = dc.document.getString("sourceId") ?: ""
									val msg = dc.document.getString("message") ?: "SOS reported"
									val curId = Locator.repo.currentUserId.value ?: ""
									val role = Locator.repo.currentUserRole.value
									if (role == com.mahakumbh.crowdsafety.data.UserRole.Volunteer && sourceId != curId) {
										NotificationHelper.showNotification(ctx, dc.document.id.hashCode(), "SOS Alert", msg)
									}
								}
							}
						}
				}
			} catch (e: Exception) {
				// ignore: Firebase not configured or runtime error; listener not active
			}
			onDispose {
				registration?.remove()
			}
		}
		if (currentUser == null) {
			LoginScreen(useDark = useDark, onToggleTheme = { useDark = !useDark })
		} else {
			Scaffold(
				topBar = {
					CenterAlignedTopAppBar(
						title = { 
							Text(
								text = stringResource(id = getTitleForRoute(currentRoute, currentUser)),
								fontWeight = FontWeight.Bold
							)
						},
						navigationIcon = {
							// Language selector placed at the start (left) of the app bar
							var langMenuExpanded by remember { mutableStateOf(false) }
							val context = LocalContext.current
							IconButton(onClick = { langMenuExpanded = true }) {
								Icon(Icons.Filled.Language, contentDescription = stringResource(id = R.string.select_language))
							}
							DropdownMenu(expanded = langMenuExpanded, onDismissRequest = { langMenuExpanded = false }) {
								DropdownMenuItem(text = { Text(stringResource(id = R.string.lang_english_label)) }, onClick = {
									applyLocale(context, "en")
									langMenuExpanded = false
								})
								DropdownMenuItem(text = { Text(stringResource(id = R.string.lang_hindi_label)) }, onClick = {
									applyLocale(context, "hi")
									langMenuExpanded = false
								})
								DropdownMenuItem(text = { Text(stringResource(id = R.string.lang_bengali_label)) }, onClick = {
									applyLocale(context, "bn")
									langMenuExpanded = false
								})
								DropdownMenuItem(text = { Text(stringResource(id = R.string.lang_telugu_label)) }, onClick = {
									applyLocale(context, "te")
									langMenuExpanded = false
								})
							}
						},
						actions = {
							TextButton(onClick = { useDark = !useDark }) { 
								Text(if (useDark) stringResource(id = R.string.light_mode) else stringResource(id = R.string.dark_mode)) 
							}
							TextButton(onClick = {
								// Clear presence in Firestore (best-effort) before logging out
								val uid = repo.currentUserId.value
								if (!uid.isNullOrBlank()) Locator.presenceRepo.setOffline(uid)
								repo.logout()
							}) {
								Text(stringResource(id = R.string.logout)) 
							}
						}
					)
				},
				bottomBar = { 
					BottomNavigationBar(
						navController = navController, 
						currentRoute = currentRoute, 
						items = if (currentUser == UserRole.Volunteer) volunteerItems else visitorItems
					) 
				}
			) { padding ->
				when (currentUser) {
					UserRole.Visitor -> VisitorNavGraph(navController = navController)
					UserRole.Volunteer -> VolunteerNavGraph(navController = navController)
					else -> LoginScreen(useDark = useDark, onToggleTheme = { useDark = !useDark })
				}
			}
		}
	}
}


@Composable
fun IncidentNotifier() {
	val context = LocalContext.current
	val repo = Locator.repo
	val incidents by repo.incidents.collectAsState(initial = emptyList())
	val seen = remember { mutableStateListOf<String>() }
	LaunchedEffect(incidents) {
		incidents.forEach { inc ->
			if (!seen.contains(inc.id)) {
				seen.add(inc.id)
				// If the incident message is targeted like: [to:userId] message
				val targetedPrefix = "^\\[to:([^\\]]+)\\]\\s*(.*)"
				val regex = Regex(targetedPrefix)
				val currentUserId = repo.currentUserId.value ?: ""
				val match = regex.find(inc.message)
				if (match != null) {
					val target = match.groupValues[1]
					val msg = match.groupValues[2]
					if (target == currentUserId) {
						val title = when (inc.severity) {
							Severity.Red -> "Emergency"
							Severity.Yellow -> "Alert"
							Severity.Green -> "Info"
						}
						NotificationHelper.showNotification(context, inc.id.hashCode(), title, msg)
					}
				} else {
					val title = when (inc.severity) {
						Severity.Red -> "Emergency"
						Severity.Yellow -> "Alert"
						Severity.Green -> "Info"
					}
					NotificationHelper.showNotification(context, inc.id.hashCode(), title, inc.message)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(useDark: Boolean, onToggleTheme: () -> Unit) {
	val repo = Locator.repo
	var selected by remember { mutableStateOf(UserRole.Visitor) }
	var name by remember { mutableStateOf("") }
	var id by remember { mutableStateOf("") }
	
	Scaffold(
		topBar = {
			TopAppBar(
				title = { 
					Text(
						text = "Crowd Safety - Sign In",
						fontWeight = FontWeight.Bold
					)
				},
				actions = {
					IconButton(onClick = onToggleTheme) {
						if (useDark) Icon(Icons.Filled.LightMode, contentDescription = "Light mode")
						else Icon(Icons.Filled.DarkMode, contentDescription = "Dark mode")
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			// App Logo/Header
			Card(
				modifier = Modifier
					.size(120.dp)
					.padding(bottom = 32.dp),
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.primaryContainer
				),
				elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
			) {
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center
				) {
					Icon(
						imageVector = Icons.Filled.Security,
						contentDescription = "Crowd Safety",
						modifier = Modifier.size(64.dp),
						tint = MaterialTheme.colorScheme.primary
					)
				}
			}
			
			Text(
				text = "Crowd Safety",
				style = MaterialTheme.typography.headlineLarge,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary
			)
        
			Text(
				text = "Emergency Management System",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			
			Spacer(modifier = Modifier.height(48.dp))
			
			// Role Selection
			Text(
				text = "Select Your Role",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface
			)
			
			Spacer(modifier = Modifier.height(16.dp))
			
			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				if (selected == UserRole.Visitor) {
					FilledTonalButton(
						onClick = { selected = UserRole.Visitor },
						shape = RoundedCornerShape(12.dp)
					) { 
						Text("Visitor") 
					}
					OutlinedButton(
						onClick = { selected = UserRole.Volunteer },
						shape = RoundedCornerShape(12.dp)
					) { 
						Text("Volunteer") 
					}
				} else {
					OutlinedButton(
						onClick = { selected = UserRole.Visitor },
						shape = RoundedCornerShape(12.dp)
					) { 
						Text("Visitor") 
					}
					FilledTonalButton(
						onClick = { selected = UserRole.Volunteer },
						shape = RoundedCornerShape(12.dp)
					) { 
						Text("Volunteer") 
					}
				}
			}
			
			Spacer(modifier = Modifier.height(32.dp))
			
			// Login Form
			OutlinedTextField(
				value = name,
				onValueChange = { name = it },
				label = { Text("${selected.name} Name") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(12.dp)
			)
			
			Spacer(modifier = Modifier.height(16.dp))
    
			
			OutlinedTextField(
				value = id,
				onValueChange = { id = it },
				label = { Text("${selected.name} ID") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(12.dp)
			)
			
			Spacer(modifier = Modifier.height(32.dp))
			
			Button(
				onClick = { 
					if (name.isNotBlank() && id.isNotBlank()) {
						repo.login(selected, "$name:$id")
						// If the user is a visitor, publish presence
						if (selected == UserRole.Visitor) {
							val visitor = ActiveVisitor(id = "$name:$id", role = UserRole.Visitor, lat = 0.0, lng = 0.0, zone = "", checkInTime = System.currentTimeMillis(), isOnline = true, displayName = name)
							Locator.presenceRepo.setOnline(visitor)
						}
					}
				},
				modifier = Modifier
					.fillMaxWidth()
					.height(56.dp),
				shape = RoundedCornerShape(16.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = MaterialTheme.colorScheme.primary
				)
			) { 
				Text(
					"Continue",
					fontWeight = FontWeight.Bold,
					style = MaterialTheme.typography.titleMedium
				) 
			}
		}
	}
}

@Composable
fun VisitorNavGraph(navController: NavHostController) {
	NavHost(navController = navController, startDestination = VisitorRoute.Home.route) {
		composable(VisitorRoute.Home.route) { VisitorHomeScreen(onNavigate = { route -> navController.navigate(route) }) }
		composable(VisitorRoute.LostItems.route) { com.mahakumbh.crowdsafety.ui.VisitorLostItemsScreen(navController = navController) }
		composable(VisitorRoute.Map.route) { VisitorMapScreen() }
		composable(VisitorRoute.Alerts.route) { VisitorAlertsScreen() }
		composable(VisitorRoute.Profile.route) { VisitorProfileScreen() }
		composable(VisitorRoute.Weather.route) { WeatherScreen(onBack = { navController.popBackStack() }) }
		composable(VisitorRoute.LostPeople.route) { 
			LostPersonListScreen(
				onBack = { navController.popBackStack() }, 
				onReportClick = { reportId ->
					navController.navigate(VisitorRoute.LostPersonDetail.createRoute(reportId))
				},
				onNavigateToReport = { navController.navigate(VisitorRoute.ReportLostPerson.route) }
			) 
		}
		composable(VisitorRoute.ReportLostPerson.route) { ReportLostPersonScreen(onBack = { navController.popBackStack() }, onSubmit = { navController.popBackStack() }) }
		composable(VisitorRoute.LostPersonDetail.route) {
			val reportId = it.arguments?.getString("reportId") ?: ""
			LostPersonDetailScreen(
				reportId = reportId,
				onBack = { navController.popBackStack() }
			)
		}
	composable(VisitorRoute.Donation.route) { com.mahakumbh.crowdsafety.ui.DonationScreen() }
	composable(VisitorRoute.Density.route) { com.mahakumbh.crowdsafety.ui.DensityHeatMapScreen() }
	composable(VisitorRoute.Reservation.route) { com.mahakumbh.crowdsafety.ui.SpecialReservationScreen() }
		composable(VisitorRoute.SOS.route) {
			val repo = Locator.repo
			val ctx = LocalContext.current
			var sending by remember { mutableStateOf(false) }
			com.mahakumbh.crowdsafety.ui.EmergencySosScreen(onSosSend = { type ->
				val userId = repo.currentUserId.value ?: "anonymous"
				val role = repo.currentUserRole.value ?: com.mahakumbh.crowdsafety.data.UserRole.Visitor
				sending = true
				repo.logSos(type, userId, role, 25.419518, 81.889800)
				android.widget.Toast.makeText(ctx, "SOS sent", android.widget.Toast.LENGTH_SHORT).show()
			}, sosState = com.mahakumbh.crowdsafety.ui.SosUiState(sending = sending))
		}
		composable(VisitorRoute.ShareLocation.route) { VisitorShareLocationScreen() }
	composable(VisitorRoute.Events.route) { com.mahakumbh.crowdsafety.ui.EventScheduleScreen() }
		composable(VisitorRoute.Places.route) { VisitorPlacesScreen() }
	}
}

@Composable
fun VolunteerNavGraph(navController: NavHostController) {
	NavHost(navController = navController, startDestination = VolunteerRoute.Dashboard.route) {
	composable(VolunteerRoute.Dashboard.route) { VolunteerDashboard(navController) }
	composable(VolunteerRoute.Tasks.route) { VolunteerTaskDashboard(navController) }
		composable(VolunteerRoute.Map.route) { VolunteerCrowdMapView() }
		composable(VolunteerRoute.CheckIn.route) { VolunteerCheckInScreen() }
		composable(VolunteerRoute.ActiveVisitors.route) { 
			ActiveVisitorsScreen(onBack = { navController.popBackStack() })
		}
			// Dedicated lost items list for volunteers
			composable("vol_lost_items") {
				com.mahakumbh.crowdsafety.ui.VolunteerLostItemsListScreen(navController = navController)
			}
		composable("vol_live_stream/{zone}") { backStack ->
			val zoneArg = backStack.arguments?.getString("zone") ?: "Zone A"
			LiveStreamScreen(onBack = { navController.popBackStack() }, zone = zoneArg)
		}
		composable("vol_events") {
			EventManagementScreen(onBack = { navController.popBackStack() })
		}
			composable(VolunteerRoute.LostPeople.route) {
				LostManagementScreen(onBack = { navController.popBackStack() })
			}
		// Alert detail route
		composable(VolunteerRoute.AlertDetail.route) { backStackEntry ->
			val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
			com.mahakumbh.crowdsafety.ui.VolunteerAlertScreen(navController = navController, taskId = taskId)
		}
		// Lost item detail route for volunteers
		composable(VolunteerRoute.LostItemDetail.route) { backStackEntry ->
			val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
			com.mahakumbh.crowdsafety.ui.VolunteerLostItemScreen(navController = navController, taskId = taskId)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveVisitorsScreen(onBack: () -> Unit) {
	val repo = Locator.repo
	val presenceList by Locator.presenceRepo.online.collectAsState(initial = emptyList())
	val mockVisitors by repo.activeVisitors.collectAsState(initial = emptyList())
	val activeVisitors = if (presenceList.isNotEmpty()) presenceList else mockVisitors

	Scaffold(topBar = {
		TopAppBar(title = { Text("Active Visitors") }, navigationIcon = {
			IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
		})
	}) { padding ->
		LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			item {
				Text("Active Visitors: ${activeVisitors.size}", style = MaterialTheme.typography.headlineSmall)
			}
			items(activeVisitors) { v ->
					var showEdit by remember { mutableStateOf(false) }
					var showConfirmCheckout by remember { mutableStateOf(false) }
					Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
						Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
							Icon(Icons.Filled.Person, contentDescription = null)
							Spacer(Modifier.width(12.dp))
							Column(modifier = Modifier.weight(1f)) {
								val label = if (v.displayName.isNotBlank()) v.displayName else v.id
								Text(label, fontWeight = FontWeight.SemiBold)
								Text(v.zone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
								val ct = android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(java.util.Date(v.checkInTime))
								Text("Checked in: $ct", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
							Column(horizontalAlignment = Alignment.End) {
								Text(if (v.isOnline) "Online" else "Offline", style = MaterialTheme.typography.bodySmall)
								Row {
									// Edit button (volunteers can change display name / zone)
									IconButton(onClick = { showEdit = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit visitor") }
									// Checkout / remove button
									IconButton(onClick = { showConfirmCheckout = true }) { Icon(Icons.Filled.ExitToApp, contentDescription = "Check out visitor") }
								}
							}
						}
					}

					if (showConfirmCheckout) {
						AlertDialog(
							onDismissRequest = { showConfirmCheckout = false },
							title = { Text("Check out visitor") },
							text = { Text("Remove this visitor from the active list? This will set them offline.") },
							confirmButton = {
								TextButton(onClick = {
									showConfirmCheckout = false
									Locator.presenceRepo.setOffline(v.id)
								}) { Text("Remove") }
							},
							dismissButton = {
								TextButton(onClick = { showConfirmCheckout = false }) { Text("Cancel") }
							}
						)
					}

					if (showEdit) {
						// small edit dialog to change displayName and zone
						var newName by remember { mutableStateOf(v.displayName) }
						var newZone by remember { mutableStateOf(v.zone) }
						AlertDialog(
							onDismissRequest = { showEdit = false },
							title = { Text("Edit visitor") },
							text = {
								Column {
									OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
									Spacer(Modifier.height(8.dp))
									OutlinedTextField(value = newZone, onValueChange = { newZone = it }, label = { Text("Zone") })
								}
							},
							confirmButton = {
								TextButton(onClick = {
									showEdit = false
									// update presence doc with new name/zone
									Locator.presenceRepo.setOnline(v.copy(displayName = newName, zone = newZone))
								}) { Text("Save") }
							},
							dismissButton = {
								TextButton(onClick = { showEdit = false }) { Text("Cancel") }
							}
						)
					}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
// Simple byte-array indexOf helper (file-private)
private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
	if (pattern.isEmpty() || data.size < pattern.size) return -1
	outer@ for (i in 0..data.size - pattern.size) {
		for (j in pattern.indices) {
			if (data[i + j] != pattern[j]) continue@outer
		}
		return i
	}
	return -1
}

@Composable
fun MjpegStream(streamUrl: String, personCount: Int = 0, modifier: Modifier = Modifier) {
	val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
	val running = remember { mutableStateOf(true) }
	val connRef = remember { mutableStateOf<java.net.HttpURLConnection?>(null) }

	LaunchedEffect(streamUrl) {
		running.value = true
		withContext(kotlinx.coroutines.Dispatchers.IO) {
			var conn: java.net.HttpURLConnection? = null
			var input: java.io.InputStream? = null
			val outStream = java.io.ByteArrayOutputStream()
			try {
				val url = java.net.URL(streamUrl)
				conn = url.openConnection() as java.net.HttpURLConnection
				conn.connectTimeout = 5000
				conn.readTimeout = 0 // block until closed
				conn.doInput = true
				conn.connect()
				connRef.value = conn
				input = conn.inputStream
				val tmp = ByteArray(4096)
				var len: Int
				while (running.value) {
					val read = input.read(tmp)
					if (read <= 0) break
					len = read
					outStream.write(tmp, 0, len)
					val data = outStream.toByteArray()
					val start = indexOf(data, byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
					val end = indexOf(data, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
					if (start >= 0 && end > start) {
						val jpg = data.copyOfRange(start, end + 2)
						val bm = android.graphics.BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
						if (bm != null) {
							withContext(kotlinx.coroutines.Dispatchers.Main) {
								bitmapState.value = bm
							}
						}
						// keep remainder
						val rem = if (end + 2 < data.size) data.copyOfRange(end + 2, data.size) else ByteArray(0)
						outStream.reset()
						if (rem.isNotEmpty()) outStream.write(rem)
					}
				}
			} catch (e: Exception) {
				// ignore — will show spinner and keep trying on next LaunchedEffect
			} finally {
				try { input?.close() } catch (_: Exception) {}
				try { conn?.disconnect() } catch (_: Exception) {}
				connRef.value = null
			}
		}
	}

	DisposableEffect(streamUrl) {
		onDispose {
			running.value = false
			try { connRef.value?.disconnect() } catch (_: Exception) {}
			connRef.value = null
		}
	}

	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		bitmapState.value?.let { bm ->
			Image(bitmap = bm.asImageBitmap(), contentDescription = "MJPEG frame", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
		} ?: Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))

		Box(modifier = Modifier
			.align(Alignment.TopStart)
			.padding(8.dp)
			.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
			.padding(horizontal = 10.dp, vertical = 6.dp)
		) {
			Text(text = "Persons: $personCount", color = MaterialTheme.colorScheme.onPrimary)
		}

		if (bitmapState.value == null) {
			CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
		}
	}
}

@Composable
fun LiveStreamScreen(onBack: () -> Unit, zone: String, piBase: String = "http://172.19.81.89:5000") {
	// Decode the incoming zone (nav argument may already be URL-encoded)
	val decodedZone = try { java.net.URLDecoder.decode(zone, "UTF-8") } catch (e: Exception) { zone }

	// Local selected zone state lets the user switch zones by tapping the top cards
	val selectedZone = remember { mutableStateOf(decodedZone) }

	val countState = remember { mutableStateOf(0) }
	val zoneCounts = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
	val context = LocalContext.current
	// Load persisted piBase override if present
	val prefs = context.getSharedPreferences("crowd_prefs", android.content.Context.MODE_PRIVATE)
	var storedPi by remember { mutableStateOf(prefs.getString("pi_base", null)) }
	var showEditPi by remember { mutableStateOf(false) }

	// Poll the Pi /count endpoint and extract the selected zone
	LaunchedEffect(selectedZone.value) {
		// Poll the Pi /count endpoint and build a map of counts for the top cards
		val zonesList = listOf("Zone A", "Zone B", "Zone C")
		while (true) {
			try {
				withContext(kotlinx.coroutines.Dispatchers.IO) {
					val url = java.net.URL("$piBase/count")
					val conn = url.openConnection() as java.net.HttpURLConnection
					conn.connectTimeout = 2000
					conn.readTimeout = 2000
					conn.requestMethod = "GET"
					conn.doInput = true
					conn.connect()
					val text = conn.inputStream.bufferedReader().use { it.readText() }
					try {
						val json = org.json.JSONObject(text)

						fun parseAny(a: Any?): Int {
							return when (a) {
								is org.json.JSONObject -> a.optInt("display", a.optInt("raw_last", 0))
								is Number -> a.toInt()
								is String -> a.toIntOrNull() ?: 0
								else -> 0
							}
						}

						val newMap = mutableMapOf<String, Int>()
						zonesList.forEach { z ->
							var v: Any? = json.opt(z)
							if (v == null) v = json.opt(z.replace(" ", ""))
							newMap[z] = parseAny(v)
						}
						val selectedCount = newMap[selectedZone.value] ?: 0
						withContext(kotlinx.coroutines.Dispatchers.Main) {
							zoneCounts.value = newMap
							countState.value = selectedCount
						}
					} catch (e: Exception) {
						// fallback: maybe the endpoint returned a raw number
						try {
							val raw = text.trim().toIntOrNull()
							withContext(kotlinx.coroutines.Dispatchers.Main) {
								val single = raw ?: 0
								countState.value = single
								zoneCounts.value = mapOf(decodedZone to single)
							}
						} catch (_: Exception) { }
					}
					conn.disconnect()
				}
			} catch (_: Exception) {
			}
			kotlinx.coroutines.delay(800)
		}
	}

	// Resolve effective piBase: prefer stored override, then passed param, then common candidate(s)
	val effectivePi = storedPi ?: piBase
	val streamUrl = "$effectivePi/video_feed?zone=${java.net.URLEncoder.encode(selectedZone.value, "UTF-8").replace("+", "%20") }"

	Scaffold(topBar = {
		TopAppBar(title = { Text(decodedZone) }, navigationIcon = {
			IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
		}, actions = {
			IconButton(onClick = { showEditPi = true }) { Icon(Icons.Filled.Settings, contentDescription = "Set Pi URL") }
		})
	}) { padding ->
		Column(modifier = Modifier
			.padding(padding)
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface)
			.padding(12.dp)) {

			// Top: small zone visuals with live counts
			val zones = listOf("Zone A", "Zone B", "Zone C")
			Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				zones.forEach { z ->
					val isSelected = z == selectedZone.value
					val ct = zoneCounts.value[z] ?: 0
					Card(
						modifier = Modifier.weight(1f).clickable { selectedZone.value = z },
						shape = RoundedCornerShape(10.dp),
						colors = CardDefaults.cardColors(
							containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
						)
					) {
						Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
							Text(text = z, style = MaterialTheme.typography.bodyMedium)
							Spacer(modifier = Modifier.height(6.dp))
							Text(text = "$ct", style = MaterialTheme.typography.titleSmall)
						}
					}
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			// Video area: reduced height so it fits on smaller screens
			Card(modifier = Modifier
				.fillMaxWidth()
				.height(300.dp),
				shape = RoundedCornerShape(8.dp)) {
				// Use themed background and a snapshot-polling composable for reliable in-app streams
				Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
					SnapshotLiveStream(zone = selectedZone.value, piBase = effectivePi, personCount = countState.value, modifier = Modifier.fillMaxSize())
				}
			}



		// Dialog to edit Pi base URL
		if (showEditPi) {
			var inputUrl by remember { mutableStateOf(storedPi ?: effectivePi) }
			AlertDialog(
				onDismissRequest = { showEditPi = false },
				title = { Text("Set stream base URL") },
				text = {
					Column {
						OutlinedTextField(value = inputUrl, onValueChange = { inputUrl = it }, label = { Text("Pi base URL (e.g. http://10.68.59.89:5000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
						Spacer(modifier = Modifier.height(6.dp))
						Text("If the stream loads in browser at a different IP, paste it here and press Save.", style = MaterialTheme.typography.bodySmall)
					}
				},
				confirmButton = {
					TextButton(onClick = {
						storedPi = inputUrl
						prefs.edit().putString("pi_base", inputUrl).apply()
						showEditPi = false
					}) { Text("Save") }
				},
				dismissButton = { TextButton(onClick = { showEditPi = false }) { Text("Cancel") } }
			)
		}
			Spacer(modifier = Modifier.height(12.dp))

			// Person count box with pink background labeled as requested
			Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0E6)), modifier = Modifier.fillMaxWidth()) {
				Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
					Text(text = "Person Count - ", style = MaterialTheme.typography.bodyLarge, color = Color.Black)
					Text(text = "${countState.value}", style = MaterialTheme.typography.headlineSmall, color = Color.Black)
				}
			}

			Spacer(modifier = Modifier.height(8.dp))
			// The rest of the screen will show the theme background (no white area)
			Box(modifier = Modifier.fillMaxSize()) { /* placeholder for future controls */ }
		}
	}
}

@Composable
fun SnapshotLiveStream(zone: String, piBase: String, personCount: Int = 0, modifier: Modifier = Modifier) {
	val decodedZone = try { java.net.URLDecoder.decode(zone, "UTF-8") } catch (e: Exception) { zone }
	val encodedZone = java.net.URLEncoder.encode(decodedZone, "UTF-8").replace("+", "%20")
	val snapshotUrl = "$piBase/snapshot?zone=$encodedZone"

	val bitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }

	LaunchedEffect(key1 = snapshotUrl) {
		while (true) {
			try {
				withContext(kotlinx.coroutines.Dispatchers.IO) {
					try {
						val url = java.net.URL(snapshotUrl)
						val conn = url.openConnection() as java.net.HttpURLConnection
						conn.connectTimeout = 2000
						conn.readTimeout = 2000
						conn.requestMethod = "GET"
						conn.doInput = true
						conn.connect()
						val bytes = conn.inputStream.readBytes()
						conn.disconnect()
						val bm = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
						withContext(kotlinx.coroutines.Dispatchers.Main) {
							bitmapState.value = bm
						}
					} catch (e: Exception) {
						// ignore transient errors, keep previous frame
					}
				}
			} catch (_: Exception) { }
			kotlinx.coroutines.delay(400) // ~2.5 FPS by default
		}
	}

	Box(modifier = modifier, contentAlignment = Alignment.Center) {
		bitmapState.value?.let { bm ->
			Image(bitmap = bm.asImageBitmap(), contentDescription = "Live snapshot", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
		} ?: Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))

		// Top-left person count badge
		Box(modifier = Modifier
			.align(Alignment.TopStart)
			.padding(8.dp)
			.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
			.padding(horizontal = 10.dp, vertical = 6.dp)
		) {
			Text(text = "Persons: $personCount", color = MaterialTheme.colorScheme.onPrimary)
		}

		// Loading spinner while no frame available
		if (bitmapState.value == null) {
			CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
		}
	}
}

@Composable
fun VisitorShareLocationScreen() {
	// Minimal placeholder to satisfy references from the nav graph.
	// Real implementation can be provided in a dedicated file later.
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		contentAlignment = Alignment.Center
	) {
		Text(text = "Share Location screen is not available yet.")
	}
}

@Composable
fun VisitorPlacesScreen() {
	// Minimal placeholder to satisfy references from the nav graph.
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		contentAlignment = Alignment.Center
	) {
		Text(text = "Places screen is not available yet.")
	}
}

private fun getTitleForRoute(route: String, userRole: UserRole?): Int = when (userRole) {
	UserRole.Visitor -> when (route) {
		VisitorRoute.Home.route -> R.string.title_home
		VisitorRoute.Map.route -> R.string.title_map
		VisitorRoute.Alerts.route -> R.string.title_alerts
		VisitorRoute.Profile.route -> R.string.title_profile
		else -> R.string.app_name
	}
	UserRole.Volunteer -> when (route) {
		VolunteerRoute.Dashboard.route -> R.string.title_dashboard
		VolunteerRoute.Tasks.route -> R.string.title_tasks
		VolunteerRoute.Map.route -> R.string.title_crowd_map
		VolunteerRoute.CheckIn.route -> R.string.title_checkin
		else -> R.string.title_volunteer_portal
	}
	else -> R.string.app_name
}


