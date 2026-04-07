FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY notification-service/build/libs/notification-service-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
