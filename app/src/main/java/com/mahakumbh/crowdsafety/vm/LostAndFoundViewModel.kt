package com.mahakumbh.crowdsafety.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahakumbh.crowdsafety.data.LostPersonReport
import com.mahakumbh.crowdsafety.data.ReportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import com.mahakumbh.crowdsafety.di.Locator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LostAndFoundViewModel : ViewModel() {

    private val repo = Locator.repo

    // Use Firestore lost repo when available so reports are real-time across devices
    private val lostRepo = Locator.lostRepo

    val reports: StateFlow<List<LostPersonReport>> = lostRepo.reports
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addReport(
        name: String, 
        age: Int, 
        gender: String, 
        clothingDescription: String, 
        lastKnownLocation: String, 
        photoUrl: String = ""
    ) {
        viewModelScope.launch {
            val newReport = LostPersonReport(
                id = "",
                reporterId = repo.currentUserId.value ?: "unknown",
                name = name,
                age = age,
                gender = gender,
                clothingDescription = clothingDescription,
                lastKnownLocation = lastKnownLocation,
                photoUrl = photoUrl,
                status = ReportStatus.Active,
                timestamp = System.currentTimeMillis()
            )
            // Post to Firestore-backed repo so all devices receive updates
            lostRepo.postReport(newReport)
        }
    }

    fun resolveReport(reportId: String) {
        viewModelScope.launch {
            reports.value.find { it.id == reportId }?.let { report ->
                // mark resolved via Firestore-backed repo
                lostRepo.updateReport(report.copy(status = ReportStatus.Resolved, id = reportId))
            }
        }
    }

    fun removeReport(reportId: String) {
        viewModelScope.launch {
            if (reportId.isBlank()) return@launch
            lostRepo.deleteReport(reportId)
        }
    }
}
