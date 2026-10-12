package org.example;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Full application on a random port, backed by a Testcontainers Postgres.
 * All integration tests extending this class share one Spring context, and therefore one container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class IntegrationTestBase {

    @Autowired
    protected DSLContext dsl;

    @LocalServerPort
    protected int port;

    protected RestClient http;

    @BeforeEach
    void resetDatabaseAndClient() {
        truncateAllTablesExceptReferenceData();
        http = RestClient.create("http://localhost:" + port);
    }

    /** Empties every table except seeded reference data, so tables added by later migrations are covered too. */
    private void truncateAllTablesExceptReferenceData() {
        List<String> tables = dsl.fetch("""
                        select tablename from pg_tables
                        where schemaname = 'public'
                          and tablename not in ('flyway_schema_history', 'merchants')
                        """)
                .getValues(0, String.class);
        if (!tables.isEmpty()) {
            dsl.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
