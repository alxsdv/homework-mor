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
        Map<String, BigDecimal> feeRates = dsl.selectFrom(MERCHANTS).fetchMap(MERCHANTS.PUBLIC_ID, MERCHANTS.FEE_RATE);

        assertThat(feeRates).containsOnlyKeys("MERCH-001", "MERCH-002", "MERCH-003");
        assertThat(feeRates.get("MERCH-001")).isEqualByComparingTo("0.03");
        assertThat(feeRates.get("MERCH-002")).isEqualByComparingTo("0.05");
        assertThat(feeRates.get("MERCH-003")).isEqualByComparingTo("0.025");
    }

    @Test
    void merchantsPublicIdIsUnique() {
        List<String> names = dsl.fetch("""
                        select conname from pg_constraint
                        where conrelid = 'merchants'::regclass and contype = 'u'
                        """)
                .getValues(0, String.class);

        assertThat(names).containsExactly("merchants_public_id_key");
    }

    @Test
    void paymentsRefundsAndPayoutsReferenceMerchantsByPublicId() {
        List<String> references = dsl.fetch("""
                        select c.conrelid::regclass || '.' || a.attname || ' -> ' || fa.attname
                        from pg_constraint c
                        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey)
                        join pg_attribute fa on fa.attrelid = c.confrelid and fa.attnum = any (c.confkey)
                        where c.contype = 'f' and c.confrelid = 'merchants'::regclass
                        """)
                .getValues(0, String.class);

        assertThat(references).containsExactlyInAnyOrder(
                "payments.merchant_id -> public_id", "refunds.merchant_id -> public_id",
                "payouts.merchant_id -> public_id");
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

    @Test
    void refundsUniqueConstraintsHaveTheNamesTheAdapterMapsOn() {
        List<String> names = dsl.fetch("""
                        select conname from pg_constraint
                        where conrelid = 'refunds'::regclass and contype = 'u'
                        """)
                .getValues(0, String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "refunds_public_id_key", "refunds_merchant_id_external_id_key");
    }

    @Test
    void payoutsUniqueConstraintsHaveTheNamesTheAdapterMapsOn() {
        List<String> names = dsl.fetch("""
                        select conname from pg_constraint
                        where conrelid = 'payouts'::regclass and contype = 'u'
                        """)
                .getValues(0, String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "payouts_public_id_key", "payouts_merchant_id_external_id_key");
    }
}
