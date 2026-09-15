pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
include(":core:voip-api")
include(":core:voip-pjsip")
