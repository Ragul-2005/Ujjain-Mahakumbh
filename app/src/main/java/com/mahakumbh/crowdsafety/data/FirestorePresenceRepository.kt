package com.mahakumbh.crowdsafety.data
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Presence repository that uses Firestore `presence` collection for real-time list of online visitors.
 * Safe no-op behavior when Firestore is unavailable so the app doesn't crash in non-Firebase builds.
 */
class FirestorePresenceRepository {
	private val db: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
	private val presenceCollection = db?.collection("presence")

	private val _online = MutableStateFlow<List<ActiveVisitor>>(emptyList())
	val online: StateFlow<List<ActiveVisitor>> = _online

	init {
		if (presenceCollection != null) {
			presenceCollection.addSnapshotListener { snap, err ->
				try {
					if (err != null) {
						android.util.Log.w("FirestorePresence", "listener error", err)
						return@addSnapshotListener
					}
					if (snap != null) {
						val list = snap.documents.mapNotNull { doc ->
							try {
								val data = doc.data ?: return@mapNotNull null
								val roleStr = data["role"] as? String
								val role = try { if (!roleStr.isNullOrBlank()) UserRole.valueOf(roleStr) else UserRole.Visitor } catch (e: Exception) { UserRole.Visitor }
								val lat = (data["lat"] as? Number)?.toDouble() ?: 0.0
								val lng = (data["lng"] as? Number)?.toDouble() ?: 0.0
								val zone = data["zone"] as? String ?: ""
								val checkIn = (data["checkInTime"] as? Number)?.toLong() ?: System.currentTimeMillis()
								val isOnline = (data["isOnline"] as? Boolean) ?: true
								val needsAssist = (data["needsAssist"] as? Boolean) ?: false
								val assisted = (data["assisted"] as? Boolean) ?: false
								val displayName = (data["displayName"] as? String) ?: (doc.id.split(":").getOrNull(0) ?: doc.id)
								ActiveVisitor(
									id = doc.id,
									role = role,
									lat = lat,
									lng = lng,
									zone = zone,
									checkInTime = checkIn,
									displayName = displayName,
									isOnline = isOnline,
									needsAssist = needsAssist,
									assisted = assisted
								)
							} catch (e: Exception) {
								android.util.Log.w("FirestorePresence", "map failed for ${doc.id}", e)
								null
							}
						}
						_online.value = list
					}
				} catch (e: Exception) {
					android.util.Log.e("FirestorePresence", "unexpected listener error", e)
				}
			}
		} else {
			android.util.Log.i("FirestorePresence", "Firestore not available; presence listener not attached")
		}
	}

	fun setOnline(visitor: ActiveVisitor) {
		val col = presenceCollection ?: return
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val doc = col.document(visitor.id)
				val payload = mapOf(
					"role" to visitor.role.name,
					"lat" to visitor.lat,
					"lng" to visitor.lng,
					"zone" to visitor.zone,
					"checkInTime" to visitor.checkInTime,
					"isOnline" to true,
					"needsAssist" to visitor.needsAssist,
					"assisted" to visitor.assisted,
					"displayName" to (visitor.displayName.ifBlank { visitor.id.split(":").getOrNull(0) ?: "" })
				)
				doc.set(payload, SetOptions.merge()).await()
			} catch (e: Exception) {
				android.util.Log.e("FirestorePresence", "failed to setOnline for ${visitor.id}", e)
			}
		}
	}

	fun setOffline(visitorId: String) {
		val col = presenceCollection ?: return
		CoroutineScope(Dispatchers.IO).launch {
			try {
				col.document(visitorId).delete().await()
			} catch (e: Exception) {
				android.util.Log.e("FirestorePresence", "failed to setOffline for $visitorId", e)
			}
		}
	}
}
