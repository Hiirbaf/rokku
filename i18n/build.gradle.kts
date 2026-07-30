import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import yokai.build.tasks.GenerateLocalesConfigTask

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    alias(libs.plugins.moko)
}

kotlin {
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    android {
        namespace = "yokai.i18n"
        minSdk = AndroidConfig.MIN_SDK
        compileSdk = AndroidConfig.COMPILE_SDK
        enableCoreLibraryDesugaring = true
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain {
            resources.srcDir("src/commonMain/resources")
            dependencies {
                api(libs.moko.resources)
                api(libs.moko.resources.compose)
            }
        }
        androidMain {
        }
//        iosMain {
//        }
    }
}

multiplatformResources {
    resourcesPackage.set("yokai.i18n")
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants

        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateLocalesConfigTask>("generate${variantName}LocalesConfig")
        resSource.addGeneratedSourceDirectory(task) { it.outputDir }
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
