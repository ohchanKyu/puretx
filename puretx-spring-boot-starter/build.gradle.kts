description = "puretx Spring Boot starter — detects impure @Transactional work"

val springBootBaselineVersion = project.property("springBootBaselineVersion") as String
val feignVersion = "13.14"

// Pinned rather than taken from a BOM: Lombok's version has nothing to do with which
// Spring Boot this module compiles against.
val lombokVersion = "1.18.46"

/**
 * Compiled against the oldest supported Spring Boot so that using anything newer by accident
 * fails here rather than at a user's startup. Tests run against the current release.
 */
val baselineBom = dependencies.platform(
    "org.springframework.boot:spring-boot-dependencies:$springBootBaselineVersion",
)

dependencies {
    compileOnly(baselineBom)

    api(project(":puretx-core"))
    api("org.springframework.boot:spring-boot-autoconfigure:$springBootBaselineVersion")
    api("org.springframework:spring-tx:6.1.0")
    api("org.springframework:spring-context:6.1.0")

    // Lombok must come first on the processor path so that the configuration processor sees the
    // getters and setters it generates. Both are compile-time only and neither reaches consumers.
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Every integration below is optional: the matching auto-configuration is @ConditionalOnClass,
    // so an application only pays for the ones it already has.
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework.kafka:spring-kafka")
    compileOnly("io.github.openfeign:feign-core:$feignVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("io.github.openfeign:feign-core:$feignVersion")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("com.h2database:h2")
}
