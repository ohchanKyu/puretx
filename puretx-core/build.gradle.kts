description = "puretx core — framework-agnostic impure-transaction detection engine"

val slf4jBaselineVersion = project.property("slf4jBaselineVersion") as String

val jspecifyVersion = project.property("jspecifyVersion") as String

dependencies {
    api("org.slf4j:slf4j-api:$slf4jBaselineVersion")
    // Nullness annotations are retained at runtime, so anything compiling against puretx needs
    // them on its classpath too. This is the same choice Spring Framework 7 made.
    api("org.jspecify:jspecify:$jspecifyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
