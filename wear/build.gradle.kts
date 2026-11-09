plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shlomoa.myadhdpal.poc.wear"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.health.services)
    // Health Services returns ListenableFuture (needed on the compile classpath)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
    // play-services pulls in an old androidx.fragment (see the note in gradle/libs.versions.toml)
    implementation(libs.androidx.fragment)
}
