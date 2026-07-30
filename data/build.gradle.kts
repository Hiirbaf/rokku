plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    alias(kotlinx.plugins.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "yokai.data"
        minSdk = AndroidConfig.MIN_SDK
        compileSdk = AndroidConfig.COMPILE_SDK
        enableCoreLibraryDesugaring = true
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.domain)
                api(libs.bundles.db)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.bundles.db.android)
                implementation(projects.source.api)
            }
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("yokai.data")
            dialect(libs.sqldelight.dialects.sql)
            schemaOutputDirectory.set(project.file("./src/commonMain/sqldelight"))
        }
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar)
}
