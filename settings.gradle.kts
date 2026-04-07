pluginManagement {
    val springBootVersion: String by settings
    val dependencyManagementVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version dependencyManagementVersion
    }
}

rootProject.name = "messaging-engine"

include(
    "common",
    "gateway-service",
    "chat-service",
    "query-service",
    "presence-service",
    "notification-service",
    "ai-service"
)