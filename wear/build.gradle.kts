plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shlomoa.myadhdpal.wear"
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.play.services.wearable)
    implementation(project(":protocol"))

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
