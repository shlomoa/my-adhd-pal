plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shlomoa.myadhdpal.poc.phone"
}

dependencies {
    implementation(project(":core"))
}
