FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY chat-service/build/libs/chat-service-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
