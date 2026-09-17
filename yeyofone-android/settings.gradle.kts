pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "yeyofone-android"

include(":app")
include(":core:model")
include(":core:account-data")
include(":core:registration")
include(":core:calling")
include(":core:voip-api")
include(":core:voip-pjsip")
