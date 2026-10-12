package org.example.payments;

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
}
