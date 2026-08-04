import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    alias(kotlinx.plugins.serialization)
}

kotlin {
    android {
        namespace = "yokai.core.main"
        minSdk = AndroidConfig.MIN_SDK
        compileSdk = AndroidConfig.COMPILE_SDK
        enableCoreLibraryDesugaring = true
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.i18n)

                // Logging
                api(libs.bundles.logging)

                api(libs.okio)

                api(libs.rxjava)
                api(project.dependencies.enforcedPlatform(kotlinx.coroutines.bom))
                api(kotlinx.coroutines.core)
                api(kotlinx.serialization.json)
                api(kotlinx.serialization.json.okio)

                implementation(libs.jsoup)
            }
        }
        androidMain {
            dependencies {
                // Dependency injection
                api(project.dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
                api(libs.koin.injekt)

                // Network client (OkHttp 5.2+ required for CompressionInterceptor used by extlib 1.6)
                api(libs.okhttp)
                api(libs.okhttp.logging.interceptor)
                api(libs.okhttp.dnsoverhttps)
                // Not used directly by the app; kept on the classpath because some extensions'
                // generated code references okhttp3.brotli.Brotli / okhttp3.zstd.Zstd directly.
                api(libs.okhttp.brotli)
                api(libs.okhttp.zstd)

                api(androidx.preference)
                implementation(androidx.webkit)

                implementation(libs.quickjs.android)

                api(libs.unifile)

                implementation(libs.libarchive)
            }
        }
        // iosMain {
        //     dependencies {
        //     }
        // }
    }
}

tasks {
    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar)
}
