package com.mahakumbh.crowdsafety.di

import com.mahakumbh.crowdsafety.data.CrowdRepository
import com.mahakumbh.crowdsafety.data.FirestorePresenceRepository
import com.mahakumbh.crowdsafety.data.MockRepository
import com.mahakumbh.crowdsafety.data.FirestoreLostRepository

object Locator {
	val repo: CrowdRepository by lazy { MockRepository() }
	// presenceRepo is optional and will noop if Firestore isn't configured
	val presenceRepo: FirestorePresenceRepository by lazy { FirestorePresenceRepository() }
	// Firestore-backed lost-person reports (singleton) - UI and ViewModels should use this when running with Firebase
	val lostRepo: FirestoreLostRepository by lazy { FirestoreLostRepository() }
}
