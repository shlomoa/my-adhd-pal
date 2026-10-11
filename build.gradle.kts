import com.android.build.api.dsl.ApplicationExtension

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Configuration shared by both apps lives here and in the [versions] block of gradle/libs.versions.toml.
// The watch and phone apps must keep the same applicationId and signing config (plan.md section 2).
subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk = libs.versions.compileSdk.get().toInt()
            defaultConfig {
                applicationId = libs.versions.applicationId.get()
                minSdk = libs.versions.minSdk.get().toInt()
                targetSdk = libs.versions.targetSdk.get().toInt()
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
        }
    }
}

// Pairing check: collect the facts that must match from the final (evaluated) android configuration.
val pairingFacts = objects.mapProperty<String, Map<String, String>>()
subprojects {
    pluginManager.withPlugin("com.android.application") {
        afterEvaluate {
            val android = extensions.getByType<ApplicationExtension>()
            val signing = listOf("debug", "release").joinToString(",") { name ->
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
                    "signing" to signing,
                ),
            )
        }
    }
}

val checkPairing = tasks.register("checkPairing") {
    group = "verification"
    description = "Fails when the watch and phone apps differ in applicationId, SDK levels or signing config."
    val facts = pairingFacts
    inputs.property("facts", facts)
    doLast {
        val apps = facts.get().toSortedMap()
        apps.forEach { (project, values) ->
            logger.lifecycle("$project: " + values.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }
        val problems = listOf("applicationId", "compileSdk", "minSdk", "targetSdk", "signing").mapNotNull { key ->
            val distinct = apps.mapValues { it.value.getValue(key) }
            if (distinct.values.toSet().size > 1) {
                "$key differs between apps: " + distinct.entries.joinToString(", ") { "${it.key}=${it.value}" }
            } else {
                null
            }
        }
        if (apps.size < 2) throw GradleException("Pairing check found ${apps.size} app module(s), expected :wear and :phone")
        if (problems.isNotEmpty()) throw GradleException("Pairing check failed:\n" + problems.joinToString("\n") { " - $it" })
    }
}

tasks.named("check") { dependsOn(checkPairing) }
