# Packages the jar built by `./mvnw clean install`; the build itself (and the tests) run outside Docker.
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY target/homework-backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
