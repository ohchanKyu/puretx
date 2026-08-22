description = "puretx core — framework-agnostic impure-transaction detection engine"

val slf4jBaselineVersion = project.property("slf4jBaselineVersion") as String

dependencies {
    api("org.slf4j:slf4j-api:$slf4jBaselineVersion")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
