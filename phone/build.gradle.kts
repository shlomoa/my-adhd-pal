plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shlomoa.myadhdpal.poc.phone"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.play.services.wearable)
    // play-services pulls in an old androidx.fragment (see the note in gradle/libs.versions.toml)
    implementation(libs.androidx.fragment)
}
