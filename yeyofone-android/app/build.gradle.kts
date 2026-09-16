import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.yeyofone.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yeyofone.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Push-relay wiring (see yeyofone-push-relay, a separate project - this repo stays
        // client-only per MAIN-IDEA.md). Empty by default so a normal build never depends on
        // it; set via -PpushRelayUrl=... -PpushRelaySecret=... or gradle.properties.
        buildConfigField("String", "PUSH_RELAY_URL", "\"${project.findProperty("pushRelayUrl") ?: ""}\"")
        buildConfigField("String", "PUSH_RELAY_SECRET", "\"${project.findProperty("pushRelaySecret") ?: ""}\"")
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
    // Push-wake skeleton (see YeyoFoneFirebaseMessagingService). Now backed by a real Firebase
    // project (app/google-services.json, project "yeyofone") - FCM can route messages here.
    // The PBX side still needs to trigger a push on an unanswered INVITE; see HANDOFF.md.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
