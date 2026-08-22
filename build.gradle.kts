// These types are in Gradle's implicit Kotlin DSL import list, so the build does not need them.
// They are spelled out because `subprojects { }` and `configure(...) { }` blocks get no type-safe
// accessors, and an IDE that cannot see the script model resolves nothing inside them otherwise.
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val springBootVersion = project.property("springBootVersion") as String

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")

    extensions.configure<CheckstyleExtension> {
        // Matched to the newest engine the CheckStyle-IDEA plugin bundles, so what the editor
        // highlights and what the build fails on are the same thing. Raise both together.
        toolVersion = "12.1.0"
        configFile = rootProject.file("config/checkstyle.xml")
        // The config reports at warning severity, which on its own would let anything through.
        // Zero is only a viable bar because the code is at zero today; it stays honest by failing
        // the build the moment it is not.
        maxWarnings = 0
    }

    // The BOM is for building and testing only. Letting it reach `api`/`implementation` would
    // publish it as an imported <dependencyManagement>, which would quietly pin every consumer's
    // Spring versions to whatever this build happened to compile against.
    val bom = dependencies.platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")

    dependencies {
        add("annotationProcessor", bom)
        add("testImplementation", bom)
        add("testAnnotationProcessor", bom)
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
        // Kept for JitPack and for IDE source navigation; the javadoc jar can come back
        // whenever this is actually published.
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

// Enough publishing to let somebody actually try this before it lives anywhere public:
//   ./gradlew publishToMavenLocal   →  repositories { mavenLocal() } in their app.
// It is also what JitPack needs. Signing, POM metadata and the Maven Central bundle can be
// added back when there is a reason to release.
configure(subprojects.filter { it.name != "puretx-sample" }) {
    apply(plugin = "maven-publish")

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
