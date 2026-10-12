package org.example;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.jooq.Tables.MERCHANTS;

public class MigrationsIT extends IntegrationTestBase {

    @Test
    void seedMerchantsWithFeeRates() {
        Map<String, BigDecimal> feeRates = dsl.selectFrom(MERCHANTS).fetchMap(MERCHANTS.ID, MERCHANTS.FEE_RATE);

        assertThat(feeRates).containsOnlyKeys("MERCH-001", "MERCH-002", "MERCH-003");
        assertThat(feeRates.get("MERCH-001")).isEqualByComparingTo("0.03");
        assertThat(feeRates.get("MERCH-002")).isEqualByComparingTo("0.05");
        assertThat(feeRates.get("MERCH-003")).isEqualByComparingTo("0.025");
    }

    @Test
    void paymentsUniqueConstraintsHaveTheNamesTheAdapterMapsOn() {
        List<String> names = dsl.fetch("""
                        select conname from pg_constraint
                        where conrelid = 'payments'::regclass and contype = 'u'
                        """)
                .getValues(0, String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "payments_public_id_key", "payments_merchant_id_external_id_key");
    }
}
