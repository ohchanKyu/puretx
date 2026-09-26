rootProject.name = "puretx"

// Uploads the existing Maven publications to the Central Portal. Nothing here builds or signs
// anything; that stays with maven-publish and signing in build.gradle.kts. The credentials are
// a Central Portal user token, present only on the release workflow.
plugins {
    id("com.gradleup.nmcp.settings") version "1.6.2"
}

include(
    "puretx-core",
    "puretx-spring-boot-starter",
    "puretx-sample",
)

nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME") ?: ""
        password = System.getenv("CENTRAL_PASSWORD") ?: ""
        publishingType = "AUTOMATIC"
        publicationName = "puretx"
    }
}
