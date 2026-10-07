import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.yeyofone.app"
    compileSdk = 36
    // BUILD-03: pinned so CI and every dev machine resolve the identical build-tools release
    // instead of whatever AGP picks from what's locally installed.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.yeyofone.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Push-relay v2 base URL (see yeyofone-push-relay, a separate project - this repo stays
        // client-only per MAIN-IDEA.md). Must be HTTPS. Empty by default so a normal build never
        // depends on it; set via -PpushRelayUrl=... or gradle.properties. Not a secret: each
        // device authenticates with its own operator-issued credential, imported per account.
        buildConfigField("String", "PUSH_RELAY_URL", "\"${project.findProperty("pushRelayUrl") ?: ""}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }

}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:account-data"))
    implementation(project(":core:model"))
    implementation(project(":core:registration"))
    implementation(project(":core:calling"))
    implementation(project(":core:voip-api"))
    implementation(project(":core:voip-pjsip"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Real org.json for JVM unit tests; the Android stub returns defaults.
    testImplementation("org.json:json:20260814")
    // Push-wake skeleton (see YeyoFoneFirebaseMessagingService). Now backed by a real Firebase
    // project (app/google-services.json, project "yeyofone") - FCM can route messages here.
    // The PBX side still needs to trigger a push on an unanswered INVITE; see HANDOFF.md.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    // Keep Compose's bundled lint checks compatible with the Kotlin 2.2 toolchain.
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
