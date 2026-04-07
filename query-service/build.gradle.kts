plugins {
    id("org.springframework.boot")
}

val springdocVersion: String by project
val micrometerTracingVersion: String by project
val otelVersion: String by project

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel:$micrometerTracingVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
    implementation("org.springframework.kafka:spring-kafka")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // Test
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
