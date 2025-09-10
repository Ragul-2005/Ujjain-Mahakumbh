package com.mahakumbh.crowdsafety.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirestoreEventRepository {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    init {
        // Listen for real-time updates
        eventsCollection.orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                try {
                    if (error != null) {
                        // log error and return early
                        android.util.Log.w("FirestoreEventRepo", "listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val eventList = snapshot.documents.mapNotNull { doc ->
                            try { doc.toObject(Event::class.java)?.copy(id = doc.id) } catch (e: Exception) {
                                android.util.Log.w("FirestoreEventRepo", "doc->Event mapping failed: ${doc.id}", e)
                                null
                            }
                        }
                        _events.value = eventList
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FirestoreEventRepo", "unexpected listener error", e)
                }
            }
    }

    fun postEvent(event: Event) {
        // Fire-and-forget add on a background coroutine with error logging
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ref = eventsCollection.add(event).await()
                android.util.Log.i("FirestoreEventRepo", "event posted: ${ref.id}")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreEventRepo", "failed to post event", e)
            }
        }
    }

    fun updateEvent(event: Event) {
        if (event.id.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                eventsCollection.document(event.id).set(event).await()
                android.util.Log.i("FirestoreEventRepo", "event updated: ${event.id}")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreEventRepo", "failed to update event", e)
            }
        }
    }

    fun deleteEvent(eventId: String) {
        if (eventId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                eventsCollection.document(eventId).delete().await()
                android.util.Log.i("FirestoreEventRepo", "event deleted: $eventId")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreEventRepo", "failed to delete event", e)
            }
        }
    }
}
