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
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(project(":protocol"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.health.services)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.wear.tiles.testing)
}
