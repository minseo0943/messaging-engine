plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

val springBootVersion: String by project

allprojects {
    group = "com.jdc"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val testcontainersVersion: String by project

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    // integrationTest 소스셋 설정
    sourceSets {
        create("integrationTest") {
            compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
            runtimeClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        }
    }

    val integrationTestImplementation by configurations.getting {
        extendsFrom(configurations["testImplementation"])
    }

    dependencies {
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testCompileOnly("org.projectlombok:lombok")
        testAnnotationProcessor("org.projectlombok:lombok")
        "integrationTestCompileOnly"("org.projectlombok:lombok")
        "integrationTestAnnotationProcessor"("org.projectlombok:lombok")

        testImplementation("org.springframework.boot:spring-boot-starter-test")

        // 구조화 로깅 (JSON) — Grafana Loki 연동
        implementation("net.logstash.logback:logstash-logback-encoder:7.4")

        // Testcontainers (통합 테스트용)
        integrationTestImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
        integrationTestImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
        integrationTestImplementation("org.testcontainers:kafka:$testcontainersVersion")
        integrationTestImplementation("org.testcontainers:mysql:$testcontainersVersion")
        integrationTestImplementation("org.testcontainers:mongodb:$testcontainersVersion")
        integrationTestImplementation("org.awaitility:awaitility:4.2.0")
    }

    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests with Testcontainers."
        group = "verification"
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.named("test"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<JacocoCoverageVerification> {
        violationRules {
            rule {
                limit {
                    minimum = "0.60".toBigDecimal()
                }
            }
        }
    }
}