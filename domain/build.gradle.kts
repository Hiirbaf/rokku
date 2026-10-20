plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    alias(kotlinx.plugins.serialization)
}

kotlin {
    android {
        namespace = "yokai.domain"
        minSdk = AndroidConfig.MIN_SDK
        compileSdk {
            version = release(AndroidConfig.COMPILE_SDK) { minorApiLevel = AndroidConfig.COMPILE_SDK_MINOR }
        }
        enableCoreLibraryDesugaring = true
        // AGP 9's KMP library target dropped testInstrumentationRunner from this flattened
        // block; commonTest below only needs a host (JVM) test run, not on-device instrumentation.
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.source.api)
                api(kotlinx.datetime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.bundles.test)
                implementation(kotlinx.coroutines.test)
            }
        }
        androidMain {
            dependencies {
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.bundles.test.runtime)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar)
}
