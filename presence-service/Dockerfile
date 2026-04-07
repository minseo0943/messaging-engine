FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY presence-service/build/libs/presence-service-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
