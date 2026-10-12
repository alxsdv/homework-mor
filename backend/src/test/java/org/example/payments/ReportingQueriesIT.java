package org.example.payments;

import org.example.IntegrationTestBase;
import org.jooq.Record;
import org.jooq.Result;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.payments.PaymentRequests.payment;
import static org.example.payments.PaymentRequests.post;

/**
 * Reference scenario. MERCH-001 is "merchant A" (3% fee), MERCH-002 is "merchant B" (5% fee).
 */
class ReportingQueriesIT extends IntegrationTestBase {

    private static final List<Map<String, Object>> P1_TO_P4 = List.of(
            payment("EXT-P1", "MERCH-001", "DE", "EUR", "119.00", "0.19", "2026-07-15T10:00:00Z"),
            payment("EXT-P2", "MERCH-001", "GR", "EUR", "124.00", "0.24", "2026-08-01T10:00:00Z"),
            payment("EXT-P3", "MERCH-001", "US", "USD", "108.00", "0.08", "2026-08-20T10:00:00Z"),
            // Local time is already Q4 in Berlin, but in UTC it is 2026-09-30T23:30Z: inside Q3.
            payment("EXT-P4", "MERCH-002", "DE", "EUR", "238.00", "0.19", "2026-10-01T01:30:00+02:00"));

    /** Settled exactly at the end of Q3 (exclusive bound): belongs to Q4. */
    private static final Map<String, Object> P5_IN_Q4 =
            payment("EXT-P5", "MERCH-002", "DE", "EUR", "119.00", "0.19", "2026-10-01T00:00:00Z");

    private static String sql(String name) throws IOException {
        return new ClassPathResource("sql/reports/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private void record(List<Map<String, Object>> payments) {
        for (Map<String, Object> body : payments) {
            assertThat(post(http, body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    /** "key1/key2" → owed. */
    private static Map<String, BigDecimal> owed(Result<Record> rows, String key1, String key2) {
        Map<String, BigDecimal> owed = new LinkedHashMap<>();
        for (Record row : rows) {
            owed.put(row.get(key1, String.class) + "/" + row.get(key2, String.class),
                     row.get("owed", BigDecimal.class));
        }
        return owed;
    }

    @Test
    void owedPerMerchantAndCurrency() throws IOException {
        record(P1_TO_P4);

        Map<String, BigDecimal> owed = owed(dsl.fetch(sql("merchant_balances.sql")), "merchant_id", "currency");

        assertThat(owed).containsOnlyKeys("MERCH-001/EUR", "MERCH-001/USD", "MERCH-002/EUR");
        assertThat(owed.get("MERCH-001/EUR")).isEqualByComparingTo("194.00");
        assertThat(owed.get("MERCH-001/USD")).isEqualByComparingTo("97.00");
        assertThat(owed.get("MERCH-002/EUR")).isEqualByComparingTo("190.00");
    }

    @Test
    void taxOwedPerCountryAndCurrencyForAPeriod() throws IOException {
        record(P1_TO_P4);
        record(List.of(P5_IN_Q4));

        Map<String, BigDecimal> owed = owed(
                dsl.fetch(sql("tax_owed.sql"),
                          OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                          OffsetDateTime.parse("2026-10-01T00:00:00Z")),
                "country_code", "currency");

        assertThat(owed).containsOnlyKeys("DE/EUR", "GR/EUR", "US/USD");
        assertThat(owed.get("DE/EUR")).isEqualByComparingTo("57.00");   // P1 19.00 + P4 38.00; P5 is Q4
        assertThat(owed.get("GR/EUR")).isEqualByComparingTo("24.00");
        assertThat(owed.get("US/USD")).isEqualByComparingTo("8.00");
    }
}
