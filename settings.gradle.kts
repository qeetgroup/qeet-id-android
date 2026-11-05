import java.util.Properties

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

rootProject.name = "qeet-id-android"

// Qeetrix for Android comes from source, pinned to a release: GitHub Packages needs a token even for a public package,
// and a composite build needs none, on a laptop or in CI. scripts/fetch-qeetrix.sh puts the pinned tag in
// .qeetrix/qeetrix-android; qeetrix.dir (local.properties or -P) points at another checkout instead.
val localProperties =
    Properties().apply {
        val file = file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val qeetrixDir: File =
    file(
        providers.gradleProperty("qeetrix.dir").orNull
            ?: localProperties.getProperty("qeetrix.dir")
            ?: ".qeetrix/qeetrix-android",
    )
check(qeetrixDir.resolve("qeetrix/build.gradle.kts").isFile) {
    "Qeetrix for Android was not found at $qeetrixDir. Run scripts/fetch-qeetrix.sh, or set qeetrix.dir in local.properties."
}
includeBuild(qeetrixDir) {
    name = "qeetrix-android"
    dependencySubstitution {
        substitute(module("com.qeetgroup:qeetrix")).using(project(":qeetrix"))
    }
}

include(":app")
