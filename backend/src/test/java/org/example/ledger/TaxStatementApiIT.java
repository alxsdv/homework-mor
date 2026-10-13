package org.example.ledger;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.payment;
import static org.example.PaymentRequests.post;

/** Reference scenario: P1–P4 settle in Q3 (UTC), P5 exactly at the start of Q4 (UTC). */
class TaxStatementApiIT extends IntegrationTestBase {

    private static final List<Map<String, Object>> P1_TO_P5 = List.of(
            payment("EXT-P1", "MERCH-001", "DE", "EUR", "119.00", "0.19", "2026-07-15T10:00:00Z"),
            payment("EXT-P2", "MERCH-001", "GR", "EUR", "124.00", "0.24", "2026-08-01T10:00:00Z"),
            payment("EXT-P3", "MERCH-001", "US", "USD", "108.00", "0.08", "2026-08-20T10:00:00Z"),
            // 2026-09-30T23:30Z: still Q3 in UTC, already Q4 in Berlin.
            payment("EXT-P4", "MERCH-002", "DE", "EUR", "238.00", "0.19", "2026-10-01T01:30:00+02:00"),
            payment("EXT-P5", "MERCH-002", "DE", "EUR", "119.00", "0.19", "2026-10-01T00:00:00Z"));

    private void recordP1ToP5() {
        for (Map<String, Object> body : P1_TO_P5) {
            assertThat(post(http, body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    /** query is the raw query string, e.g. "?from=2026-07-01&to=2026-10-01"; keep it free of characters to encode. */
    private ResponseEntity<Map<String, Object>> getTaxStatement(String query) {
        return http.get()
                .uri("/tax-statement" + query)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private static Map<String, String> statement(String countryCode, String currency, String payable) {
        return Map.of("countryCode", countryCode, "currency", currency, "payable", payable);
    }

    @Test
    void thirdQuarterInUtc() {
        recordP1ToP5();

        ResponseEntity<Map<String, Object>> response = getTaxStatement("?from=2026-07-01&to=2026-10-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("from", "2026-07-01")
                .containsEntry("to", "2026-10-01")
                .containsEntry("zone", "UTC");
        // DE: P1 19.00 + P4 38.00; P5 is on the exclusive bound.
        assertThat(response.getBody().get("statements")).isEqualTo(List.of(
                statement("DE", "EUR", "57.00"),
                statement("GR", "EUR", "24.00"),
                statement("US", "USD", "8.00")));
    }

    @Test
    void thirdQuarterInBerlin() {
        recordP1ToP5();

        ResponseEntity<Map<String, Object>> response =
                getTaxStatement("?from=2026-07-01&to=2026-10-01&zone=Europe/Berlin");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("zone", "Europe/Berlin");
        assertThat(response.getBody().get("statements")).isEqualTo(List.of(
                statement("DE", "EUR", "19.00"),
                statement("GR", "EUR", "24.00"),
                statement("US", "USD", "8.00")));
    }

    @Test
    void fourthQuarterInBerlin() {
        recordP1ToP5();

        ResponseEntity<Map<String, Object>> response =
                getTaxStatement("?from=2026-10-01&to=2027-01-01&zone=Europe/Berlin");

        // P4 38.00 + P5 19.00.
        assertThat(response.getBody().get("statements")).isEqualTo(List.of(statement("DE", "EUR", "57.00")));
    }

    @Test
    void periodWithoutPaymentsHasNoStatements() {
        recordP1ToP5();

        ResponseEntity<Map<String, Object>> response = getTaxStatement("?from=2026-01-01&to=2026-04-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("statements", List.of());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "?to=2026-10-01                                  | from",
            "?from=2026-07-01                                | to",
            "?from=2026-07-01&to=2026-13-01                  | to",
            "?from=2026-07-01&to=2026-10-01&zone=Mars/Olympus | zone",
            "?from=2026-07-01&to=2026-07-01                  | from"})
    void invalidPeriodIs400NamingTheParameter(String query, String parameter) {
        ResponseEntity<Map<String, Object>> response = getTaxStatement(query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).startsWith(parameter + " ");
    }

    @Test
    void repeatedParameterIs400() {
        ResponseEntity<Map<String, Object>> response =
                getTaxStatement("?from=2026-07-01&from=2026-08-01&to=2026-10-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).startsWith("from ");
    }
}
