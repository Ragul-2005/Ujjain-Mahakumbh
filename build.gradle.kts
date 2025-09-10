plugins {
	id("com.android.application") version "8.6.1" apply false
	id("org.jetbrains.kotlin.android") version "1.9.24" apply false
	// Google services plugin for Firebase configuration (applied in modules)
	id("com.google.gms.google-services") version "4.4.0" apply false
}

// Compatibility shim: ensure a `testClasses` task exists in subprojects that might be queried by IDEs/CI
subprojects {
	if (project.tasks.findByName("testClasses") == null) {
		tasks.register("testClasses") {
			group = "verification"
			description = "Compatibility shim: aggregate test classes task for IDE/CI that expect ':<module>:testClasses'"
			// Attempt to depend on common test tasks if present
			val candidates = listOf("testDebugUnitTestClasses", "testReleaseUnitTestClasses", "test")
			candidates.forEach { name ->
				project.tasks.findByName(name)?.let { dependsOn(it) }
			}
		}
	}
}
