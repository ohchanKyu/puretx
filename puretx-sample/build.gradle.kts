plugins {
    application
}

description = "puretx sample application (not published)"

application {
    mainClass = "com.example.shop.SampleApplication"
}

val springBootVersion = project.property("springBootVersion") as String

// Lombok's version comes from here too, so it never needs pinning by hand.
val bom = dependencies.platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion")

dependencies {
    implementation(bom)
    compileOnly(bom)
    testCompileOnly(bom)

    implementation(project(":puretx-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("ch.qos.logback:logback-classic")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
