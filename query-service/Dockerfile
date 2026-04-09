FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY query-service/build/libs/query-service-0.0.1-SNAPSHOT.jar app.jar

USER appuser
EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
