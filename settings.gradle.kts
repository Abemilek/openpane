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
        // Official Mozilla repository containing GeckoView.
        maven("https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "openpane"
include(":app")
