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
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.recordRefund;
import static org.example.PaymentRequests.refund;

/** Reference scenario: MERCH-001 charges a 3% fee, MERCH-002 5%, MERCH-003 has no payments. */
class MerchantBalancesApiIT extends IntegrationTestBase {

    private static final List<Map<String, Object>> P1_TO_P4 = List.of(
            payment("EXT-P1", "MERCH-001", "DE", "EUR", "119.00", "0.19", "2026-07-15T10:00:00Z"),
            payment("EXT-P2", "MERCH-001", "GR", "EUR", "124.00", "0.24", "2026-08-01T10:00:00Z"),
            payment("EXT-P3", "MERCH-001", "US", "USD", "108.00", "0.08", "2026-08-20T10:00:00Z"),
            payment("EXT-P4", "MERCH-002", "DE", "EUR", "238.00", "0.19", "2026-10-01T01:30:00+02:00"));

    /** A balance with no payout in flight: everything we owe is available. */
    private static Map<String, Object> withoutPayouts(String currency, String payable) {
        return Map.of("currency", currency, "payable", payable, "available", payable, "pendingPayout", "0.00");
    }

    /** Records P1–P4 and returns their payment ids, in order. */
    private List<String> recordP1ToP4() {
        return P1_TO_P4.stream().map(body -> recordPayment(http, body)).toList();
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
                withoutPayouts("EUR", "194.00"),
                withoutPayouts("USD", "97.00")));
    }

    @Test
    void merchantWithOneCurrency() {
        recordP1ToP4();

        assertThat(getBalances("MERCH-002").getBody().get("balances"))
                .isEqualTo(List.of(withoutPayouts("EUR", "190.00")));
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

    @Test
    void refundsReduceWhatWeOweTheMerchant() {
        List<String> ids = recordP1ToP4();

        // Half of P1 gives back 48.50 of its 97.00 merchant share; P3 is refunded in full.
        recordRefund(http, ids.get(0), refund("RE-1", "2026-08-10T10:00:00Z", "59.50"));
        recordRefund(http, ids.get(2), refund("RE-2", "2026-09-01T10:00:00Z", null));

        assertThat(getBalances("MERCH-001").getBody().get("balances")).isEqualTo(List.of(
                withoutPayouts("EUR", "145.50"),
                withoutPayouts("USD", "0.00")));
        assertThat(getBalances("MERCH-002").getBody().get("balances"))
                .isEqualTo(List.of(withoutPayouts("EUR", "190.00")));
    }

    @Test
    void partialRefundsAddingUpToThePaymentLeaveNoRemainder() {
        List<String> ids = recordP1ToP4();

        recordRefund(http, ids.get(1), refund("RE-1", "2026-08-05T10:00:00Z", "31.00"));
        Map<String, Object> rest = recordRefund(http, ids.get(1), refund("RE-2", "2026-08-06T10:00:00Z", null));

        assertThat(rest).containsEntry("refundAmount", "93.00").containsEntry("merchantAmount", "72.75");
        // P2's 97.00 merchant share is gone entirely (24.25 + 72.75); P1's 97.00 is left.
        assertThat(getBalances("MERCH-001").getBody().get("balances")).isEqualTo(List.of(
                withoutPayouts("EUR", "97.00"),
                withoutPayouts("USD", "97.00")));
    }
}
