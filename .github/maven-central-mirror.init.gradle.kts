// CI only (ci.yml): Google's mirror of Maven Central, ahead of Maven Central, for this build and the included Qeetrix
// build. Maven Central answers the shared CI runners' large downloads with HTTP 429; the mirror serves the same files.
// Google's own groups are not on Maven Central, so they skip the mirror and go straight to google().
beforeSettings {
    dependencyResolutionManagement {
        repositories {
            maven("https://maven-central.storage-download.googleapis.com/maven2/") {
                name = "MavenCentralGoogleMirror"
                content {
                    excludeGroupByRegex("androidx\\..*")
                    excludeGroupByRegex("com\\.android\\..*")
                    excludeGroupByRegex("com\\.google\\.android\\..*")
                }
            }
        }
    }
}
