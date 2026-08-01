import java.util.Locale

plugins {
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.gradle.versions)
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Applied per-subproject (rather than just at the root) so `lintKotlin`/`formatKotlin` actually
// exist to run - the root project has no Kotlin sources of its own for the plugin to lint.
subprojects {
    apply(plugin = "org.jmailen.kotlinter")

    // Generated code (moko-resources' MR.kt, SqlDelight, etc.) lives under build/ and doesn't
    // follow - and shouldn't be forced to follow - the project's Kotlin style rules.
    tasks.withType<org.jmailen.gradle.kotlinter.tasks.LintTask>().configureEach {
        exclude { it.file.path.contains("${File.separatorChar}build${File.separatorChar}") }
    }
    tasks.withType<org.jmailen.gradle.kotlinter.tasks.FormatTask>().configureEach {
        exclude { it.file.path.contains("${File.separatorChar}build${File.separatorChar}") }
    }
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { candidate.version.uppercase(Locale.ROOT).contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(candidate.version)
        isStable.not()
    }
    // optional parameters
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
