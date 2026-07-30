import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    alias(kotlinx.plugins.serialization)
}

kotlin {
    android {
        namespace = "eu.kanade.tachiyomi.source"
        minSdk = AndroidConfig.MIN_SDK
        compileSdk = AndroidConfig.COMPILE_SDK
        enableCoreLibraryDesugaring = true
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        optimization {
            consumerKeepRules.files.add(project.file("consumer-proguard.pro"))
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(project.dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
                api(libs.koin.injekt)
                api(libs.rxjava)
                api(libs.jsoup)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.main)
                api(androidx.preference)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }
}
tasks {
    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar)
}
