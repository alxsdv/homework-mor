package org.example.payments.adapters.in;

import org.example.TestcontainersConfiguration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.example.jooq.Tables.PAYMENTS;

/**
 * Doesn't extend IntegrationTestBase: that class truncates tables before each test, which would erase the
 * seeded data. The dev profile gives this test its own Spring context and Postgres container.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class DemoDataSeederIT {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private DemoDataSeeder seeder;

    @Test
    void seedsDemoPaymentsWithLedgerPostingsOnStartup() {
        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(5);
        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isEqualTo(5);
        assertThat(dsl.fetchCount(LEDGER_ENTRIES)).isEqualTo(20);
    }

    @Test
    void seedingAgainAddsNothing() throws Exception {
        seeder.run(new DefaultApplicationArguments());

        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(5);
        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isEqualTo(5);
    }
}
