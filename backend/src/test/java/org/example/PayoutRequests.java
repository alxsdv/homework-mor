package org.example;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON bodies and calls for the payout endpoints, built as maps so tests can drop or break any field. */
public final class PayoutRequests {

    private PayoutRequests() {}

    public static Map<String, Object> payout(String externalId, String merchantId, String currency, String amount,
                                             String requestedAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalId", externalId);
        body.put("merchantId", merchantId);
        body.put("currency", currency);
        body.put("amount", amount);
        body.put("requestedAt", requestedAt);
        return body;
    }

    /** MERCH-001 pays out amount EUR on 2026-10-03. */
    public static Map<String, Object> eurPayout(String externalId, String amount) {
        return payout(externalId, "MERCH-001", "EUR", amount, "2026-10-03T09:00:00+02:00");
    }

    /** Copy of body with field set to value; null removes the field. */
    public static Map<String, Object> with(Map<String, Object> body, String field, @Nullable Object value) {
        return PaymentRequests.with(body, field, value);
    }

    public static ResponseEntity<Map<String, Object>> postPayout(RestClient http, Map<String, Object> body) {
        return send(http, "/payouts", body);
    }

    public static ResponseEntity<Map<String, Object>> settle(RestClient http, String payoutId, String settledAt) {
        return send(http, "/payouts/" + payoutId + "/settle", Map.of("settledAt", settledAt));
    }

    public static ResponseEntity<Map<String, Object>> fail(RestClient http, String payoutId, String failedAt) {
        return send(http, "/payouts/" + payoutId + "/fail", Map.of("failedAt", failedAt));
    }

    /** Requests a payout that must succeed, and returns its payoutId. */
    public static String requestPayout(RestClient http, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> response = postPayout(http, body);
        if (response.getStatusCode().value() != 201 || response.getBody() == null) {
            throw new IllegalStateException("Expected 201 Created, got " + response);
        }
        return (String) response.getBody().get("payoutId");
    }

    public static ResponseEntity<Map<String, Object>> getBalances(RestClient http, String merchantId) {
        return http.get()
                .uri("/merchants/{merchantId}/balances", merchantId)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private static ResponseEntity<Map<String, Object>> send(RestClient http, String path, Map<String, Object> body) {
        return http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }
}
