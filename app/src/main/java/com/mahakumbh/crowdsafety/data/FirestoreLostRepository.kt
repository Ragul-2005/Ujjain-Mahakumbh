package com.mahakumbh.crowdsafety.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreLostRepository {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("lost_people")
    private val _reports = MutableStateFlow<List<LostPersonReport>>(emptyList())
    val reports: StateFlow<List<LostPersonReport>> = _reports

    // Expose a small last-updated timestamp so callers / logs can verify when data changed
    private val _lastUpdated = MutableStateFlow<Long>(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated
    // Expose project id for runtime diagnostics
    var projectId: String? = null

    init {
        try {
            // Log FirebaseApp diagnostics so we can verify both devices use the same Firebase project
            try {
                val default = try { FirebaseApp.getInstance() } catch (e: Exception) { null }
                if (default != null) {
                    projectId = default.options?.projectId
                    android.util.Log.i("FirestoreLostRepo", "Default app projectId=${projectId}")
                } else {
                    android.util.Log.w("FirestoreLostRepo", "No default FirebaseApp available")
                }
            } catch (e: Exception) {
                android.util.Log.w("FirestoreLostRepo", "FirebaseApp diagnostics failed: ${e.message}")
            }

            // Real-time listener (will update when other devices post)
            col.orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snaps, err ->
                    if (err != null) {
                        android.util.Log.w("FirestoreLostRepo", "snapshot listener error: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snaps == null) {
                        android.util.Log.w("FirestoreLostRepo", "snapshot listener returned null")
                        return@addSnapshotListener
                    }
                    val list = snaps.documents.mapNotNull { doc ->
                        try { doc.toObject(LostPersonReport::class.java)?.copy(id = doc.id) } catch (e: Exception) { null }
                    }
                    _reports.value = list
                    _lastUpdated.value = System.currentTimeMillis()
                    android.util.Log.i("FirestoreLostRepo", "snapshot updated (${list.size} reports) lastUpdated=${_lastUpdated.value}")
                }

            // Also perform a one-time initial fetch so the UI shows existing reports immediately
        CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snaps = col.orderBy("timestamp", Query.Direction.DESCENDING).get().await()
                    val list = snaps.documents.mapNotNull { doc ->
                        try { doc.toObject(LostPersonReport::class.java)?.copy(id = doc.id) } catch (e: Exception) { null }
                    }
            _reports.value = list
            _lastUpdated.value = System.currentTimeMillis()
            android.util.Log.i("FirestoreLostRepo", "initial fetch complete (${list.size} reports) lastUpdated=${_lastUpdated.value}")
                } catch (e: Exception) {
                    android.util.Log.w("FirestoreLostRepo", "initial fetch failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.i("FirestoreLostRepo", "Firestore not available for lost_people: ${e.message}")
        }
    }

    fun postReport(report: LostPersonReport) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val m = report.copy(id = "")
                val ref = col.add(m).await()
                // Insert the newly created report into the local flow immediately so the UI updates
                val saved = m.copy(id = ref.id)
                try {
                    _reports.update { list -> listOf(saved) + list }
                } catch (_: Exception) { /* ignore update errors */ }
                android.util.Log.i("FirestoreLostRepo", "posted lost report: ${ref.id}")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreLostRepo", "failed to post lost report", e)
            }
        }
    }

    fun updateReport(report: LostPersonReport) {
        if (report.id.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                col.document(report.id).set(report).await()
                android.util.Log.i("FirestoreLostRepo", "updated lost report: ${report.id}")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreLostRepo", "failed to update lost report", e)
            }
        }
    }

    fun deleteReport(reportId: String) {
        if (reportId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                col.document(reportId).delete().await()
                android.util.Log.i("FirestoreLostRepo", "deleted lost report: $reportId")
            } catch (e: Exception) {
                android.util.Log.e("FirestoreLostRepo", "failed to delete lost report", e)
            }
        }
    }
}
