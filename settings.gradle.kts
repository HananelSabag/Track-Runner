pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Track-Runner"

// Apps: one per device. Both ship under the same applicationId and signing key,
// which is what lets the Wearable Data Layer connect them.
include(":wear")
include(":mobile")

// Shared code. Pure-Kotlin modules stay free of Android so they run in plain JVM tests.
include(":core:model")
include(":core:tracking")
include(":core:diagnostics")
