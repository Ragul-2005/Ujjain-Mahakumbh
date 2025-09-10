plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("com.google.gms.google-services")
}

android {
	namespace = "com.mahakumbh.crowdsafety"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.mahakumbh.crowdsafety"
		minSdk = 24
		targetSdk = 34
		versionCode = 1
		versionName = "1.0"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	buildFeatures {
		compose = true
	}

	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.14"
	}

	kotlin {
		jvmToolchain(17)
	}
	kotlinOptions {
		jvmTarget = "17"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
	implementation(composeBom)
	androidTestImplementation(composeBom)

	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.activity:activity-compose:1.9.1")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
	implementation("androidx.navigation:navigation-compose:2.7.7")

	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")
	debugImplementation("androidx.compose.ui:ui-tooling")
	debugImplementation("androidx.compose.ui:ui-test-manifest")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
	implementation("com.google.android.material:material:1.12.0")
	implementation("androidx.compose.material:material-icons-extended")

	implementation("com.google.maps.android:maps-compose:4.3.3")
	implementation("com.google.android.gms:play-services-maps:18.2.0")
	implementation("com.google.android.gms:play-services-location:21.3.0")

	// Better QR utilities
	implementation("com.journeyapps:zxing-android-embedded:4.3.0")

	// QR generation for donation receipts
	implementation("com.google.zxing:core:3.5.1")

	// Firebase Firestore and related
	implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
	implementation("com.google.firebase:firebase-analytics-ktx:21.5.0")
	implementation("com.google.firebase:firebase-auth-ktx:22.3.0")
}

// Compatibility task: some CI/IDE setups call ':app:testClasses' which doesn't exist for Android projects.
// Provide a no-op/aggregate task that depends on available test task(s) so older callers don't fail.
if (tasks.findByName("testClasses") == null) {
	tasks.register("testClasses") {
		group = "verification"
		description = "Compatibility shim: aggregate test classes task for IDE/CI that expect ':app:testClasses'"
		// Try to depend on known Android test tasks if they exist
		val candidates = listOf("testDebugUnitTestClasses", "testReleaseUnitTestClasses", "test")
		candidates.forEach { name ->
			project.tasks.findByName(name)?.let { dependsOn(it) }
		}
	}
}
