# Homework: The MoR Ledger Service

## Tech Stack
- Java 25
- Spring Boot 4.1.0
- Jooq
- Flyway
- Testcontainers
- NullAway, JSpecify
- ArchUnit
- OpenTelemetry Java agent + Grafana LGTM (traces, logs)

## How to build & run locally

```bash
./mvnw clean install                 # build backend/target/homework-backend-*.jar
docker compose up -d --build         # start Postgres, app and Grafana (rebuilds the app image)
docker compose logs -f app           # follow app logs; ready when "Started Application" appears
docker compose ps                    # check all containers are up
```

```bash
docker compose down                  # stop, keep the database (stored traces and logs are lost either way)
docker compose down -v               # stop and wipe the database
```

### Regenerating jOOQ classes

The jOOQ classes in `backend/src/main/java/org/example/jooq` are generated from the Flyway migrations and checked in.
After adding or changing a migration, regenerate them (requires Docker) and commit the result:

```bash
./mvnw -pl backend -Pjooq-codegen generate-sources
```

## Testing the API

Base URL: `http://localhost:8080`  
Swagger UI: http://localhost:8080/swagger-ui.html  
Grafana: http://localhost:3000 (Explore → Tempo for request traces; see
[Tracing in the smoke-test guide](smoke-test-guide.md#5-tracing-follow-a-request-in-grafana))

The application exposes the following API methods:

1. `POST /payments`: record a settled payment from the customer to a given merchant
2. `POST /payments/{paymentId}/refund`: record a refund to a previously setlled payment, fully or partly
3. `GET /merchants/{merchantId}/balances`: what are merchant's balances with the MoR (per currency)
4. `GET /tax-statement`: how much does the MoR owe tax authorities (per country & currency)

See the full API spec through the Swagger UI (^^)  
More on the prepared scenarios for manual testing: [smoke-test-guide.md](smoke-test-guide.md)

## Design

Read the [Design Document](design.md) to know more about the task, as well as decisions and assumptions made.
