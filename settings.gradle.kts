pluginManagement {
    includeBuild("build-logic")
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "selinux-toolbox"

include(":app")
include(":core:core-model")
include(":core:core-data")
include(":core:core-domain")
include(":core:core-ui")
include(":feature:feature-dashboard")
include(":feature:feature-cleanup")
include(":feature:feature-denials")
include(":feature:feature-contexts")
include(":feature:feature-diff")
include(":feature:feature-compile")
include(":feature:feature-explorer")
include(":feature:feature-projects")
include(":feature:feature-conflicts")
include(":feature:feature-validator")
