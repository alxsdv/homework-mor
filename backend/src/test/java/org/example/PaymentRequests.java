package org.example;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON bodies for POST /payments, built as maps so tests can drop or break any field. */
public final class PaymentRequests {

    private PaymentRequests() {}

    public static Map<String, Object> payment(String externalId, String merchantId, String countryCode,
                                              String currency, String totalAmount, String taxRate,
                                              String settledAt) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("addressLine1", "Hauptstrasse 12");
        contact.put("addressLine2", "Wohnung 4");
        contact.put("countryCode", countryCode);
        contact.put("postalCode", "10115");

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("firstName", "Anna");
        customer.put("lastName", "Schmidt");
        customer.put("email", "anna.schmidt@example.com");
        customer.put("billingContact", contact);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalId", externalId);
        body.put("settledAt", settledAt);
        body.put("merchantId", merchantId);
        body.put("customer", customer);
        body.put("currency", currency);
        body.put("totalAmount", totalAmount);
        body.put("taxRate", taxRate);
        return body;
    }

    /** Reference payment P1: merchant MERCH-001 (3% fee), German customer, 119.00 EUR at 19% tax. */
    public static Map<String, Object> validPayment() {
        return payment("EXT-1", "MERCH-001", "DE", "EUR", "119.00", "0.19", "2026-09-30T23:30:00+02:00");
    }

    /** Copy of body with field set to value; null removes the field. */
    public static Map<String, Object> with(Map<String, Object> body, String field, @Nullable Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        if (value == null) {
            copy.remove(field);
        } else {
            copy.put(field, value);
        }
        return copy;
    }

    /** Copy of body with customer.billingContact.field set to value; null removes the field. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> withContact(Map<String, Object> body, String field, @Nullable Object value) {
        Map<String, Object> customer = new LinkedHashMap<>((Map<String, Object>) body.get("customer"));
        Map<String, Object> contact = (Map<String, Object>) customer.get("billingContact");
        customer.put("billingContact", with(contact, field, value));
        return with(body, "customer", customer);
    }

    public static ResponseEntity<Map<String, Object>> post(RestClient http, Map<String, Object> body) {
        return http.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    /** JSON body for POST /payments/{paymentId}/refund; a null refundAmount leaves the field out (a full refund). */
    public static Map<String, Object> refund(String externalId, String refundedAt, @Nullable String refundAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalId", externalId);
        body.put("refundedAt", refundedAt);
        if (refundAmount != null) {
            body.put("refundAmount", refundAmount);
        }
        return body;
    }

    public static ResponseEntity<Map<String, Object>> postRefund(RestClient http, String paymentId,
                                                                 Map<String, Object> body) {
        return http.post()
                .uri("/payments/{paymentId}/refund", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    /** Records a payment that must succeed, and returns its paymentId. */
    public static String recordPayment(RestClient http, Map<String, Object> body) {
        return (String) created(post(http, body)).get("paymentId");
    }

    /** Records a refund that must succeed, and returns its 201 body. */
    public static Map<String, Object> recordRefund(RestClient http, String paymentId, Map<String, Object> body) {
        return created(postRefund(http, paymentId, body));
    }

    private static Map<String, Object> created(ResponseEntity<Map<String, Object>> response) {
        if (response.getStatusCode().value() != 201 || response.getBody() == null) {
            throw new IllegalStateException("Expected 201 Created, got " + response);
        }
        return response.getBody();
    }
}
