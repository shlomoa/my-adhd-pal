import com.android.build.api.dsl.ApplicationExtension

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
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
    source.setFrom("wear/src", "phone/src", "protocol/src")
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

// Configuration shared by both apps lives here and in the [versions] block of gradle/libs.versions.toml.
// The watch and phone apps must keep the same applicationId and signing config (plan.md section 2).
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
                versionName = "0.1.0"
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
            buildFeatures {
                compose = true
            }
            // Fail on any lint issue that is not in the module's baseline, warnings included.
            lint {
                abortOnError = true
                warningsAsErrors = true
                baseline = file("lint-baseline.xml")
            }
        }
    }
}

// Pairing check: collect the facts that must match from the final (evaluated) android configuration.
val pairingFacts = objects.mapProperty<String, Map<String, String>>()
subprojects {
    pluginManager.withPlugin("com.android.application") {
        afterEvaluate {
            val android = extensions.getByType<ApplicationExtension>()
            val signing =
                listOf("debug", "release").joinToString(",") { name ->
                    val config = android.buildTypes.getByName(name).signingConfig
                    "$name:" + (config?.let { "${it.name}(${it.storeFile?.name ?: "default"})" } ?: "none")
                }
            pairingFacts.put(
                path,
                mapOf(
                    "applicationId" to android.defaultConfig.applicationId.orEmpty(),
                    "compileSdk" to android.compileSdk.toString(),
                    "minSdk" to android.defaultConfig.minSdk.toString(),
                    "targetSdk" to android.defaultConfig.targetSdk.toString(),
                    "signing" to signing
                )
            )
        }
    }
}

val checkPairing =
    tasks.register("checkPairing") {
        group = "verification"
        description = "Fails when the watch and phone apps differ in applicationId, SDK levels or signing config."
        val facts = pairingFacts
        inputs.property("facts", facts)
        doLast {
            val apps = facts.get().toSortedMap()
            apps.forEach { (project, values) ->
                logger.lifecycle("$project: " + values.entries.joinToString(" ") { "${it.key}=${it.value}" })
            }
            val problems =
                listOf("applicationId", "compileSdk", "minSdk", "targetSdk", "signing").mapNotNull { key ->
                    val distinct = apps.mapValues { it.value.getValue(key) }
                    if (distinct.values.toSet().size > 1) {
                        "$key differs between apps: " + distinct.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    } else {
                        null
                    }
                }
            if (apps.size <
                2
            ) {
                throw GradleException("Pairing check found ${apps.size} app module(s), expected :wear and :phone")
            }
            if (problems.isNotEmpty()) {
                throw GradleException("Pairing check failed:\n" + problems.joinToString("\n") { " - $it" })
            }
        }
    }

tasks.named("check") { dependsOn(checkPairing) }

// Documentation checks. One script (scripts/docs-lint.sh) holds the tools; Gradle and CI only call it.
val lintDocs =
    tasks.register<Exec>("lintDocs") {
        group = "verification"
        description = "Markdown style (markdownlint) and internal links (files and headings)."
        commandLine("sh", "-c", "scripts/docs-lint.sh markdown && scripts/docs-lint.sh links")
    }
tasks.register<Exec>("checkExternalLinks") {
    group = "verification"
    description = "External URLs in the docs. Not part of check: CI runs it as a separate, non-blocking job."
    commandLine("scripts/docs-lint.sh", "external")
}
tasks.named("check") { dependsOn(lintDocs) }

// Release builds contain no debug code. Debug-only code lives in a `debug` package
// (com.shlomoa.myadhdpal.<module>.debug, sources in src/debug/); no such class may be in a release APK.
val checkNoDebugInRelease =
    tasks.register("checkNoDebugInRelease") {
        group = "verification"
        description = "Fails when a release APK contains a class from a `debug` package."
        dependsOn(":wear:assembleRelease", ":phone:assembleRelease")
        val apks =
            fileTree(rootDir) {
                include("wear/build/outputs/apk/release/*.apk", "phone/build/outputs/apk/release/*.apk")
            }
        inputs.files(apks)
        doLast {
            val debugClass = Regex("Lcom/shlomoa/myadhdpal/[A-Za-z0-9_/]*/debug/[A-Za-z0-9_$]+")
            val files = apks.files
            if (files.isEmpty()) throw GradleException("No release APKs found to check")
            val leaks =
                files
                    .flatMap { apk ->
                        java.util.zip.ZipFile(apk).use { zip ->
                            zip
                                .entries()
                                .asSequence()
                                .filter { it.name.endsWith(".dex") }
                                .flatMap { entry ->
                                    val text = String(zip.getInputStream(entry).readBytes(), Charsets.ISO_8859_1)
                                    debugClass
                                        .findAll(
                                            text
                                        ).map { "${apk.name}: ${it.value.removePrefix("L").replace('/', '.')}" }
                                        .toList()
                                }.toList()
                        }
                    }.distinct()
            if (leaks.isNotEmpty()) {
                throw GradleException(
                    "Release builds must contain no debug code, but found:\n" + leaks.joinToString("\n") { " - $it" }
                )
            }
        }
    }
tasks.named("check") { dependsOn(checkNoDebugInRelease) }
