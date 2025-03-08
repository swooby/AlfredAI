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

rootProject.name = "AlfredAI"
include(":mobile")
include(":openai")
include(":openai-kotlin-client")
project(":openai-kotlin-client").projectDir = file("./openai-openapi-kotlin/lib")
include(":shared")
include(":utils")
include(":wear")
include(":smartfoo-android-lib-core")
project(":smartfoo-android-lib-core").projectDir = file("./smartfoo/android/smartfoo-android-lib-core")
