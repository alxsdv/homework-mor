package org.example.merchants;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.payment;
import static org.example.PaymentRequests.post;

/** Reference scenario: MERCH-001 charges a 3% fee, MERCH-002 5%, MERCH-003 has no payments. */
class MerchantBalancesApiIT extends IntegrationTestBase {

    private static final List<Map<String, Object>> P1_TO_P4 = List.of(
            payment("EXT-P1", "MERCH-001", "DE", "EUR", "119.00", "0.19", "2026-07-15T10:00:00Z"),
            payment("EXT-P2", "MERCH-001", "GR", "EUR", "124.00", "0.24", "2026-08-01T10:00:00Z"),
            payment("EXT-P3", "MERCH-001", "US", "USD", "108.00", "0.08", "2026-08-20T10:00:00Z"),
            payment("EXT-P4", "MERCH-002", "DE", "EUR", "238.00", "0.19", "2026-10-01T01:30:00+02:00"));

    private void recordP1ToP4() {
        for (Map<String, Object> body : P1_TO_P4) {
            assertThat(post(http, body).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    private ResponseEntity<Map<String, Object>> getBalances(String merchantId) {
        return http.get()
                .uri("/merchants/{merchantId}/balances", merchantId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    @Test
    void merchantWithTwoCurrencies() {
        recordP1ToP4();

        ResponseEntity<Map<String, Object>> response = getBalances("MERCH-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("merchantId", "MERCH-001");
        assertThat(response.getBody().get("balances")).isEqualTo(List.of(
                Map.of("currency", "EUR", "payable", "194.00"),
                Map.of("currency", "USD", "payable", "97.00")));
    }

    @Test
    void merchantWithOneCurrency() {
        recordP1ToP4();

        assertThat(getBalances("MERCH-002").getBody().get("balances"))
                .isEqualTo(List.of(Map.of("currency", "EUR", "payable", "190.00")));
    }

    @Test
    void knownMerchantWithoutPaymentsHasEmptyBalances() {
        recordP1ToP4();

        ResponseEntity<Map<String, Object>> response = getBalances("MERCH-003");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("merchantId", "MERCH-003")
                .containsEntry("balances", List.of());
    }

    @Test
    void unknownMerchantIs404() {
        ResponseEntity<Map<String, Object>> response = getBalances("MERCH-999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) response.getBody().get("detail")).contains("MERCH-999");
    }

    @Test
    void merchantIdIsMatchedExactly() {
        recordP1ToP4();

        assertThat(getBalances("merch-001").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
