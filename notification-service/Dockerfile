FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY notification-service/build/libs/notification-service-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-Xms96m", "-Xmx192m", "-jar", "app.jar"]
