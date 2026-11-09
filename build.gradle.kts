import com.android.build.api.dsl.ApplicationExtension

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Formatting and static analysis; both run as part of `check`.
spotless {
    kotlin {
        target("*/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    source.setFrom("wear/src", "phone/src", "core/src")
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

// Configuration shared by both apps lives here and in the [versions] block of gradle/libs.versions.toml.
// The watch and phone apps must keep the same applicationId and signing config: the Wearable Data Layer only links
// apps with the same package name and signature.
subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk =
                libs.versions.compileSdk
                    .get()
                    .toInt()
            defaultConfig {
                applicationId = libs.versions.applicationId.get()
                minSdk =
                    libs.versions.minSdk
                        .get()
                        .toInt()
                targetSdk =
                    libs.versions.targetSdk
                        .get()
                        .toInt()
                versionCode = 1
                versionName = "0.1.0-poc"
            }
            buildTypes {
                release {
                    isMinifyEnabled = false
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
            lint {
                abortOnError = true
            }
        }
    }
}
