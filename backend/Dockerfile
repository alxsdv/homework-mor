# Packages the jar built by `./mvnw clean install`; the build itself (and the tests) run outside Docker.
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY target/homework-backend-*.jar app.jar
# Docker Compose attaches the agent through JAVA_TOOL_OPTIONS, so the image also runs without it.
COPY target/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
