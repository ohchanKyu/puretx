// These types are in Gradle's implicit Kotlin DSL import list, so the build does not need them.
// They are spelled out because `subprojects { }` and `configure(...) { }` blocks get no type-safe
// accessors, and an IDE that cannot see the script model resolves nothing inside them otherwise.
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugins.signing.SigningExtension

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

// Everything a Maven Central release needs, wired so that it costs nothing until it is used:
//   ./gradlew publishToMavenLocal          works with no keys, for JitPack and for trying it out
//   SIGNING_KEY / SIGNING_PASSWORD set     signs the artifacts, which Central requires
// Versions come from gradle.properties; a release overrides it with -Pversion=<tag without v>.
configure(subprojects.filter { it.name != "puretx-sample" }) {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Javadoc>().configureEach {
        // Lombok-generated accessors have no source for javadoc to see, and the starter's
        // properties class is documented through the configuration metadata instead. Missing
        // comments stay a warning; everything else doclint finds still fails the build.
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("Xdoclint:all,-missing", true)
            links("https://docs.oracle.com/en/java/javase/17/docs/api/")
        }
    }

    // JPMS applications that put the jar on the module path get a stable module name rather
    // than one derived from the file name.
    val automaticModuleName = when (project.name) {
        "puretx-core" -> "io.github.ohchankyu.puretx"
        else -> "io.github.ohchankyu.puretx.spring"
    }
    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                "Automatic-Module-Name" to automaticModuleName,
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
            )
        }
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name = project.name
                    description = project.provider { project.description }
                    url = "https://github.com/ohchanKyu/puretx"
                    inceptionYear = "2026"
                    licenses {
                        license {
                            name = "Apache License, Version 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                            distribution = "repo"
                        }
                    }
                    developers {
                        developer {
                            id = "ohchanKyu"
                            name = "ohchanKyu"
                            url = "https://github.com/ohchanKyu"
                        }
                    }
                    scm {
                        url = "https://github.com/ohchanKyu/puretx"
                        connection = "scm:git:https://github.com/ohchanKyu/puretx.git"
                        developerConnection = "scm:git:git@github.com:ohchanKyu/puretx.git"
                    }
                    issueManagement {
                        system = "GitHub"
                        url = "https://github.com/ohchanKyu/puretx/issues"
                    }
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")
        isRequired = !signingKey.isNullOrBlank()
        if (isRequired) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(extensions.getByType<PublishingExtension>().publications["maven"])
        }
    }
}
