plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shlomoa.myadhdpal.poc.wear"
}

dependencies {
    implementation(project(":core"))
}
