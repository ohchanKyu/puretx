description = "puretx Spring Boot starter — detects impure @Transactional work"

val springBootVersion = project.property("springBootVersion") as String
val springBootBaselineVersion = project.property("springBootBaselineVersion") as String
val springBoot4BaselineVersion = project.property("springBoot4BaselineVersion") as String
val feignVersion = "13.14"

// Pinned rather than taken from a BOM: Lombok's version has nothing to do with which
// Spring Boot this module compiles against.
val lombokVersion = "1.18.46"

/**
 * Compiled against the oldest supported Spring Boot so that using anything newer by accident
 * fails here rather than at a user's startup. Tests run against whatever `springBootVersion`
 * says, which is how CI covers the whole supported range.
 */
val baselineBom = dependencies.platform(
    "org.springframework.boot:spring-boot-dependencies:$springBootBaselineVersion",
)

val testsRunOnSpringBoot4 = springBootVersion.substringBefore('.').toInt() >= 4

dependencies {
    compileOnly(baselineBom)

    api(project(":puretx-core"))
    // Runtime scope in the published POM: an application has its own Boot version and must not
    // see this one as an API it compiles against.
    implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootBaselineVersion")
    api("org.springframework:spring-tx:6.1.0")
    api("org.springframework:spring-context:6.1.0")

    // Spring Boot 4 moved the HTTP client customizer interfaces into modules of their own. Only
    // those two jars are needed to compile the Boot 4 variants, and pulling them in without
    // their dependencies keeps the rest of the compile classpath on the 3.2 baseline.
    compileOnly("org.springframework.boot:spring-boot-restclient:$springBoot4BaselineVersion") { isTransitive = false }
    compileOnly("org.springframework.boot:spring-boot-webclient:$springBoot4BaselineVersion") { isTransitive = false }

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
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.github.openfeign:feign-core:$feignVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.github.openfeign:feign-core:$feignVersion")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("com.h2database:h2")

    // On Boot 3 the builder beans come with spring-boot-autoconfigure. On Boot 4 they live in
    // starters of their own, without which the customizer path has nothing to attach to.
    if (testsRunOnSpringBoot4) {
        testImplementation("org.springframework.boot:spring-boot-starter-restclient")
        testImplementation("org.springframework.boot:spring-boot-starter-webclient")
    }
}
